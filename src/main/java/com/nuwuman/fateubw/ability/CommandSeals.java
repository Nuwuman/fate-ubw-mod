package com.nuwuman.fateubw.ability;

import com.mojang.serialization.Codec;
import com.nuwuman.fateubw.FateUBW;
import com.nuwuman.fateubw.mixin.ItemCooldownManagerAccessor;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentSyncPredicate;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.particle.DustParticleEffect;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.registry.Registries;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.List;

/**
 * Sellos de Comando: tres por jugador, se recupera uno cada día de Minecraft (20 min). Con la tecla V se gasta uno:
 * todas las recargas del mod a cero, el maná lleno y 30 s de Fuerza II, Velocidad II, Resistencia I y Regeneración II.
 */
public final class CommandSeals {
    public static final int MAX = 3;
    private static final int REGEN_TICKS = 24000;
    private static final int BOOST_TICKS = 20 * 30;
    private static final DustParticleEffect SEAL_RED = new DustParticleEffect(new Vector3f(0.9F, 0.05F, 0.1F), 1.5F);
    public static final AttachmentType<Integer> SEALS = AttachmentRegistry.<Integer>builder()
            .persistent(Codec.INT).copyOnDeath().initializer(() -> MAX)
            .syncWith(PacketCodecs.VAR_INT.cast(), AttachmentSyncPredicate.targetOnly())
            .buildAndRegister(FateUBW.id("command_seals"));

    /** Cliente → servidor: gastar un Sello de Comando. */
    public record UsePayload() implements CustomPayload {
        public static final Id<UsePayload> ID = new Id<>(FateUBW.id("use_command_seal"));
        public static final PacketCodec<RegistryByteBuf, UsePayload> CODEC = PacketCodec.unit(new UsePayload());

        @Override
        public Id<? extends CustomPayload> getId() {
            return ID;
        }
    }

    private CommandSeals() {
    }

    public static void register() {
        PayloadTypeRegistry.playC2S().register(UsePayload.ID, UsePayload.CODEC);
        ServerPlayNetworking.registerGlobalReceiver(UsePayload.ID, (payload, context) -> use(context.player()));
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
                if (player.age > 0 && player.age % REGEN_TICKS == 0 && seals(player) < MAX) set(player, seals(player) + 1);
            }
        });
    }

    public static int seals(PlayerEntity player) {
        Integer seals = player.getAttached(SEALS);
        return seals == null ? MAX : seals;
    }

    public static void set(PlayerEntity player, int seals) {
        player.setAttached(SEALS, Math.max(0, Math.min(MAX, seals)));
    }

    private static void use(ServerPlayerEntity player) {
        if (player.isSpectator() || !player.isAlive()) return;
        int seals = seals(player);
        if (seals <= 0) {
            player.sendMessage(Text.translatable("message.fate_ubw.no_seals").formatted(Formatting.GRAY), true);
            return;
        }
        set(player, seals - 1);

        // Todas las recargas del mod (las claves son ítems del mod)
        ItemCooldownManagerAccessor cooldowns = (ItemCooldownManagerAccessor) player.getItemCooldownManager();
        List<Item> keys = new ArrayList<>(cooldowns.fateubw$entries().keySet());
        for (Item key : keys) {
            if (Registries.ITEM.getId(key).getNamespace().equals(FateUBW.MOD_ID)) player.getItemCooldownManager().remove(key);
        }
        Mana.fill(player);
        player.addStatusEffect(new StatusEffectInstance(StatusEffects.STRENGTH, BOOST_TICKS, 1));
        player.addStatusEffect(new StatusEffectInstance(StatusEffects.SPEED, BOOST_TICKS, 1));
        player.addStatusEffect(new StatusEffectInstance(StatusEffects.RESISTANCE, BOOST_TICKS, 0));
        player.addStatusEffect(new StatusEffectInstance(StatusEffects.REGENERATION, BOOST_TICKS, 1));

        ServerWorld world = player.getServerWorld();
        for (int i = 0; i < 40; i++) {
            double angle = i * Math.PI / 20;
            world.spawnParticles(SEAL_RED, player.getX() + Math.cos(angle) * 1.2, player.getY() + 0.1 + i * 0.05,
                    player.getZ() + Math.sin(angle) * 1.2, 1, 0.0, 0.0, 0.0, 0.0);
        }
        world.spawnParticles(ParticleTypes.FLASH, player.getX(), player.getBodyY(0.5), player.getZ(), 1, 0.0, 0.0, 0.0, 0.0);
        world.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.BLOCK_RESPAWN_ANCHOR_DEPLETE.value(), SoundCategory.PLAYERS, 1.5F, 1.2F);
        world.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.BLOCK_BEACON_POWER_SELECT, SoundCategory.PLAYERS, 1.5F, 0.6F);
        player.sendMessage(Text.translatable("message.fate_ubw.command_seal", seals - 1).formatted(Formatting.RED, Formatting.BOLD), true);
    }
}
