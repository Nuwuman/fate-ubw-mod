package com.nuwuman.fateubw.lancer;

import com.nuwuman.fateubw.FateUBW;
import net.minecraft.component.type.AttributeModifierSlot;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.attribute.EntityAttributeModifier;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.SwordItem;
import net.minecraft.item.ToolMaterials;
import net.minecraft.item.tooltip.TooltipType;
import net.minecraft.particle.DustParticleEffect;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Hand;
import net.minecraft.util.TypedActionResult;
import net.minecraft.util.UseAction;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3f;

import software.bernie.geckolib.animatable.GeoItem;
import software.bernie.geckolib.animatable.client.GeoRenderProvider;
import software.bernie.geckolib.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.animation.AnimatableManager;
import software.bernie.geckolib.util.GeckoLibUtil;

import java.util.List;

/**
 * Gáe Bolg. Mantener y soltar: estocada que siempre acierta al corazón.
 * Agachado, mantener y soltar: salta y lanza la lanza, que persigue al objetivo y estalla.
 */
public class GaeBolgItem extends SwordItem implements GeoItem {
    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);

    // GeckoLib la dibuja para que brillen sus partes (textura _glowmask); no tiene animaciones
    @Override
    public void createGeoRenderer(java.util.function.Consumer<GeoRenderProvider> consumer) {
        com.nuwuman.fateubw.GlowingGeo.renderer(consumer, "gae_bolg");
    }

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return cache;
    }

    public static final int PIERCE_CHARGE = 20;
    public static final int SOARING_CHARGE = 40;
    public static final int PIERCE_COOLDOWN = 20 * 15;
    public static final int SOARING_COOLDOWN = 20 * 30;
    private static final double PIERCE_RANGE = 12.0;
    private static final float PIERCE_DAMAGE = 35.0F;
    public static final DustParticleEffect CRIMSON = new DustParticleEffect(new Vector3f(0.85F, 0.05F, 0.1F), 1.2F);

    public GaeBolgItem(Item.Settings settings) {
        super(ToolMaterials.NETHERITE, settings.attributeModifiers(
                SwordItem.createAttributeModifiers(ToolMaterials.NETHERITE, 5, -2.6F).with(
                        EntityAttributes.PLAYER_ENTITY_INTERACTION_RANGE,
                        new EntityAttributeModifier(FateUBW.id("gae_bolg_reach"), 1.5, EntityAttributeModifier.Operation.ADD_VALUE),
                        AttributeModifierSlot.MAINHAND)));
    }

    @Override
    public UseAction getUseAction(ItemStack stack) {
        return UseAction.SPEAR;
    }

    @Override
    public int getMaxUseTime(ItemStack stack, LivingEntity user) {
        return 72000;
    }

    @Override
    public TypedActionResult<ItemStack> use(World world, PlayerEntity user, Hand hand) {
        ItemStack stack = user.getStackInHand(hand);
        boolean soaring = user.isSneaking();
        Item key = soaring ? FateUBW.GAE_BOLG_SOARING : FateUBW.GAE_BOLG_PIERCE;
        if (!com.nuwuman.fateubw.Rules.ready(user, key, soaring ? SOARING_COOLDOWN : PIERCE_COOLDOWN,
                soaring ? "gae_bolg_soaring" : "gae_bolg_pierce")) return TypedActionResult.fail(stack);
        user.setCurrentHand(hand);
        return TypedActionResult.consume(stack);
    }

    // Energía carmesí alrededor de la lanza mientras se carga
    @Override
    public void usageTick(World world, LivingEntity user, ItemStack stack, int remainingUseTicks) {
        if (!(world instanceof ServerWorld server)) return;
        int charge = getMaxUseTime(stack, user) - remainingUseTicks;
        int needed = user.isSneaking() ? SOARING_CHARGE : PIERCE_CHARGE;
        server.spawnParticles(CRIMSON, user.getX(), user.getEyeY() + 0.3, user.getZ(), 2 + Math.min(charge, needed) / 8,
                0.4, 0.6, 0.4, 0.0);
        if (charge == needed) {
            world.playSound(null, user.getX(), user.getY(), user.getZ(), SoundEvents.BLOCK_RESPAWN_ANCHOR_CHARGE, SoundCategory.PLAYERS, 1.5F, 0.6F);
            server.spawnParticles(ParticleTypes.CRIMSON_SPORE, user.getX(), user.getBodyY(0.5), user.getZ(), 40, 0.8, 1.0, 0.8, 0.1);
        }
        if (charge > needed && charge % 4 == 0) {
            server.spawnParticles(ParticleTypes.CRIMSON_SPORE, user.getX(), user.getEyeY(), user.getZ(), 6, 0.5, 0.5, 0.5, 0.05);
        }
    }

    @Override
    public void onStoppedUsing(ItemStack stack, World world, LivingEntity user, int remainingUseTicks) {
        if (!(user instanceof PlayerEntity player)) return;
        int charge = getMaxUseTime(stack, user) - remainingUseTicks;
        boolean soaring = player.isSneaking();
        if (charge < (soaring ? SOARING_CHARGE : PIERCE_CHARGE)) return;
        com.nuwuman.fateubw.PlayerAnims.play(player, soaring ? "gae_bolg_throw" : "gae_bolg_pierce");

        if (!(world instanceof ServerWorld server)) return;
        // Puede haberse agachado o levantado mientras cargaba: se comprueba la versión que suelta
        Item key = soaring ? FateUBW.GAE_BOLG_SOARING : FateUBW.GAE_BOLG_PIERCE;
        int cooldown = soaring ? SOARING_COOLDOWN : PIERCE_COOLDOWN;
        if (!com.nuwuman.fateubw.Rules.ready(player, key, cooldown, soaring ? "gae_bolg_soaring" : "gae_bolg_pierce")) return;
        com.nuwuman.fateubw.Rules.commit(player, key, cooldown);
        player.swingHand(player.getActiveHand(), true);
        com.nuwuman.fateubw.Voices.say(world, player, "gae_bolg");
        if (soaring) {
            soaringSpear(server, player, stack);
        } else {
            pierce(server, player);
        }
    }

    // Gáe Bolg: la lanza que atraviesa con la muerte. Invierte la causalidad: el corazón ya está atravesado
    private void pierce(ServerWorld world, PlayerEntity player) {
        Vec3d dir = player.getRotationVec(1.0F);
        LivingEntity target = findTarget(world, player, PIERCE_RANGE, 0.95);
        world.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.ENTITY_PLAYER_ATTACK_SWEEP, SoundCategory.PLAYERS, 1.0F, 0.6F);
        if (target == null) {
            player.addVelocity(dir.x * 1.2, 0.1, dir.z * 1.2);
            player.velocityModified = true;
            return;
        }

        Vec3d eye = player.getEyePos();
        Vec3d heart = target.getBoundingBox().getCenter().add(0.0, target.getHeight() * 0.15, 0.0);
        Vec3d to = heart.subtract(eye);
        Vec3d dash = to.normalize().multiply(Math.max(0.0, Math.min(to.length() - 1.5, 6.0)) * 0.35);
        player.addVelocity(dash.x, 0.15, dash.z);
        player.velocityModified = true;

        target.damage(world.getDamageSources().create(FateUBW.GAE_BOLG_DAMAGE, player), PIERCE_DAMAGE);
        target.addStatusEffect(new StatusEffectInstance(StatusEffects.WITHER, 200, 1));

        Vec3d from = eye.add(0.0, -0.3, 0.0);
        for (int i = 0; i <= 24; i++) {
            Vec3d p = from.lerp(heart, i / 24.0);
            world.spawnParticles(CRIMSON, p.x, p.y, p.z, 2, 0.05, 0.05, 0.05, 0.0);
        }
        world.spawnParticles(ParticleTypes.FLASH, heart.x, heart.y, heart.z, 1, 0.0, 0.0, 0.0, 0.0);
        world.spawnParticles(ParticleTypes.CRIMSON_SPORE, heart.x, heart.y, heart.z, 30, 0.3, 0.3, 0.3, 0.2);
        world.spawnParticles(CRIMSON, heart.x, heart.y, heart.z, 30, 0.4, 0.4, 0.4, 0.0);
        world.playSound(null, target.getX(), target.getY(), target.getZ(), SoundEvents.ENTITY_PLAYER_ATTACK_CRIT, SoundCategory.PLAYERS, 1.5F, 0.7F);
        world.playSound(null, target.getX(), target.getY(), target.getZ(), SoundEvents.ENTITY_WITHER_BREAK_BLOCK, SoundCategory.PLAYERS, 0.6F, 1.6F);
    }

    // Gáe Bolg: la lanza que vuela con la muerte. Salto y lanzamiento
    private void soaringSpear(ServerWorld world, PlayerEntity player, ItemStack stack) {
        LivingEntity target = findTarget(world, player, 64.0, 0.9);
        player.addVelocity(0.0, 1.0, 0.0);
        player.velocityModified = true;
        GaeBolgSpearEntity.launch(world, player, stack, target);
        world.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.ENTITY_WITHER_SHOOT, SoundCategory.PLAYERS, 1.5F, 0.7F);
        world.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.ENTITY_LIGHTNING_BOLT_THUNDER, SoundCategory.PLAYERS, 0.6F, 1.8F);
    }

    /** El ser vivo visible más cercano a la línea de mirada (coseno del ángulo ≥ minDot) dentro del alcance. */
    @Nullable
    public static LivingEntity findTarget(ServerWorld world, PlayerEntity player, double range, double minDot) {
        Vec3d eye = player.getEyePos();
        Vec3d dir = player.getRotationVec(1.0F);
        Box area = player.getBoundingBox().stretch(dir.multiply(range)).expand(3.0);
        LivingEntity best = null;
        double bestDot = minDot;
        for (LivingEntity e : world.getEntitiesByClass(LivingEntity.class, area, e -> e != player && e.isAlive() && !e.isSpectator() && com.nuwuman.fateubw.Rules.canAffect(player, e))) {
            Vec3d to = e.getBoundingBox().getCenter().subtract(eye);
            if (to.length() > range) continue;
            double dot = to.normalize().dotProduct(dir);
            if (dot > bestDot && player.canSee(e)) {
                bestDot = dot;
                best = e;
            }
        }
        return best;
    }

    @Override
    public void appendTooltip(ItemStack stack, Item.TooltipContext context, List<Text> tooltip, TooltipType type) {
        tooltip.add(Text.translatable("item.fate_ubw.gae_bolg.tooltip.pierce").formatted(Formatting.RED));
        tooltip.add(Text.translatable("item.fate_ubw.gae_bolg.tooltip.soaring").formatted(Formatting.DARK_RED));
    }
}
