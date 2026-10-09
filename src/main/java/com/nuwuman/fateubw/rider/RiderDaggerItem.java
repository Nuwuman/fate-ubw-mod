package com.nuwuman.fateubw.rider;

import com.nuwuman.fateubw.FateUBW;
import net.minecraft.block.Blocks;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.SwordItem;
import net.minecraft.item.ToolMaterials;
import net.minecraft.item.tooltip.TooltipType;
import net.minecraft.particle.BlockStateParticleEffect;
import net.minecraft.particle.DustParticleEffect;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Hand;
import net.minecraft.util.TypedActionResult;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import org.joml.Vector3f;

import java.util.List;

/** Daga con cadena de Medusa. Click derecho: la lanza (atrae enemigos o te impulsa hacia un bloque). Agachado: Ojos Místicos. */
public class RiderDaggerItem extends SwordItem {
    private static final int THROW_COOLDOWN = 30;
    public static final int MYSTIC_EYES_COOLDOWN = 20 * 20;
    private static final double MYSTIC_EYES_RANGE = 16.0;
    private static final DustParticleEffect PURPLE = new DustParticleEffect(new Vector3f(0.6F, 0.2F, 0.9F), 1.2F);
    private static final BlockStateParticleEffect STONE = new BlockStateParticleEffect(ParticleTypes.BLOCK, Blocks.STONE.getDefaultState());

    public RiderDaggerItem(Item.Settings settings) {
        super(ToolMaterials.NETHERITE, settings.attributeModifiers(
                SwordItem.createAttributeModifiers(ToolMaterials.NETHERITE, 2, -1.6F)));
    }

    @Override
    public TypedActionResult<ItemStack> use(World world, PlayerEntity user, Hand hand) {
        ItemStack stack = user.getStackInHand(hand);
        if (world instanceof ServerWorld server) {
            ChainDaggerEntity.toss(server, user, stack);
            world.playSound(null, user.getX(), user.getY(), user.getZ(), SoundEvents.BLOCK_CHAIN_PLACE, SoundCategory.PLAYERS, 1.2F, 0.8F);
        }
        com.nuwuman.fateubw.Rules.cooldown(user, this, THROW_COOLDOWN);
        return TypedActionResult.success(stack, world.isClient());
    }

    /** Cybele (habilidad de Breaker Gorgon): quien mira a Medusa y está delante de ella queda casi petrificado. */
    public static boolean mysticEyes(ServerPlayerEntity player) {
        ServerWorld world = player.getServerWorld();
        Vec3d eye = player.getEyePos();
        Vec3d dir = player.getRotationVec(1.0F);
        world.spawnParticles(PURPLE, eye.x + dir.x * 0.4, eye.y, eye.z + dir.z * 0.4, 20, 0.15, 0.05, 0.15, 0.0);
        world.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.ENTITY_ELDER_GUARDIAN_CURSE, SoundCategory.PLAYERS, 0.8F, 1.4F);
        boolean grip = com.nuwuman.fateubw.enchant.FateEnchantments.held(player, FateUBW.RIDER_DAGGER,
                com.nuwuman.fateubw.enchant.FateEnchantments.GORGONS_GRIP) > 0;

        for (LivingEntity target : world.getEntitiesByClass(LivingEntity.class, player.getBoundingBox().expand(MYSTIC_EYES_RANGE),
                e -> e != player && e.isAlive() && player.canSee(e) && com.nuwuman.fateubw.Rules.canAffect(player, e))) {
            Vec3d to = target.getEyePos().subtract(eye);
            if (to.length() > MYSTIC_EYES_RANGE || to.normalize().dotProduct(dir) < 0.5) continue;
            target.addStatusEffect(new StatusEffectInstance(StatusEffects.SLOWNESS, 100, 4));
            target.addStatusEffect(new StatusEffectInstance(StatusEffects.MINING_FATIGUE, 100, 2));
            target.addStatusEffect(new StatusEffectInstance(StatusEffects.WEAKNESS, 100, 1));
            world.spawnParticles(STONE, target.getX(), target.getBodyY(0.5), target.getZ(), 30,
                    target.getWidth() / 2, target.getHeight() / 3, target.getWidth() / 2, 0.0);
            world.playSound(null, target.getX(), target.getY(), target.getZ(), SoundEvents.BLOCK_STONE_PLACE, SoundCategory.PLAYERS, 1.0F, 0.6F);
            // Gorgon's Grip: con la daga en la mano, la daga remata al petrificado (el doble si ya está malherido)
            if (grip) {
                target.timeUntilRegen = 0;
                target.damage(world.getDamageSources().playerAttack(player), target.getHealth() < target.getMaxHealth() / 2 ? 12.0F : 6.0F);
                world.spawnParticles(ParticleTypes.CRIT, target.getX(), target.getBodyY(0.5), target.getZ(), 15, 0.3, 0.4, 0.3, 0.2);
                world.playSound(null, target.getX(), target.getY(), target.getZ(), SoundEvents.BLOCK_STONE_BREAK, SoundCategory.PLAYERS, 1.2F, 0.5F);
            }
        }
        return true;
    }

    @Override
    public void appendTooltip(ItemStack stack, Item.TooltipContext context, List<Text> tooltip, TooltipType type) {
        tooltip.add(Text.translatable("item.fate_ubw.rider_dagger.tooltip.throw").formatted(Formatting.GRAY));
    }
}
