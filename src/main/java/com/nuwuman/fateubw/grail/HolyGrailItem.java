package com.nuwuman.fateubw.grail;

import com.nuwuman.fateubw.ability.CommandSeals;
import com.nuwuman.fateubw.ability.Mana;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
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
 * El Santo Grial, premio de la Guerra del Santo Grial. Click derecho: pide tu deseo y se consume.
 * Te cura del todo, recupera los tres Sellos de Comando y el maná, y te concede el arma de un servant al azar.
 */
public class HolyGrailItem extends Item {
    public HolyGrailItem(Item.Settings settings) {
        super(settings);
    }

    @Override
    public boolean hasGlint(ItemStack stack) {
        return true;
    }

    @Override
    public TypedActionResult<ItemStack> use(World world, PlayerEntity user, Hand hand) {
        ItemStack stack = user.getStackInHand(hand);
        if (!(world instanceof ServerWorld server)) return TypedActionResult.success(stack, true);
        user.setHealth(user.getMaxHealth());
        user.getHungerManager().setFoodLevel(20);
        user.addStatusEffect(new StatusEffectInstance(StatusEffects.ABSORPTION, 20 * 120, 3));
        CommandSeals.set(user, CommandSeals.MAX);
        Mana.fill(user);
        Servants.Servant servant = Servants.all().get(server.random.nextInt(Servants.all().size()));
        Item weapon = servant.weapons().get(server.random.nextInt(servant.weapons().size()));
        user.getInventory().offerOrDrop(new ItemStack(weapon));

        server.spawnParticles(ParticleTypes.END_ROD, user.getX(), user.getY() + 1.0, user.getZ(), 120, 0.4, 2.0, 0.4, 0.15);
        server.spawnParticles(ParticleTypes.TOTEM_OF_UNDYING, user.getX(), user.getBodyY(0.5), user.getZ(), 60, 0.6, 1.0, 0.6, 0.3);
        world.playSound(null, user.getX(), user.getY(), user.getZ(), SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, SoundCategory.PLAYERS, 1.0F, 1.0F);
        world.playSound(null, user.getX(), user.getY(), user.getZ(), SoundEvents.BLOCK_BEACON_ACTIVATE, SoundCategory.PLAYERS, 1.5F, 0.8F);
        user.sendMessage(Text.translatable("message.fate_ubw.wish").formatted(Formatting.GOLD, Formatting.BOLD), true);
        if (!user.isCreative()) stack.decrement(1);
        return TypedActionResult.success(stack);
    }

    @Override
    public void appendTooltip(ItemStack stack, Item.TooltipContext context, List<Text> tooltip, TooltipType type) {
        tooltip.add(Text.translatable("item.fate_ubw.holy_grail.tooltip").formatted(Formatting.GOLD));
    }
}
