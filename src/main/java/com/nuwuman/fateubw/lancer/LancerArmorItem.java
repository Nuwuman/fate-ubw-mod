package com.nuwuman.fateubw.lancer;

import com.nuwuman.fateubw.FateUBW;
import com.nuwuman.fateubw.ServantArmorItem;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ArmorItem;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.tooltip.TooltipType;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.registry.tag.DamageTypeTags;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.world.World;

import java.util.List;

/**
 * Ropa de Cú Chulainn con coleta animada. Conjunto completo: Velocidad I, Protección contra Proyectiles
 * y Continuación de Batalla.
 */
public class LancerArmorItem extends ServantArmorItem {
    private static final float ARROW_PROTECTION_CHANCE = 0.75F;
    public static final int BATTLE_CONTINUATION_COOLDOWN = 20 * 60 * 5;

    public LancerArmorItem(ArmorItem.Type type, Item.Settings settings) {
        super(FateUBW.LANCER_MATERIAL, type, settings, "lancer_armor");
    }

    private static boolean fullSet(LivingEntity entity) {
        return entity instanceof PlayerEntity player
                && wearsSet(player, FateUBW.LANCER_CHESTPLATE, FateUBW.LANCER_LEGGINGS, FateUBW.LANCER_BOOTS);
    }

    @Override
    public void inventoryTick(ItemStack stack, World world, Entity entity, int slot, boolean selected) {
        if (getType() != Type.CHESTPLATE || world.isClient || !(entity instanceof PlayerEntity player)) return;
        if (player.age % 40 != 0 || player.getEquippedStack(EquipmentSlot.CHEST) != stack || !fullSet(player)) return;
        player.addStatusEffect(new StatusEffectInstance(StatusEffects.SPEED, 60, 0, true, false));
    }

    /** Protección contra Proyectiles: la mayoría de proyectiles no llegan a herirle. */
    public static boolean allowDamage(LivingEntity entity, DamageSource source, float amount) {
        if (!fullSet(entity) || !source.isIn(DamageTypeTags.IS_PROJECTILE)
                || entity.getRandom().nextFloat() >= ARROW_PROTECTION_CHANCE) return true;
        if (entity.getWorld() instanceof ServerWorld world) {
            world.spawnParticles(ParticleTypes.CRIT, entity.getX(), entity.getBodyY(0.6), entity.getZ(), 10, 0.4, 0.4, 0.4, 0.2);
            world.playSound(null, entity.getX(), entity.getY(), entity.getZ(), SoundEvents.ITEM_SHIELD_BLOCK, SoundCategory.PLAYERS, 1.0F, 1.3F);
        }
        return false;
    }

    /** Continuación de Batalla: sobrevive a un golpe mortal con 1 de vida (una vez cada 5 minutos). */
    public static boolean allowDeath(LivingEntity entity, DamageSource source, float amount) {
        if (!fullSet(entity) || !(entity instanceof PlayerEntity player)
                || player.getItemCooldownManager().isCoolingDown(FateUBW.BATTLE_CONTINUATION)) return true;
        player.setHealth(1.0F);
        player.addStatusEffect(new StatusEffectInstance(StatusEffects.REGENERATION, 100, 1));
        player.addStatusEffect(new StatusEffectInstance(StatusEffects.RESISTANCE, 60, 2));
        player.getItemCooldownManager().set(FateUBW.BATTLE_CONTINUATION, BATTLE_CONTINUATION_COOLDOWN);
        player.sendMessage(Text.translatable("message.fate_ubw.battle_continuation").formatted(Formatting.AQUA), true);
        if (player.getWorld() instanceof ServerWorld world) {
            world.spawnParticles(ParticleTypes.TOTEM_OF_UNDYING, player.getX(), player.getBodyY(0.5), player.getZ(), 40, 0.5, 0.8, 0.5, 0.3);
            world.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.ITEM_TOTEM_USE, SoundCategory.PLAYERS, 0.8F, 1.4F);
        }
        return false;
    }

    @Override
    public void appendTooltip(ItemStack stack, Item.TooltipContext context, List<Text> tooltip, TooltipType type) {
        tooltip.add(Text.translatable("item.fate_ubw.lancer_armor.tooltip.set").formatted(Formatting.GOLD));
        tooltip.add(Text.translatable("item.fate_ubw.lancer_armor.tooltip.arrows").formatted(Formatting.GRAY));
        tooltip.add(Text.translatable("item.fate_ubw.lancer_armor.tooltip.battle").formatted(Formatting.GRAY));
    }
}
