package com.nuwuman.fateubw.rider;

import com.nuwuman.fateubw.FateUBW;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.data.DataTracker;
import net.minecraft.entity.data.TrackedData;
import net.minecraft.entity.data.TrackedDataHandlerRegistry;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.projectile.ProjectileEntity;
import net.minecraft.entity.projectile.ProjectileUtil;
import net.minecraft.item.ItemStack;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

/**
 * Daga lanzada unida por una cadena a su dueño. Contra un ser vivo: lo hiere y lo atrae.
 * Contra un bloque: se clava y tira del dueño hacia ella. Solo se mueve en el servidor.
 */
public class ChainDaggerEntity extends ProjectileEntity {
    private static final TrackedData<ItemStack> STACK = DataTracker.registerData(ChainDaggerEntity.class, TrackedDataHandlerRegistry.ITEM_STACK);
    private static final double SPEED = 2.0;
    private static final double MAX_RANGE = 24.0;
    private static final float DAMAGE = 6.0F;
    // Cuánto se queda visible la cadena tras enganchar o clavarse
    private static final int HOOK_LINGER = 10;
    private static final int GRAPPLE_LINGER = 15;

    private int linger; // > 0: ya ha enganchado y desaparece al llegar a 0

    public ChainDaggerEntity(EntityType<? extends ChainDaggerEntity> type, World world) {
        super(type, world);
    }

    public static void toss(ServerWorld world, PlayerEntity owner, ItemStack stack) {
        ChainDaggerEntity dagger = new ChainDaggerEntity(FateUBW.CHAIN_DAGGER, world);
        dagger.setOwner(owner);
        dagger.dataTracker.set(STACK, stack.copyWithCount(1));
        Vec3d dir = owner.getRotationVec(1.0F);
        dagger.setPosition(owner.getEyePos().add(0.0, -0.2, 0.0).add(dir.multiply(0.8)));
        dagger.setVelocity(dir.multiply(SPEED));
        dagger.face(dir);
        world.spawnEntity(dagger);
    }

    public ItemStack getStack() {
        return dataTracker.get(STACK);
    }

    @Override
    protected void initDataTracker(DataTracker.Builder builder) {
        builder.add(STACK, ItemStack.EMPTY);
    }

    private void face(Vec3d v) {
        setYaw((float) Math.toDegrees(Math.atan2(v.x, v.z)));
        setPitch((float) Math.toDegrees(Math.atan2(v.y, v.horizontalLength())));
    }

    @Override
    public boolean shouldRender(double distance) {
        return true;
    }

    @Override
    public void tick() {
        super.tick();
        if (!(getWorld() instanceof ServerWorld world)) return;
        Entity owner = getOwner();
        if (owner == null || !owner.isAlive()) {
            discard();
            return;
        }
        if (linger > 0) {
            if (--linger == 0) discard();
            return;
        }
        if (distanceTo(owner) > MAX_RANGE) {
            discard();
            return;
        }

        HitResult hit = ProjectileUtil.getCollision(this, this::canHit);
        if (hit.getType() == HitResult.Type.ENTITY && ((EntityHitResult) hit).getEntity() instanceof LivingEntity target) {
            hook(world, owner, target);
            return;
        }
        if (hit.getType() == HitResult.Type.BLOCK) {
            grapple(world, owner, hit.getPos());
            return;
        }
        setPosition(getPos().add(getVelocity()));
    }

    // Lo hiere y tira de él hacia el dueño
    private void hook(ServerWorld world, Entity owner, LivingEntity target) {
        target.damage(getDamageSources().thrown(this, owner), DAMAGE);
        if (com.nuwuman.fateubw.Rules.canAffect(owner, target)) {
            Vec3d pull = owner.getPos().subtract(target.getPos()).normalize().multiply(1.6);
            target.setVelocity(pull.x, 0.4, pull.z);
            target.velocityModified = true;
        }
        world.playSound(null, target.getX(), target.getY(), target.getZ(), SoundEvents.ITEM_TRIDENT_HIT, SoundCategory.PLAYERS, 1.0F, 1.0F);
        world.spawnParticles(ParticleTypes.CRIT, target.getX(), target.getBodyY(0.5), target.getZ(), 10, 0.3, 0.3, 0.3, 0.2);
        setPosition(target.getBoundingBox().getCenter());
        setVelocity(Vec3d.ZERO);
        linger = HOOK_LINGER;
    }

    // Se clava y lanza al dueño hacia ese punto
    private void grapple(ServerWorld world, Entity owner, Vec3d point) {
        setPosition(point);
        setVelocity(Vec3d.ZERO);
        linger = GRAPPLE_LINGER;
        Vec3d to = point.subtract(owner.getPos());
        Vec3d pull = to.normalize().multiply(Math.min(to.length() * 0.25, 2.2));
        owner.setVelocity(pull.x, pull.y + 0.35, pull.z);
        owner.velocityModified = true;
        owner.fallDistance = 0.0F;
        world.playSound(null, point.x, point.y, point.z, SoundEvents.BLOCK_CHAIN_HIT, SoundCategory.PLAYERS, 1.2F, 0.8F);
    }
}
