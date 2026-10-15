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
 * Saber y 150 de maná máximo en vez de 100. No tiene Avalon; sí Mana Burst.
 */
public class SaberAlterArmorItem extends ServantArmorItem {
    public static final float MAX_MANA = 150.0F;

    public SaberAlterArmorItem(ArmorItem.Type type, Item.Settings settings) {
        super(FateUBW.SABER_ALTER_MATERIAL, type, settings, "saber_alter_armor");
    }

    public static boolean fullSet(LivingEntity entity) {
        return entity instanceof PlayerEntity player
                && wearsSet(player, FateUBW.SABER_ALTER_CHESTPLATE, FateUBW.SABER_ALTER_LEGGINGS, FateUBW.SABER_ALTER_BOOTS);
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
