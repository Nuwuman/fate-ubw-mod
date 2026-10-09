package com.nuwuman.fateubw.ishtar;

import com.nuwuman.fateubw.FateUBW;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentSyncPredicate;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.UseAction;

/**
 * La Maanna que flota junto a Ishtar con el conjunto puesto. Con la mano principal vacía, mantener click derecho la
 * tensa como si fuera el arco en la mano (mismos disparos y Noble Phantasm). El cliente avisa al pulsar y al soltar;
 * el servidor guarda desde qué tick tensa (sincronizado con todos, para dibujarla tensa).
 */
public final class FloatingMaanna {
    /** Tick del mundo en que empezó a tensar; HOLDING mientras sale el Noble Phantasm. Sin valor: en reposo. */
    public static final AttachmentType<Long> DRAW = AttachmentRegistry.<Long>builder()
            .syncWith(PacketCodecs.VAR_LONG.cast(), AttachmentSyncPredicate.all())
            .buildAndRegister(FateUBW.id("maanna_draw"));
    public static final long HOLDING = 0L;

    public record BowPayload(boolean pressed) implements CustomPayload {
        public static final Id<BowPayload> ID = new Id<>(FateUBW.id("maanna_bow"));
        public static final PacketCodec<RegistryByteBuf, BowPayload> CODEC = PacketCodec.tuple(
                PacketCodecs.BOOL, BowPayload::pressed, BowPayload::new);

        @Override
        public Id<? extends CustomPayload> getId() {
            return ID;
        }
    }

    private FloatingMaanna() {
    }

    /** ¿Tiene la Maanna flotante (el conjunto de Ishtar puesto)? */
    public static boolean present(PlayerEntity player) {
        return IshtarArmorItem.fullSet(player) && !player.isSpectator();
    }

    /** ¿Puede tensarla ahora? Con la mano principal vacía y nada que usar en la otra. */
    public static boolean usable(PlayerEntity player) {
        ItemStack off = player.getOffHandStack();
        return present(player) && player.isAlive() && player.getMainHandStack().isEmpty()
                && (off.isEmpty() || off.getUseAction() == UseAction.NONE);
    }

    public static void register() {
        PayloadTypeRegistry.playC2S().register(BowPayload.ID, BowPayload.CODEC);
        ServerPlayNetworking.registerGlobalReceiver(BowPayload.ID, (payload, context) -> {
            ServerPlayerEntity player = context.player();
            Long start = player.getAttached(DRAW);
            if (payload.pressed()) {
                if (start == null && usable(player)) player.setAttached(DRAW, player.getWorld().getTime());
                return;
            }
            if (start == null || start == HOLDING) return;
            if (!usable(player)) {
                player.removeAttached(DRAW);
                return;
            }
            int used = (int) (player.getWorld().getTime() - start);
            if (MaannaItem.release(player.getServerWorld(), player, used, null)) {
                // Se queda tensa mientras sale el Noble Phantasm, y luego vuelve a su sitio
                player.setAttached(DRAW, HOLDING);
                com.nuwuman.fateubw.enchant.FateEnchantments.later(com.nuwuman.fateubw.PlayerAnims.WINDUP, () -> player.removeAttached(DRAW));
            } else {
                player.removeAttached(DRAW);
            }
        });
        // Mientras tensa: las partículas de la carga; si deja de poder usarla (cambia de ítem, se quita la ropa), se suelta
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
                Long start = player.getAttached(DRAW);
                if (start == null || start == HOLDING) continue;
                if (!usable(player)) player.removeAttached(DRAW);
                else MaannaItem.chargeTick(player.getServerWorld(), player, (int) (player.getWorld().getTime() - start));
            }
        });
    }
}
