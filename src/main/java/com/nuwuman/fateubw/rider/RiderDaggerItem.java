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
        if (user.isSneaking()) {
            if (user.getItemCooldownManager().isCoolingDown(FateUBW.MYSTIC_EYES)) {
                if (!world.isClient) FateUBW.cooldownMessage(user, FateUBW.MYSTIC_EYES, MYSTIC_EYES_COOLDOWN, "mystic_eyes");
                return TypedActionResult.fail(stack);
            }
            if (world instanceof ServerWorld server) mysticEyes(server, user);
            user.getItemCooldownManager().set(FateUBW.MYSTIC_EYES, MYSTIC_EYES_COOLDOWN);
            return TypedActionResult.success(stack, world.isClient());
        }
        if (world instanceof ServerWorld server) {
            ChainDaggerEntity.toss(server, user, stack);
            world.playSound(null, user.getX(), user.getY(), user.getZ(), SoundEvents.BLOCK_CHAIN_PLACE, SoundCategory.PLAYERS, 1.2F, 0.8F);
        }
        user.getItemCooldownManager().set(this, THROW_COOLDOWN);
        return TypedActionResult.success(stack, world.isClient());
    }

    // Cybele: quien mira a Medusa y está delante de ella queda casi petrificado
    private void mysticEyes(ServerWorld world, PlayerEntity player) {
        Vec3d eye = player.getEyePos();
        Vec3d dir = player.getRotationVec(1.0F);
        world.spawnParticles(PURPLE, eye.x + dir.x * 0.4, eye.y, eye.z + dir.z * 0.4, 20, 0.15, 0.05, 0.15, 0.0);
        world.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.ENTITY_ELDER_GUARDIAN_CURSE, SoundCategory.PLAYERS, 0.8F, 1.4F);

        for (LivingEntity target : world.getEntitiesByClass(LivingEntity.class, player.getBoundingBox().expand(MYSTIC_EYES_RANGE),
                e -> e != player && e.isAlive() && player.canSee(e))) {
            Vec3d to = target.getEyePos().subtract(eye);
            if (to.length() > MYSTIC_EYES_RANGE || to.normalize().dotProduct(dir) < 0.5) continue;
            target.addStatusEffect(new StatusEffectInstance(StatusEffects.SLOWNESS, 100, 4));
            target.addStatusEffect(new StatusEffectInstance(StatusEffects.MINING_FATIGUE, 100, 2));
            target.addStatusEffect(new StatusEffectInstance(StatusEffects.WEAKNESS, 100, 1));
            world.spawnParticles(STONE, target.getX(), target.getBodyY(0.5), target.getZ(), 30,
                    target.getWidth() / 2, target.getHeight() / 3, target.getWidth() / 2, 0.0);
            world.playSound(null, target.getX(), target.getY(), target.getZ(), SoundEvents.BLOCK_STONE_PLACE, SoundCategory.PLAYERS, 1.0F, 0.6F);
        }
    }

    @Override
    public void appendTooltip(ItemStack stack, Item.TooltipContext context, List<Text> tooltip, TooltipType type) {
        tooltip.add(Text.translatable("item.fate_ubw.rider_dagger.tooltip.throw").formatted(Formatting.GRAY));
        tooltip.add(Text.translatable("item.fate_ubw.rider_dagger.tooltip.eyes").formatted(Formatting.LIGHT_PURPLE));
    }
}
