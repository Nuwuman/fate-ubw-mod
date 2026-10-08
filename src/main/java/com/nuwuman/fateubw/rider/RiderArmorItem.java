package com.nuwuman.fateubw.rider;

import com.nuwuman.fateubw.FateUBW;
import com.nuwuman.fateubw.ServantArmorItem;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ArmorItem;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.tooltip.TooltipType;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.world.World;

import java.util.List;

/** Ropa de Medusa: Breaker Gorgon (venda), vestido con melena animada, medias y botas. Conjunto completo: Velocidad I y Salto II. */
public class RiderArmorItem extends ServantArmorItem {
    public RiderArmorItem(ArmorItem.Type type, Item.Settings settings) {
        super(FateUBW.RIDER_MATERIAL, type, settings, "rider_armor");
    }

    @Override
    public void inventoryTick(ItemStack stack, World world, Entity entity, int slot, boolean selected) {
        if (getType() != Type.CHESTPLATE || world.isClient || !(entity instanceof PlayerEntity player)) return;
        if (player.age % 40 != 0 || player.getEquippedStack(EquipmentSlot.CHEST) != stack) return;
        if (!player.getEquippedStack(EquipmentSlot.HEAD).isOf(FateUBW.RIDER_HELMET)
                || !wearsSet(player, FateUBW.RIDER_CHESTPLATE, FateUBW.RIDER_LEGGINGS, FateUBW.RIDER_BOOTS)) return;
        player.addStatusEffect(new StatusEffectInstance(StatusEffects.SPEED, 60, 0, true, false));
        player.addStatusEffect(new StatusEffectInstance(StatusEffects.JUMP_BOOST, 60, 1, true, false));
    }

    @Override
    public void appendTooltip(ItemStack stack, Item.TooltipContext context, List<Text> tooltip, TooltipType type) {
        tooltip.add(Text.translatable("item.fate_ubw.rider_armor.tooltip.set").formatted(Formatting.LIGHT_PURPLE));
        tooltip.add(Text.translatable("item.fate_ubw.rider_armor.tooltip.abilities").formatted(Formatting.LIGHT_PURPLE));
    }
}
