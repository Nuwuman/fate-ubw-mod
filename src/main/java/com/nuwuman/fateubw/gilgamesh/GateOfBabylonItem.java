package com.nuwuman.fateubw.gilgamesh;

import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.tooltip.TooltipType;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Hand;
import net.minecraft.util.TypedActionResult;
import net.minecraft.world.World;

import java.util.List;

/** La llave de la Sala del Tesoro: abre el Gate of Babylon. */
public class GateOfBabylonItem extends Item {
    private static final int COOLDOWN = 20 * 12;

    public GateOfBabylonItem(Item.Settings settings) {
        super(settings);
    }

    @Override
    public TypedActionResult<ItemStack> use(World world, PlayerEntity user, Hand hand) {
        if (world instanceof ServerWorld server) BabylonPortalEntity.openGate(server, user);
        user.getItemCooldownManager().set(this, COOLDOWN);
        return TypedActionResult.success(user.getStackInHand(hand), world.isClient());
    }

    @Override
    public void appendTooltip(ItemStack stack, Item.TooltipContext context, List<Text> tooltip, TooltipType type) {
        tooltip.add(Text.translatable("item.fate_ubw.gate_of_babylon.tooltip").formatted(Formatting.GOLD));
    }
}
