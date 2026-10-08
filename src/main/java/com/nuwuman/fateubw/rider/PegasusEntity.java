package com.nuwuman.fateubw.rider;

import com.nuwuman.fateubw.FateUBW;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.attribute.DefaultAttributeContainer;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.data.DataTracker;
import net.minecraft.entity.data.TrackedData;
import net.minecraft.entity.data.TrackedDataHandlerRegistry;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.mob.PathAwareEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;
import software.bernie.geckolib.animatable.GeoEntity;
import software.bernie.geckolib.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.animation.AnimatableManager;
import software.bernie.geckolib.animation.AnimationController;
import software.bernie.geckolib.animation.RawAnimation;
import software.bernie.geckolib.util.GeckoLibUtil;

import java.util.HashSet;
import java.util.Set;

/**
 * Pegaso, la montura de Medusa. Montado: anda por el suelo; mirando hacia arriba y avanzando despega y vuela
 * hacia donde mira el jinete. Con las bridas de Bellerophon embiste como un cometa.
 * El movimiento lo calcula el cliente del jinete (como cualquier montura); el daño de la embestida, el servidor.
 * Sin jinete durante 5 s, desaparece.
 */
public class PegasusEntity extends PathAwareEntity implements GeoEntity {
    private static final TrackedData<Integer> CHARGE = DataTracker.registerData(PegasusEntity.class, TrackedDataHandlerRegistry.INTEGER);
    private static final RawAnimation IDLE = RawAnimation.begin().thenLoop("animation.pegasus.idle");
    private static final RawAnimation WALK = RawAnimation.begin().thenLoop("animation.pegasus.walk");
    private static final RawAnimation FLY = RawAnimation.begin().thenLoop("animation.pegasus.fly");
    public static final int CHARGE_TICKS = 30;
    private static final double FLY_SPEED = 0.9;
    private static final double CHARGE_SPEED = 2.4;
    private static final float CHARGE_DAMAGE = 40.0F;
    private static final int RIDERLESS_TICKS = 100;

    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);
    private final Set<Integer> chargeHits = new HashSet<>();
    private int riderless;

    public PegasusEntity(EntityType<? extends PegasusEntity> type, World world) {
        super(type, world);
    }

    public static DefaultAttributeContainer.Builder createAttributes() {
        return MobEntity.createMobAttributes()
                .add(EntityAttributes.GENERIC_MAX_HEALTH, 40.0)
                .add(EntityAttributes.GENERIC_MOVEMENT_SPEED, 0.3);
    }

    @Override
    protected void initDataTracker(DataTracker.Builder builder) {
        super.initDataTracker(builder);
        builder.add(CHARGE, 0);
    }

    public int getCharge() {
        return dataTracker.get(CHARGE);
    }

    public void startCharge() {
        dataTracker.set(CHARGE, CHARGE_TICKS);
        chargeHits.clear();
        getWorld().playSound(null, getX(), getY(), getZ(), SoundEvents.ENTITY_ENDER_DRAGON_GROWL, SoundCategory.PLAYERS, 1.5F, 1.5F);
        getWorld().playSound(null, getX(), getY(), getZ(), SoundEvents.ENTITY_LIGHTNING_BOLT_THUNDER, SoundCategory.PLAYERS, 1.5F, 1.2F);
    }

    // ---------- montura ----------
    @Nullable
    @Override
    public LivingEntity getControllingPassenger() {
        return getFirstPassenger() instanceof PlayerEntity player ? player : null;
    }

    @Override
    protected ActionResult interactMob(PlayerEntity player, Hand hand) {
        if (hasPassengers()) return super.interactMob(player, hand);
        if (!getWorld().isClient) player.startRiding(this);
        return ActionResult.success(getWorld().isClient);
    }

    @Override
    protected void tickControlled(PlayerEntity player, Vec3d movementInput) {
        super.tickControlled(player, movementInput);
        setYaw(player.getYaw());
        prevYaw = getYaw();
        setPitch(0.0F);
        bodyYaw = getYaw();
        headYaw = getYaw();

        Vec3d look = player.getRotationVec(1.0F);
        if (getCharge() > 0) {
            setNoGravity(true);
            setVelocity(look.multiply(CHARGE_SPEED));
            return;
        }
        boolean flying = !isOnGround();
        if (!flying && player.forwardSpeed > 0 && player.getPitch() < -15.0F) {
            addVelocity(0.0, 0.6, 0.0);
            flying = true;
        }
        setNoGravity(flying);
        if (flying) {
            Vec3d desired = player.forwardSpeed > 0 ? look.multiply(FLY_SPEED * player.forwardSpeed) : Vec3d.ZERO;
            setVelocity(getVelocity().lerp(desired, 0.12));
        }
    }

    @Override
    protected Vec3d getControlledMovementInput(PlayerEntity player, Vec3d movementInput) {
        if (!isOnGround() || getCharge() > 0) return Vec3d.ZERO; // en el aire manda tickControlled
        float forward = player.forwardSpeed <= 0 ? player.forwardSpeed * 0.25F : player.forwardSpeed;
        return new Vec3d(player.sidewaysSpeed * 0.5F, 0.0, forward);
    }

    @Override
    protected float getSaddledSpeed(PlayerEntity player) {
        return (float) getAttributeValue(EntityAttributes.GENERIC_MOVEMENT_SPEED);
    }

    @Override
    public boolean handleFallDamage(float fallDistance, float damageMultiplier, DamageSource damageSource) {
        return false;
    }

    // ---------- servidor: embestida y desaparición ----------
    @Override
    public void tick() {
        super.tick();
        if (!(getWorld() instanceof ServerWorld world)) return;
        // La gravedad del servidor también se apaga en vuelo, para que no lo trate como montura "flotando"
        setNoGravity(hasPassengers() && (!isOnGround() || getCharge() > 0));

        if (getCharge() > 0) {
            dataTracker.set(CHARGE, getCharge() - 1);
            charge(world);
            return;
        }
        if (hasPassengers()) {
            riderless = 0;
        } else if (++riderless > RIDERLESS_TICKS) {
            world.spawnParticles(ParticleTypes.END_ROD, getX(), getBodyY(0.5), getZ(), 30, 0.8, 0.8, 0.8, 0.05);
            discard();
        }
    }

    private void charge(ServerWorld world) {
        Entity rider = getFirstPassenger();
        Box area = getBoundingBox().expand(2.5);
        DamageSource source = world.getDamageSources().create(FateUBW.BELLEROPHON_DAMAGE, this, rider);
        for (LivingEntity target : world.getEntitiesByClass(LivingEntity.class, area,
                e -> e != this && e != rider && e.isAlive() && !chargeHits.contains(e.getId()))) {
            chargeHits.add(target.getId());
            target.damage(source, CHARGE_DAMAGE);
            Vec3d push = getVelocity().lengthSquared() > 1.0E-4 ? getVelocity().normalize().multiply(2.5) : Vec3d.ZERO;
            target.addVelocity(push.x, 0.8, push.z);
            target.velocityModified = true;
            world.spawnParticles(ParticleTypes.FLASH, target.getX(), target.getBodyY(0.5), target.getZ(), 1, 0.0, 0.0, 0.0, 0.0);
        }
        world.spawnParticles(ParticleTypes.END_ROD, getX(), getBodyY(0.5), getZ(), 12, 0.8, 0.8, 0.8, 0.05);
        if (getCharge() % 4 == 0) {
            world.playSound(null, getX(), getY(), getZ(), SoundEvents.ENTITY_ENDER_DRAGON_FLAP, SoundCategory.PLAYERS, 1.0F, 1.6F);
        }
    }

    // ---------- GeckoLib ----------
    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new AnimationController<>(this, 5, state -> {
            if (!isOnGround()) return state.setAndContinue(FLY);
            return state.setAndContinue(state.isMoving() ? WALK : IDLE);
        }));
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return cache;
    }
}
