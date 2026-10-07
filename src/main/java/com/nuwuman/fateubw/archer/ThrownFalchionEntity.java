package com.nuwuman.fateubw.archer;

import com.nuwuman.fateubw.FateUBW;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.data.DataTracker;
import net.minecraft.entity.data.TrackedData;
import net.minecraft.entity.data.TrackedDataHandlerRegistry;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.projectile.ProjectileEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

import java.util.HashSet;
import java.util.Set;

/**
 * Sable lanzado: sale abierto hacia un lado, se cierra en arco y vuelve al dueño (puede golpear a la ida y a la vuelta).
 * Atraviesa bloques; solo se mueve en el servidor y el cliente sigue su posición.
 */
public class ThrownFalchionEntity extends ProjectileEntity {
    private static final TrackedData<ItemStack> STACK = DataTracker.registerData(ThrownFalchionEntity.class, TrackedDataHandlerRegistry.ITEM_STACK);
    private static final int OUTBOUND_TICKS = 12;
    private static final int MAX_AGE = 80;
    private static final double SPEED = 1.4;
    private static final float DAMAGE = 9.0F;

    private Vec3d side = Vec3d.ZERO;
    private float curve;
    private boolean returning;
    private final Set<Integer> hit = new HashSet<>();

    public ThrownFalchionEntity(EntityType<? extends ThrownFalchionEntity> type, World world) {
        super(type, world);
        this.noClip = true;
    }

    /** curve: -1 abre hacia la izquierda (Kanshō), +1 hacia la derecha (Bakuya). */
    public static void toss(ServerWorld world, PlayerEntity owner, ItemStack stack, float curve) {
        ThrownFalchionEntity e = new ThrownFalchionEntity(FateUBW.THROWN_FALCHION, world);
        e.setOwner(owner);
        e.dataTracker.set(STACK, stack.copyWithCount(1));
        Vec3d dir = owner.getRotationVec(1.0F);
        Vec3d right = dir.crossProduct(new Vec3d(0.0, 1.0, 0.0));
        e.side = right.lengthSquared() < 1.0E-4 ? new Vec3d(1.0, 0.0, 0.0) : right.normalize();
        e.curve = curve;
        e.setPosition(owner.getEyePos().add(0.0, -0.3, 0.0).add(dir.multiply(0.6)).add(e.side.multiply(curve * 0.4)));
        e.setVelocity(dir.multiply(SPEED).add(e.side.multiply(curve * 0.55)));
        world.spawnEntity(e);
    }

    public ItemStack getStack() {
        return dataTracker.get(STACK);
    }

    @Override
    protected void initDataTracker(DataTracker.Builder builder) {
        builder.add(STACK, ItemStack.EMPTY);
    }

    @Override
    public void tick() {
        super.tick();
        if (!(getWorld() instanceof ServerWorld world)) return;

        Entity owner = getOwner();
        if (owner == null || !owner.isAlive() || age > MAX_AGE) {
            world.spawnParticles(ParticleTypes.END_ROD, getX(), getY(), getZ(), 10, 0.2, 0.2, 0.2, 0.05);
            discard();
            return;
        }

        Vec3d vel = getVelocity();
        if (age < OUTBOUND_TICKS) {
            vel = vel.subtract(side.multiply(curve * 0.09)); // se cierra hacia el centro
        } else {
            if (!returning) {
                returning = true;
                hit.clear(); // puede volver a golpear en el regreso
            }
            Vec3d toOwner = owner.getEyePos().add(0.0, -0.4, 0.0).subtract(getPos());
            if (toOwner.length() < 1.5) {
                discard();
                return;
            }
            vel = vel.lerp(toOwner.normalize().multiply(SPEED * 1.25), 0.3);
        }

        Box sweep = getBoundingBox().stretch(vel).expand(0.5);
        for (LivingEntity target : world.getEntitiesByClass(LivingEntity.class, sweep,
                e -> e != owner && e.isAlive() && !hit.contains(e.getId()))) {
            hit.add(target.getId());
            target.damage(getDamageSources().thrown(this, owner), DAMAGE);
            world.spawnParticles(ParticleTypes.CRIT, target.getX(), target.getBodyY(0.5), target.getZ(), 8, 0.3, 0.3, 0.3, 0.2);
            world.playSound(null, target.getX(), target.getY(), target.getZ(),
                    SoundEvents.ENTITY_PLAYER_ATTACK_CRIT, SoundCategory.PLAYERS, 1.0F, 1.2F);
        }
        if (age % 5 == 0) {
            world.playSound(null, getX(), getY(), getZ(), SoundEvents.ENTITY_PLAYER_ATTACK_SWEEP, SoundCategory.PLAYERS, 0.4F, 1.8F);
        }

        setVelocity(vel);
        setPosition(getPos().add(vel));
    }
}
