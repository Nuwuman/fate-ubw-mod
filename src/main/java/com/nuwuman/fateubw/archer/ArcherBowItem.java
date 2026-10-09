package com.nuwuman.fateubw.archer;

import com.nuwuman.fateubw.FateUBW;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.BowItem;
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
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

import java.util.List;

/** Arco de EMIYA: dispara espadas proyectadas (sin gastar flechas). Agachado y a tope: Caladbolg II. */
public class ArcherBowItem extends BowItem {
    public static final int CALADBOLG_COOLDOWN = 20 * 15;

    public ArcherBowItem(Item.Settings settings) {
        super(settings);
    }

    @Override
    public TypedActionResult<ItemStack> use(World world, PlayerEntity user, Hand hand) {
        user.setCurrentHand(hand);
        return TypedActionResult.consume(user.getStackInHand(hand));
    }

    private static boolean caladbolgReady(PlayerEntity player) {
        return player.isSneaking() && !player.getItemCooldownManager().isCoolingDown(FateUBW.CALADBOLG);
    }

    // Mientras se tensa Caladbolg: chispas en la punta y un zumbido al llegar a tope
    @Override
    public void usageTick(World world, LivingEntity user, ItemStack stack, int remainingUseTicks) {
        if (!(world instanceof ServerWorld server) || !(user instanceof PlayerEntity player) || !caladbolgReady(player)) return;
        int used = getMaxUseTime(stack, user) - remainingUseTicks;
        Vec3d tip = player.getEyePos().add(player.getRotationVec(1.0F).multiply(1.2));
        if (used == 20) {
            world.playSound(null, player.getX(), player.getY(), player.getZ(),
                    SoundEvents.BLOCK_RESPAWN_ANCHOR_CHARGE, SoundCategory.PLAYERS, 1.5F, 0.8F);
        }
        if (used >= 20 && used % 2 == 0) {
            server.spawnParticles(ParticleTypes.ELECTRIC_SPARK, tip.x, tip.y - 0.2, tip.z, 3, 0.15, 0.15, 0.15, 0.05);
        }
    }

    @Override
    public void onStoppedUsing(ItemStack stack, World world, LivingEntity user, int remainingUseTicks) {
        if (!(user instanceof PlayerEntity player)) return;
        float pull = getPullProgress(getMaxUseTime(stack, user) - remainingUseTicks);
        if (pull < 0.1F) return;

        boolean wantsCaladbolg = player.isSneaking() && pull >= 1.0F;
        if (wantsCaladbolg && caladbolgReady(player)) com.nuwuman.fateubw.PlayerAnims.cinematic(player);
        if (!(world instanceof ServerWorld server)) return;
        // Sin recarga o sin maná sale una espada normal (ready avisa de lo que falte)
        boolean caladbolg = wantsCaladbolg && com.nuwuman.fateubw.Rules.ready(player, FateUBW.CALADBOLG, CALADBOLG_COOLDOWN, "caladbolg");

        SwordArrowEntity arrow = new SwordArrowEntity(server, player, stack, caladbolg);
        arrow.setVelocity(player, player.getPitch(), player.getYaw(), 0.0F, caladbolg ? 4.5F : pull * 3.0F, caladbolg ? 0.0F : 1.0F);
        if (pull >= 1.0F) arrow.setCritical(true);
        server.spawnEntity(arrow);
        stack.damage(1, player, LivingEntity.getSlotForHand(player.getActiveHand()));

        if (caladbolg) {
            com.nuwuman.fateubw.Rules.commit(player, FateUBW.CALADBOLG, CALADBOLG_COOLDOWN);
            com.nuwuman.fateubw.Voices.say(world, player, "caladbolg");
            world.playSound(null, player.getX(), player.getY(), player.getZ(),
                    SoundEvents.ENTITY_WITHER_SHOOT, SoundCategory.PLAYERS, 1.5F, 1.4F);
            world.playSound(null, player.getX(), player.getY(), player.getZ(),
                    SoundEvents.ENTITY_LIGHTNING_BOLT_THUNDER, SoundCategory.PLAYERS, 0.8F, 1.6F);
        } else {
            world.playSound(null, player.getX(), player.getY(), player.getZ(),
                    SoundEvents.ENTITY_ARROW_SHOOT, SoundCategory.PLAYERS, 1.0F, 1.0F / (world.getRandom().nextFloat() * 0.4F + 1.2F) + pull * 0.5F);
        }
    }

    @Override
    public void appendTooltip(ItemStack stack, Item.TooltipContext context, List<Text> tooltip, TooltipType type) {
        tooltip.add(Text.translatable("item.fate_ubw.archer_bow.tooltip.shoot").formatted(Formatting.GRAY));
        tooltip.add(Text.translatable("item.fate_ubw.archer_bow.tooltip.caladbolg").formatted(Formatting.RED));
    }
}
