package com.nuwuman.fateubw.rider;

import com.nuwuman.fateubw.FateUBW;
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

/** Bridas de Bellerophon. Click derecho: invoca a Pegaso y lo monta. Montado: la embestida de Bellerophon. */
public class BellerophonItem extends Item {
    // Corto: también bloquea la embestida justo después de montar
    private static final int SUMMON_COOLDOWN = 20;
    public static final int CHARGE_COOLDOWN = 20 * 30;

    public BellerophonItem(Item.Settings settings) {
        super(settings);
    }

    @Override
    public TypedActionResult<ItemStack> use(World world, PlayerEntity user, Hand hand) {
        ItemStack stack = user.getStackInHand(hand);
        if (user.getVehicle() instanceof PegasusEntity pegasus) {
            if (user.getItemCooldownManager().isCoolingDown(FateUBW.BELLEROPHON_CHARGE)) {
                if (!world.isClient) FateUBW.cooldownMessage(user, FateUBW.BELLEROPHON_CHARGE, CHARGE_COOLDOWN, "bellerophon");
                return TypedActionResult.fail(stack);
            }
            if (!world.isClient) pegasus.startCharge();
            user.getItemCooldownManager().set(FateUBW.BELLEROPHON_CHARGE, CHARGE_COOLDOWN);
            return TypedActionResult.success(stack, world.isClient());
        }

        if (world instanceof ServerWorld server) {
            PegasusEntity pegasus = FateUBW.PEGASUS.create(server);
            if (pegasus == null) return TypedActionResult.fail(stack);
            pegasus.refreshPositionAndAngles(user.getX(), user.getY(), user.getZ(), user.getYaw(), 0.0F);
            server.spawnEntity(pegasus);
            user.startRiding(pegasus, true);
            server.spawnParticles(ParticleTypes.END_ROD, user.getX(), user.getBodyY(0.5), user.getZ(), 40, 1.0, 1.0, 1.0, 0.1);
            server.spawnParticles(ParticleTypes.CLOUD, user.getX(), user.getY() + 0.5, user.getZ(), 20, 1.0, 0.3, 1.0, 0.05);
            world.playSound(null, user.getX(), user.getY(), user.getZ(), SoundEvents.ENTITY_HORSE_AMBIENT, SoundCategory.PLAYERS, 1.2F, 1.2F);
            world.playSound(null, user.getX(), user.getY(), user.getZ(), SoundEvents.ENTITY_ENDER_DRAGON_FLAP, SoundCategory.PLAYERS, 1.0F, 1.4F);
        }
        user.getItemCooldownManager().set(this, SUMMON_COOLDOWN);
        return TypedActionResult.success(stack, world.isClient());
    }

    @Override
    public void appendTooltip(ItemStack stack, Item.TooltipContext context, List<Text> tooltip, TooltipType type) {
        tooltip.add(Text.translatable("item.fate_ubw.bellerophon.tooltip.summon").formatted(Formatting.GRAY));
        tooltip.add(Text.translatable("item.fate_ubw.bellerophon.tooltip.fly").formatted(Formatting.GRAY));
        tooltip.add(Text.translatable("item.fate_ubw.bellerophon.tooltip.charge").formatted(Formatting.GOLD));
    }
}
