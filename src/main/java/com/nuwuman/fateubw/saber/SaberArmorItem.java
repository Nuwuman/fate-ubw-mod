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
 * Armadura de Artoria con falda animada. Conjunto completo: Avalon (Regeneración I)
 * y Resistencia Mágica (inmune al daño mágico y al Wither).
 */
public class SaberArmorItem extends ServantArmorItem {
    public SaberArmorItem(ArmorItem.Type type, Item.Settings settings) {
        super(FateUBW.SABER_MATERIAL, type, settings, "saber_armor");
    }

    private static boolean fullSet(LivingEntity entity) {
        return entity instanceof PlayerEntity player
                && wearsSet(player, FateUBW.SABER_CHESTPLATE, FateUBW.SABER_LEGGINGS, FateUBW.SABER_BOOTS);
    }

    // Avalon: la vaina sagrada cura las heridas
    @Override
    public void inventoryTick(ItemStack stack, World world, Entity entity, int slot, boolean selected) {
        if (getType() != Type.CHESTPLATE || world.isClient || !(entity instanceof PlayerEntity player)) return;
        if (player.age % 40 != 0 || player.getEquippedStack(EquipmentSlot.CHEST) != stack || !fullSet(player)) return;
        player.addStatusEffect(new StatusEffectInstance(StatusEffects.REGENERATION, 60, 0, true, false));
        player.removeStatusEffect(StatusEffects.WITHER);
    }

    /** Resistencia Mágica: anula el daño mágico (pociones, evocadores, aliento de dragón) y el del Wither. */
    public static boolean allowDamage(LivingEntity entity, DamageSource source, float amount) {
        if (!fullSet(entity)) return true;
        boolean magic = source.isOf(DamageTypes.MAGIC) || source.isOf(DamageTypes.INDIRECT_MAGIC)
                || source.isOf(DamageTypes.DRAGON_BREATH) || source.isOf(DamageTypes.WITHER);
        if (!magic) return true;
        if (entity.getWorld() instanceof ServerWorld world) {
            world.spawnParticles(ParticleTypes.ENCHANT, entity.getX(), entity.getBodyY(0.6), entity.getZ(), 15, 0.4, 0.6, 0.4, 0.5);
        }
        return false;
    }

    @Override
    public void appendTooltip(ItemStack stack, Item.TooltipContext context, List<Text> tooltip, TooltipType type) {
        tooltip.add(Text.translatable("item.fate_ubw.saber_armor.tooltip.avalon").formatted(Formatting.GOLD));
        tooltip.add(Text.translatable("item.fate_ubw.saber_armor.tooltip.magic").formatted(Formatting.AQUA));
    }
}
