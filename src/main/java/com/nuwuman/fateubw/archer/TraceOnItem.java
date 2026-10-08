package com.nuwuman.fateubw.archer;

import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Hand;
import net.minecraft.util.TypedActionResult;
import net.minecraft.world.World;

/**
 * Trace On como ítem (versión 1.8.0). Ahora es una habilidad del conjunto de Archer y el ítem ya no se fabrica;
 * se conserva para que los que ya existen sigan funcionando igual que la habilidad.
 */
public class TraceOnItem extends Item {
    public TraceOnItem(Item.Settings settings) {
        super(settings);
    }

    @Override
    public TypedActionResult<ItemStack> use(World world, PlayerEntity user, Hand hand) {
        ItemStack stack = user.getStackInHand(hand);
        if (user instanceof ServerPlayerEntity player && !player.getItemCooldownManager().isCoolingDown(this) && TraceOn.use(player)) {
            com.nuwuman.fateubw.Rules.cooldown(player, this, TraceOn.COOLDOWN);
        }
        return TypedActionResult.success(stack, world.isClient());
    }
}
