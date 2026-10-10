package com.nuwuman.fateubw.lancer;

import com.nuwuman.fateubw.FateUBW;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.data.DataTracker;
import net.minecraft.entity.data.TrackedData;
import net.minecraft.entity.data.TrackedDataHandlerRegistry;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.projectile.ProjectileEntity;
import net.minecraft.entity.projectile.ProjectileUtil;
import net.minecraft.item.ItemStack;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

/**
 * La lanza que vuela con la muerte: persigue al objetivo y al impactar estalla en un área
 * donde caen treinta lanzas de luz roja. Solo se mueve en el servidor; el cliente sigue su posición.
 */
public class GaeBolgSpearEntity extends ProjectileEntity {
    private static final TrackedData<ItemStack> STACK = DataTracker.registerData(GaeBolgSpearEntity.class, TrackedDataHandlerRegistry.ITEM_STACK);
    private static final double SPEED = 1.8;
    private static final int MAX_AGE = 100;
    private static final float BLAST_RADIUS = 6.0F;
    private static final float BLAST_DAMAGE = 30.0F;

    @Nullable
    private LivingEntity target;

    public GaeBolgSpearEntity(EntityType<? extends GaeBolgSpearEntity> type, World world) {
        super(type, world);
    }

    public static void launch(ServerWorld world, PlayerEntity owner, ItemStack stack, @Nullable LivingEntity target) {
        GaeBolgSpearEntity spear = new GaeBolgSpearEntity(FateUBW.GAE_BOLG_SPEAR, world);
        spear.setOwner(owner);
        spear.dataTracker.set(STACK, stack.copyWithCount(1));
        spear.target = target;
        Vec3d dir = owner.getRotationVec(1.0F);
        spear.setPosition(owner.getEyePos().add(dir.multiply(1.0)));
        spear.setVelocity(dir.multiply(SPEED));
        spear.face(spear.getVelocity());
        world.spawnEntity(spear);
    }

    public ItemStack getStack() {
        return dataTracker.get(STACK);
    }

    @Override
    protected void initDataTracker(DataTracker.Builder builder) {
        builder.add(STACK, ItemStack.EMPTY);
    }

    // Misma convención que las flechas, para que el renderer la oriente según su vuelo
    private void face(Vec3d v) {
        setYaw((float) Math.toDegrees(Math.atan2(v.x, v.z)));
        setPitch((float) Math.toDegrees(Math.atan2(v.y, v.horizontalLength())));
    }

    @Override
    public void tick() {
        super.tick();
        if (!(getWorld() instanceof ServerWorld world)) {
            getWorld().addParticle(GaeBolgItem.CRIMSON, getX(), getY(), getZ(), 0.0, 0.0, 0.0);
            getWorld().addParticle(ParticleTypes.CRIMSON_SPORE, getX(), getY(), getZ(), 0.0, 0.0, 0.0);
            return;
        }
        if (age > MAX_AGE) {
            impact(world, getPos());
            return;
        }

        Vec3d vel = getVelocity();
        if (target != null && target.isAlive()) {
            Vec3d toTarget = target.getBoundingBox().getCenter().subtract(getPos()).normalize().multiply(SPEED);
            vel = vel.lerp(toTarget, 0.2).normalize().multiply(SPEED);
        }
        setVelocity(vel);
        face(vel);

        HitResult hit = ProjectileUtil.getCollision(this, this::canHit);
        if (hit.getType() != HitResult.Type.MISS) {
            impact(world, hit.getPos());
            return;
        }
        setPosition(getPos().add(vel));
    }

    private void impact(ServerWorld world, Vec3d pos) {
        Entity owner = getOwner();
        if (FateUBW.breaksBlocks(world, pos)) {
            world.createExplosion(this, FateUBW.explosion(world, this, getOwner()), null, pos.x, pos.y, pos.z, 6.0F, false, World.ExplosionSourceType.TNT);
        }

        DamageSource source = world.getDamageSources().create(FateUBW.GAE_BOLG_DAMAGE, this, owner);
        Box area = Box.of(pos, BLAST_RADIUS * 2, BLAST_RADIUS * 2, BLAST_RADIUS * 2);
        for (LivingEntity e : world.getEntitiesByClass(LivingEntity.class, area, e -> e != owner && e.isAlive() && com.nuwuman.fateubw.Rules.canAffect(owner, e))) {
            double distance = e.getBoundingBox().getCenter().distanceTo(pos);
            if (distance > BLAST_RADIUS) continue;
            e.damage(source, BLAST_DAMAGE * (float) (1.0 - 0.5 * distance / BLAST_RADIUS));
            e.addStatusEffect(new StatusEffectInstance(StatusEffects.WITHER, 160, 1));
        }

        // Treinta lanzas de luz roja que caen sobre la zona
        for (int i = 0; i < 30; i++) {
            double angle = world.random.nextDouble() * Math.PI * 2;
            double r = Math.sqrt(world.random.nextDouble()) * BLAST_RADIUS;
            double x = pos.x + Math.cos(angle) * r, z = pos.z + Math.sin(angle) * r;
            for (int k = 0; k < 8; k++) {
                world.spawnParticles(GaeBolgItem.CRIMSON, x, pos.y + k * 0.6, z, 1, 0.0, 0.0, 0.0, 0.0);
            }
        }
        world.spawnParticles(ParticleTypes.FLASH, pos.x, pos.y, pos.z, 1, 0.0, 0.0, 0.0, 0.0);
        world.spawnParticles(ParticleTypes.EXPLOSION_EMITTER, pos.x, pos.y, pos.z, 1, 0.0, 0.0, 0.0, 0.0);
        world.spawnParticles(ParticleTypes.CRIMSON_SPORE, pos.x, pos.y + 1.0, pos.z, 120, 3.0, 2.0, 3.0, 0.2);
        world.playSound(null, pos.x, pos.y, pos.z, SoundEvents.ENTITY_LIGHTNING_BOLT_IMPACT, SoundCategory.PLAYERS, 3.0F, 0.7F);
        world.playSound(null, pos.x, pos.y, pos.z, SoundEvents.ENTITY_GENERIC_EXPLODE.value(), SoundCategory.PLAYERS, 2.0F, 0.8F);
        discard();
    }
}
