package com.nuwuman.fateubw.saber;

import com.nuwuman.fateubw.FateUBW;
import com.nuwuman.fateubw.ServantArmorItem;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.damage.DamageTypes;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ArmorItem;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.tooltip.TooltipType;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.world.World;

import java.util.List;

/**
 * Armadura de Saber Alter (la de Saber teñida de negro y rojo). Conjunto completo: Fuerza I, Resistencia Mágica como
 * Saber y 150 de maná máximo en vez de 100. No tiene Avalon; sí Mana Burst, con tres cargas que se recargan de una en
 * una (6 s cada una).
 */
public class SaberAlterArmorItem extends ServantArmorItem {
    public static final float MAX_MANA = 150.0F;
    public static final int BURST_CHARGES = 3;
    public static final int BURST_RECHARGE = SaberArmorItem.MANA_BURST_COOLDOWN;   // lo que tarda en volver cada carga
    private static final int BURST_GAP = 10;                                         // entre una embestida y la siguiente
    /** Cargas de Mana Burst que le quedan (sincronizadas con su cliente para el HUD). */
    public static final net.fabricmc.fabric.api.attachment.v1.AttachmentType<Integer> BURST_LEFT =
            net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry.<Integer>builder()
                    .persistent(com.mojang.serialization.Codec.INT).initializer(() -> BURST_CHARGES)
                    .syncWith(net.minecraft.network.codec.PacketCodecs.VAR_INT.cast(),
                            net.fabricmc.fabric.api.attachment.v1.AttachmentSyncPredicate.targetOnly())
                    .buildAndRegister(FateUBW.id("mana_burst_charges"));
    // Tick del mundo en que vuelve la siguiente carga
    private static final net.fabricmc.fabric.api.attachment.v1.AttachmentType<Long> BURST_NEXT =
            net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry.<Long>builder()
                    .persistent(com.mojang.serialization.Codec.LONG).buildAndRegister(FateUBW.id("mana_burst_next"));

    public SaberAlterArmorItem(ArmorItem.Type type, Item.Settings settings) {
        super(FateUBW.SABER_ALTER_MATERIAL, type, settings, "saber_alter_armor");
    }

    public static boolean fullSet(LivingEntity entity) {
        return entity instanceof PlayerEntity player
                && wearsSet(player, FateUBW.SABER_ALTER_CHESTPLATE, FateUBW.SABER_ALTER_LEGGINGS, FateUBW.SABER_ALTER_BOOTS);
    }

    public static int charges(PlayerEntity player) {
        Integer left = player.getAttached(BURST_LEFT);
        return left == null ? BURST_CHARGES : left;
    }

    /** Las cargas vuelven de una en una aunque no lleve la armadura puesta. */
    public static void register() {
        net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents.END_SERVER_TICK.register(server -> {
            for (net.minecraft.server.network.ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
                int left = charges(player);
                if (left >= BURST_CHARGES) continue;
                long now = player.getServerWorld().getTime();
                Long next = player.getAttached(BURST_NEXT);
                if (next != null && now < next) continue;
                player.setAttached(BURST_LEFT, left + 1);
                if (left + 1 < BURST_CHARGES) player.setAttached(BURST_NEXT, now + BURST_RECHARGE);
            }
        });
    }

    /** Mana Burst con cargas: gasta una; si era la primera, empieza a contar la recarga. */
    public static boolean manaBurst(net.minecraft.server.network.ServerPlayerEntity player) {
        int left = charges(player);
        if (left <= 0 || !SaberArmorItem.manaBurst(player)) return false;
        long now = player.getServerWorld().getTime();
        if (left == BURST_CHARGES) player.setAttached(BURST_NEXT, now + BURST_RECHARGE);
        player.setAttached(BURST_LEFT, left - 1);
        return true;
    }

    /** Recarga tras usarla: un respiro si quedan cargas; si no, hasta que vuelva la siguiente. */
    public static int cooldownAfterBurst(net.minecraft.server.network.ServerPlayerEntity player) {
        if (charges(player) > 0) return BURST_GAP;
        Long next = player.getAttached(BURST_NEXT);
        return next == null ? BURST_RECHARGE : (int) Math.max(BURST_GAP, next - player.getServerWorld().getTime());
    }

    @Override
    public void inventoryTick(ItemStack stack, World world, Entity entity, int slot, boolean selected) {
        if (getType() != Type.CHESTPLATE || world.isClient || !(entity instanceof PlayerEntity player)) return;
        if (player.age % 40 != 0 || player.getEquippedStack(EquipmentSlot.CHEST) != stack || !fullSet(player)) return;
        player.addStatusEffect(new StatusEffectInstance(StatusEffects.STRENGTH, 60, 0, true, false));
    }

    /** Resistencia Mágica, como Saber: anula el daño mágico y el del Wither. */
    public static boolean allowDamage(LivingEntity entity, DamageSource source, float amount) {
        if (!fullSet(entity)) return true;
        boolean magic = source.isOf(DamageTypes.MAGIC) || source.isOf(DamageTypes.INDIRECT_MAGIC)
                || source.isOf(DamageTypes.DRAGON_BREATH) || source.isOf(DamageTypes.WITHER);
        if (!magic) return true;
        if (entity.getWorld() instanceof ServerWorld world) {
            world.spawnParticles(ParticleTypes.SMOKE, entity.getX(), entity.getBodyY(0.6), entity.getZ(), 15, 0.4, 0.6, 0.4, 0.05);
        }
        return false;
    }

    @Override
    public void appendTooltip(ItemStack stack, Item.TooltipContext context, List<Text> tooltip, TooltipType type) {
        tooltip.add(Text.translatable("item.fate_ubw.saber_alter_armor.tooltip.set").formatted(Formatting.DARK_RED));
        tooltip.add(Text.translatable("item.fate_ubw.saber_armor.tooltip.magic").formatted(Formatting.AQUA));
        tooltip.add(Text.translatable("item.fate_ubw.saber_alter_armor.tooltip.abilities").formatted(Formatting.LIGHT_PURPLE));
    }
}
