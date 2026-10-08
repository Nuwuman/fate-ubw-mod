package com.nuwuman.fateubw.master;

import com.nuwuman.fateubw.Rules;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.SwordItem;
import net.minecraft.item.ToolMaterials;
import net.minecraft.item.tooltip.TooltipType;
import net.minecraft.particle.ParticleTypes;
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
 * El póster enrollado que Shirou refuerza con magia para pelear: pega como una espada de hierro.
 * Click derecho: Refuerzo, repara una cuarta parte de lo que lleves en la otra mano y te da Fuerza I 20 s.
 */
public class ShirouPosterItem extends SwordItem {
    private static final int COOLDOWN = 20 * 30;

    public ShirouPosterItem(Item.Settings settings) {
        super(ToolMaterials.IRON, settings.attributeModifiers(SwordItem.createAttributeModifiers(ToolMaterials.IRON, 3, -2.4F)));
    }

    @Override
    public TypedActionResult<ItemStack> use(World world, PlayerEntity user, Hand hand) {
        ItemStack stack = user.getStackInHand(hand);
        if (user.getItemCooldownManager().isCoolingDown(this)) return TypedActionResult.fail(stack);
        ItemStack other = user.getStackInHand(hand == Hand.MAIN_HAND ? Hand.OFF_HAND : Hand.MAIN_HAND);
        ItemStack reinforced = other.isDamageable() ? other : stack;
        reinforced.setDamage(Math.max(0, reinforced.getDamage() - reinforced.getMaxDamage() / 4));
        user.addStatusEffect(new StatusEffectInstance(StatusEffects.STRENGTH, 20 * 20, 0));
        if (world instanceof ServerWorld server) {
            server.spawnParticles(ParticleTypes.ENCHANT, user.getX(), user.getBodyY(0.6), user.getZ(), 30, 0.4, 0.5, 0.4, 0.6);
            server.spawnParticles(ParticleTypes.ELECTRIC_SPARK, user.getX(), user.getBodyY(0.6), user.getZ(), 10, 0.3, 0.3, 0.3, 0.1);
            user.sendMessage(Text.translatable("message.fate_ubw.reinforcement", reinforced.getName()).formatted(Formatting.AQUA), true);
        }
        world.playSound(null, user.getX(), user.getY(), user.getZ(), SoundEvents.BLOCK_ANVIL_USE, SoundCategory.PLAYERS, 0.6F, 1.6F);
        Rules.cooldown(user, this, COOLDOWN);
        return TypedActionResult.success(stack, world.isClient());
    }

    @Override
    public void appendTooltip(ItemStack stack, Item.TooltipContext context, List<Text> tooltip, TooltipType type) {
        tooltip.add(Text.translatable("item.fate_ubw.shirou_poster.tooltip").formatted(Formatting.AQUA));
    }
}
