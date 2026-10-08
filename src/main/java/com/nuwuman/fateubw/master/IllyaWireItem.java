package com.nuwuman.fateubw.master;

import com.nuwuman.fateubw.Rules;
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

/** Hilo de plata de Illya: click derecho invoca tres Zelzeriz que vuelan a tu alrededor y atacan a los monstruos. */
public class IllyaWireItem extends Item {
    private static final int COOLDOWN = 20 * 40;

    public IllyaWireItem(Item.Settings settings) {
        super(settings);
    }

    @Override
    public TypedActionResult<ItemStack> use(World world, PlayerEntity user, Hand hand) {
        ItemStack stack = user.getStackInHand(hand);
        if (user.getItemCooldownManager().isCoolingDown(this)) return TypedActionResult.fail(stack);
        if (world instanceof ServerWorld server) ZelzerizEntity.summon(server, user, 3);
        Rules.cooldown(user, this, COOLDOWN);
        return TypedActionResult.success(stack, world.isClient());
    }

    @Override
    public void appendTooltip(ItemStack stack, Item.TooltipContext context, List<Text> tooltip, TooltipType type) {
        tooltip.add(Text.translatable("item.fate_ubw.zelzeriz.tooltip").formatted(Formatting.WHITE));
    }
}
