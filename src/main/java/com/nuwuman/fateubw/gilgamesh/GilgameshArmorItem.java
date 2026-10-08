package com.nuwuman.fateubw.gilgamesh;

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

/** Armadura dorada del Rey de los Héroes con escarcelas animadas. Conjunto completo: Regla de Oro (Suerte II, Resistencia I). */
public class GilgameshArmorItem extends ServantArmorItem {
    public GilgameshArmorItem(ArmorItem.Type type, Item.Settings settings) {
        super(FateUBW.GILGAMESH_MATERIAL, type, settings, "gilgamesh_armor");
    }

    @Override
    public void inventoryTick(ItemStack stack, World world, Entity entity, int slot, boolean selected) {
        if (getType() != Type.CHESTPLATE || world.isClient || !(entity instanceof PlayerEntity player)) return;
        if (player.age % 40 != 0 || player.getEquippedStack(EquipmentSlot.CHEST) != stack) return;
        if (!wearsSet(player, FateUBW.GILGAMESH_CHESTPLATE, FateUBW.GILGAMESH_LEGGINGS, FateUBW.GILGAMESH_BOOTS)) return;
        player.addStatusEffect(new StatusEffectInstance(StatusEffects.LUCK, 60, 1, true, false));
        player.addStatusEffect(new StatusEffectInstance(StatusEffects.RESISTANCE, 60, 0, true, false));
    }

    @Override
    public void appendTooltip(ItemStack stack, Item.TooltipContext context, List<Text> tooltip, TooltipType type) {
        tooltip.add(Text.translatable("item.fate_ubw.gilgamesh_armor.tooltip.set").formatted(Formatting.GOLD));
    }
}
