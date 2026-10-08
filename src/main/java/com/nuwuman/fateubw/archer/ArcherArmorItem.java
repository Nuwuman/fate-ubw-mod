package com.nuwuman.fateubw.archer;

import com.nuwuman.fateubw.FateUBW;
import com.nuwuman.fateubw.ServantArmorItem;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.mob.Monster;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ArmorItem;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.tooltip.TooltipType;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.world.World;

import java.util.List;

/** Ropa de EMIYA con faldón animado. */
public class ArcherArmorItem extends ServantArmorItem {
    public ArcherArmorItem(ArmorItem.Type type, Item.Settings settings) {
        super(FateUBW.ARCHER_MATERIAL, type, settings, "archer_armor");
    }

    // Conjunto completo: visión nocturna y los monstruos cercanos brillan (Ojo de Halcón)
    @Override
    public void inventoryTick(ItemStack stack, World world, Entity entity, int slot, boolean selected) {
        if (getType() != Type.CHESTPLATE || !(world instanceof ServerWorld server) || !(entity instanceof PlayerEntity player)) return;
        if (player.age % 40 != 0 || player.getEquippedStack(EquipmentSlot.CHEST) != stack) return;
        if (!wearsSet(player, FateUBW.ARCHER_CHESTPLATE, FateUBW.ARCHER_LEGGINGS, FateUBW.ARCHER_BOOTS)) return;

        player.addStatusEffect(new StatusEffectInstance(StatusEffects.NIGHT_VISION, 260, 0, true, false));
        for (LivingEntity mob : server.getEntitiesByClass(LivingEntity.class, player.getBoundingBox().expand(32.0), e -> e instanceof Monster)) {
            mob.addStatusEffect(new StatusEffectInstance(StatusEffects.GLOWING, 60, 0, true, false));
        }
    }

    @Override
    public void appendTooltip(ItemStack stack, Item.TooltipContext context, List<Text> tooltip, TooltipType type) {
        tooltip.add(Text.translatable("item.fate_ubw.archer_armor.tooltip.set").formatted(Formatting.GOLD));
        tooltip.add(Text.translatable("item.fate_ubw.archer_armor.tooltip.abilities").formatted(Formatting.LIGHT_PURPLE));
    }
}
