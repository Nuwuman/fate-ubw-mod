package com.nuwuman.fateubw.caster;

import com.nuwuman.fateubw.FateUBW;
import com.nuwuman.fateubw.archer.UnlimitedBladeWorks;
import com.nuwuman.fateubw.lancer.GaeBolgItem;
import com.nuwuman.fateubw.rider.PegasusEntity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.passive.AbstractHorseEntity;
import net.minecraft.entity.passive.TameableEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.SwordItem;
import net.minecraft.item.ToolMaterials;
import net.minecraft.item.tooltip.TooltipType;
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
import net.minecraft.world.World;
import org.joml.Vector3f;

import software.bernie.geckolib.animatable.GeoItem;
import software.bernie.geckolib.animatable.client.GeoRenderProvider;
import software.bernie.geckolib.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.animation.AnimatableManager;
import software.bernie.geckolib.util.GeckoLibUtil;

import java.util.List;

/**
 * Rule Breaker, la daga de Medea. Click derecho a quien tienes delante: la puñalada que rompe todo contrato mágico.
 * Le quita todos los efectos, rompe la doma (el animal pasa a ser tuyo), disipa a Pegaso y deshace su Reality Marble.
 */
public class RuleBreakerItem extends SwordItem implements GeoItem {
    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);

    // GeckoLib la dibuja para que brillen sus partes (textura _glowmask); no tiene animaciones
    @Override
    public void createGeoRenderer(java.util.function.Consumer<GeoRenderProvider> consumer) {
        com.nuwuman.fateubw.GlowingGeo.renderer(consumer, "rule_breaker");
    }

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return cache;
    }

    public static final int COOLDOWN = 20 * 20;
    private static final double REACH = 5.0;
    private static final DustParticleEffect[] RAINBOW = {
            new DustParticleEffect(new Vector3f(0.9F, 0.3F, 0.9F), 1.0F), new DustParticleEffect(new Vector3f(1.0F, 0.85F, 0.2F), 1.0F),
            new DustParticleEffect(new Vector3f(0.3F, 0.9F, 0.5F), 1.0F), new DustParticleEffect(new Vector3f(0.3F, 0.6F, 1.0F), 1.0F)};

    public RuleBreakerItem(Item.Settings settings) {
        super(ToolMaterials.NETHERITE, settings.attributeModifiers(
                SwordItem.createAttributeModifiers(ToolMaterials.NETHERITE, 2, -1.8F)));
    }

    @Override
    public TypedActionResult<ItemStack> use(World world, PlayerEntity user, Hand hand) {
        ItemStack stack = user.getStackInHand(hand);
        if (!com.nuwuman.fateubw.Rules.ready(user, FateUBW.RULE_BREAKER_NP, COOLDOWN, "rule_breaker")) return TypedActionResult.fail(stack);
        if (!(world instanceof ServerWorld server)) return TypedActionResult.success(stack, true);
        LivingEntity target = GaeBolgItem.findTarget(server, user, REACH, 0.8);
        if (target == null || !com.nuwuman.fateubw.Rules.canAffect(user, target)) {
            user.sendMessage(Text.translatable("message.fate_ubw.no_target").formatted(Formatting.GRAY), true);
            return TypedActionResult.fail(stack);
        }
        ruleBreaker(server, user, target);
        user.swingHand(hand, true);
        com.nuwuman.fateubw.Rules.commit(user, FateUBW.RULE_BREAKER_NP, COOLDOWN);
        return TypedActionResult.success(stack);
    }

    private static void ruleBreaker(ServerWorld world, PlayerEntity user, LivingEntity target) {
        target.damage(world.getDamageSources().indirectMagic(user, user), 8.0F);
        target.clearStatusEffects();
        if (target instanceof TameableEntity pet && pet.isTamed() && !pet.isOwner(user)) pet.setOwner(user);
        if (target instanceof AbstractHorseEntity horse && horse.isTame()) horse.bondWithPlayer(user);
        if (target instanceof PegasusEntity) target.discard();
        if (target.getVehicle() instanceof PegasusEntity pegasus) pegasus.discard();
        if (target instanceof ServerPlayerEntity player) UnlimitedBladeWorks.end(player);

        // Destello en zigzag de colores
        for (int i = 0; i < 24; i++) {
            double t = i / 23.0;
            double zig = (i % 4 < 2 ? 1 : -1) * 0.25;
            world.spawnParticles(RAINBOW[i % RAINBOW.length], target.getX() + zig, target.getY() + t * target.getHeight(),
                    target.getZ() - zig, 2, 0.05, 0.05, 0.05, 0.0);
        }
        world.spawnParticles(ParticleTypes.ENCHANT, target.getX(), target.getBodyY(0.5), target.getZ(), 40, 0.5, 0.7, 0.5, 0.8);
        world.playSound(null, target.getX(), target.getY(), target.getZ(), SoundEvents.BLOCK_GLASS_BREAK, SoundCategory.PLAYERS, 1.5F, 0.7F);
        world.playSound(null, target.getX(), target.getY(), target.getZ(), SoundEvents.ENTITY_ILLUSIONER_CAST_SPELL, SoundCategory.PLAYERS, 1.5F, 1.2F);
        user.sendMessage(Text.literal("Rule Breaker").formatted(Formatting.LIGHT_PURPLE, Formatting.BOLD), true);
        com.nuwuman.fateubw.Voices.say(world, user, "rule_breaker");
    }

    @Override
    public void appendTooltip(ItemStack stack, Item.TooltipContext context, List<Text> tooltip, TooltipType type) {
        tooltip.add(Text.translatable("item.fate_ubw.rule_breaker.tooltip").formatted(Formatting.LIGHT_PURPLE));
    }
}
