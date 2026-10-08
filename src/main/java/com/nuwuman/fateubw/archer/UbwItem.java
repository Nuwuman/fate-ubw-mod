package com.nuwuman.fateubw.archer;

import com.nuwuman.fateubw.FateUBW;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Hand;
import net.minecraft.util.TypedActionResult;
import net.minecraft.util.UseAction;
import net.minecraft.world.World;

/**
 * Unlimited Blade Works como ítem (hasta la versión 1.8.0). Ahora es una habilidad del conjunto de Archer y el ítem
 * ya no se fabrica; se conserva para que los que ya existen sigan funcionando: mantener 3 s y soltar lo despliega,
 * dentro lanza la ráfaga y agachado lo deshace.
 */
public class UbwItem extends Item {
    public UbwItem(Item.Settings settings) {
        super(settings);
    }

    @Override
    public UseAction getUseAction(ItemStack stack) {
        return UseAction.NONE;
    }

    @Override
    public int getMaxUseTime(ItemStack stack, LivingEntity user) {
        return 72000;
    }

    @Override
    public TypedActionResult<ItemStack> use(World world, PlayerEntity user, Hand hand) {
        ItemStack stack = user.getStackInHand(hand);
        if (UnlimitedBladeWorks.isInside(user)) {
            if (user.isSneaking()) {
                if (user instanceof ServerPlayerEntity player) UnlimitedBladeWorks.end(player);
            } else if (!user.getItemCooldownManager().isCoolingDown(FateUBW.UBW_BARRAGE)) {
                if (world instanceof ServerWorld server) UnlimitedBladeWorks.barrage(server, user);
                user.getItemCooldownManager().set(FateUBW.UBW_BARRAGE, UnlimitedBladeWorks.BARRAGE_COOLDOWN);
            }
            return TypedActionResult.success(stack, world.isClient());
        }
        if (user.getItemCooldownManager().isCoolingDown(FateUBW.UBW_COOLDOWN)) {
            if (!world.isClient) FateUBW.cooldownMessage(user, FateUBW.UBW_COOLDOWN, UnlimitedBladeWorks.MARBLE_COOLDOWN, "unlimited_blade_works");
            return TypedActionResult.fail(stack);
        }
        user.setCurrentHand(hand);
        return TypedActionResult.consume(stack);
    }

    @Override
    public void usageTick(World world, LivingEntity user, ItemStack stack, int remainingUseTicks) {
        if (world instanceof ServerWorld server && !UnlimitedBladeWorks.isInside(user)) {
            UnlimitedBladeWorks.chantEffects(server, user, getMaxUseTime(stack, user) - remainingUseTicks);
        }
    }

    @Override
    public void onStoppedUsing(ItemStack stack, World world, LivingEntity user, int remainingUseTicks) {
        int charge = getMaxUseTime(stack, user) - remainingUseTicks;
        if (charge >= UnlimitedBladeWorks.CHANT_TICKS && user instanceof ServerPlayerEntity player) UnlimitedBladeWorks.open(player);
    }
}
