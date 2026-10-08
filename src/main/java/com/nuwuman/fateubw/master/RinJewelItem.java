package com.nuwuman.fateubw.master;

import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.tooltip.TooltipType;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Hand;
import net.minecraft.util.TypedActionResult;
import net.minecraft.world.World;

import java.util.List;

/** Joya de Tohsaka (Rin): se lanza con click derecho y estalla al chocar. Se gasta. */
public class RinJewelItem extends Item {
    public RinJewelItem(Item.Settings settings) {
        super(settings);
    }

    @Override
    public TypedActionResult<ItemStack> use(World world, PlayerEntity user, Hand hand) {
        ItemStack stack = user.getStackInHand(hand);
        world.playSound(null, user.getX(), user.getY(), user.getZ(), SoundEvents.BLOCK_AMETHYST_BLOCK_CHIME, SoundCategory.PLAYERS, 1.0F, 1.4F);
        if (!world.isClient) {
            RinJewelEntity jewel = new RinJewelEntity(world, user);
            jewel.setItem(stack);
            jewel.setVelocity(user, user.getPitch(), user.getYaw(), 0.0F, 1.6F, 0.5F);
            world.spawnEntity(jewel);
        }
        user.getItemCooldownManager().set(this, 10);
        if (!user.isCreative()) stack.decrement(1);
        return TypedActionResult.success(stack, world.isClient());
    }

    @Override
    public void appendTooltip(ItemStack stack, Item.TooltipContext context, List<Text> tooltip, TooltipType type) {
        tooltip.add(Text.translatable("item.fate_ubw.rin_jewel.tooltip").formatted(Formatting.RED));
    }
}
