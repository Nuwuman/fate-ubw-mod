package com.nuwuman.fateubw.grail;

import com.nuwuman.fateubw.FateUBW;
import com.nuwuman.fateubw.ability.CommandSeals;
import com.nuwuman.fateubw.ability.Mana;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.tooltip.TooltipType;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Hand;
import net.minecraft.util.TypedActionResult;
import net.minecraft.world.World;

import java.util.List;

/**
 * El Santo Grial, premio de la Guerra del Santo Grial. Click derecho abre "¿Qué deseas?": eliges un servant y el Grial
 * te concede su poder (su equipo completo, puesto), te cura del todo y recupera el maná y los Sellos de Comando.
 * Se consume al pedir el deseo.
 */
public class HolyGrailItem extends Item {
    /** Abre la pantalla del deseo; la pone el cliente (aquí no se puede tocar código de cliente). */
    public static Runnable openWishScreen = () -> {
    };

    /** Cliente → servidor: el servant cuyo poder desea. */
    public record WishPayload(String servant) implements CustomPayload {
        public static final Id<WishPayload> ID = new Id<>(FateUBW.id("grail_wish"));
        public static final PacketCodec<RegistryByteBuf, WishPayload> CODEC =
                PacketCodec.tuple(PacketCodecs.STRING, WishPayload::servant, WishPayload::new);

        @Override
        public Id<? extends CustomPayload> getId() {
            return ID;
        }
    }

    public HolyGrailItem(Item.Settings settings) {
        super(settings);
    }

    public static void register() {
        PayloadTypeRegistry.playC2S().register(WishPayload.ID, WishPayload.CODEC);
        ServerPlayNetworking.registerGlobalReceiver(WishPayload.ID, (payload, context) -> wish(context.player(), payload.servant()));
    }

    @Override
    public boolean hasGlint(ItemStack stack) {
        return true;
    }

    @Override
    public TypedActionResult<ItemStack> use(World world, PlayerEntity user, Hand hand) {
        if (world.isClient) openWishScreen.run();
        return TypedActionResult.success(user.getStackInHand(hand), world.isClient());
    }

    // Comprueba que de verdad lleva un Grial en la mano: el paquete lo manda el cliente
    private static void wish(ServerPlayerEntity player, String servantId) {
        Hand hand = player.getMainHandStack().isOf(FateUBW.HOLY_GRAIL) ? Hand.MAIN_HAND
                : player.getOffHandStack().isOf(FateUBW.HOLY_GRAIL) ? Hand.OFF_HAND : null;
        Servants.Servant servant = Servants.all().stream().filter(s -> s.id().equals(servantId)).findFirst().orElse(null);
        if (hand == null || servant == null || !player.isAlive()) return;
        ItemStack grail = player.getStackInHand(hand);
        if (!player.isCreative()) grail.decrement(1);

        player.setHealth(player.getMaxHealth());
        player.getHungerManager().setFoodLevel(20);
        player.addStatusEffect(new StatusEffectInstance(StatusEffects.ABSORPTION, 20 * 120, 3));
        CommandSeals.set(player, CommandSeals.MAX);
        Mana.fill(player);
        servant.equip(player);

        ServerWorld world = player.getServerWorld();
        world.spawnParticles(ParticleTypes.END_ROD, player.getX(), player.getY() + 1.0, player.getZ(), 120, 0.4, 2.0, 0.4, 0.15);
        world.spawnParticles(ParticleTypes.TOTEM_OF_UNDYING, player.getX(), player.getBodyY(0.5), player.getZ(), 60, 0.6, 1.0, 0.6, 0.3);
        world.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, SoundCategory.PLAYERS, 1.0F, 1.0F);
        world.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.BLOCK_BEACON_ACTIVATE, SoundCategory.PLAYERS, 1.5F, 0.8F);
        player.sendMessage(Text.translatable("message.fate_ubw.wish", Text.translatable("servant.fate_ubw." + servant.id()))
                .formatted(Formatting.GOLD, Formatting.BOLD), true);
    }

    @Override
    public void appendTooltip(ItemStack stack, Item.TooltipContext context, List<Text> tooltip, TooltipType type) {
        tooltip.add(Text.translatable("item.fate_ubw.holy_grail.tooltip").formatted(Formatting.GOLD));
    }
}
