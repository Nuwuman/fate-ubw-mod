package com.nuwuman.fateubw.archer;

import com.nuwuman.fateubw.FateUBW;
import com.nuwuman.fateubw.Rules;
import com.nuwuman.fateubw.lancer.GaeBolgItem;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.data.DataTracker;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.projectile.ProjectileEntity;
import net.minecraft.entity.projectile.ProjectileUtil;
import net.minecraft.particle.DustParticleEffect;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3f;

/**
 * Hrunting, el perro de caza rojo: una espada-flecha que persigue a su presa. Al alcanzarla estalla y, mientras la
 * presa siga viva, vuelve a lanzarse contra ella (hasta tres veces). Solo se mueve en el servidor.
 */
public class HruntingEntity extends ProjectileEntity {
    private static final double SPEED = 1.6;
    private static final int MAX_AGE = 200;
    private static final int MAX_STRIKES = 3;
    private static final float DAMAGE = 12.0F;
    private static final DustParticleEffect RED = new DustParticleEffect(new Vector3f(0.8F, 0.05F, 0.1F), 1.3F);

    @Nullable
    private LivingEntity target;
    private int strikes;

    public HruntingEntity(EntityType<? extends HruntingEntity> type, World world) {
        super(type, world);
        this.noClip = true;
    }

    /** La habilidad del conjunto de Archer: dispara Hrunting hacia lo que mira (o a quien mira, que perseguirá). */
    public static boolean fire(ServerPlayerEntity player) {
        ServerWorld world = player.getServerWorld();
        HruntingEntity hrunting = new HruntingEntity(FateUBW.HRUNTING_ENTITY, world);
        hrunting.setOwner(player);
        hrunting.target = GaeBolgItem.findTarget(world, player, 48.0, 0.9);
        Vec3d dir = player.getRotationVec(1.0F);
        hrunting.setPosition(player.getEyePos().add(dir.multiply(1.0)));
        hrunting.setVelocity(dir.multiply(SPEED));
        hrunting.face(dir);
        world.spawnEntity(hrunting);
        world.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.ENTITY_WITHER_SHOOT, SoundCategory.PLAYERS, 1.0F, 1.6F);
        com.nuwuman.fateubw.Voices.say(world, player, "caladbolg");
        return true;
    }

    private void face(Vec3d v) {
        setYaw((float) Math.toDegrees(Math.atan2(v.x, v.z)));
        setPitch((float) Math.toDegrees(Math.atan2(v.y, v.horizontalLength())));
    }

    @Override
    public void tick() {
        super.tick();
        if (!(getWorld() instanceof ServerWorld world)) return;
        Entity owner = getOwner();
        if (owner == null || age > MAX_AGE) {
            discard();
            return;
        }
        Vec3d vel = getVelocity();
        if (target != null && target.isAlive()) {
            Vec3d toTarget = target.getBoundingBox().getCenter().subtract(getPos()).normalize().multiply(SPEED);
            vel = vel.lerp(toTarget, 0.25).normalize().multiply(SPEED);
        }
        setVelocity(vel);
        face(vel);
        world.spawnParticles(RED, getX(), getY(), getZ(), 2, 0.05, 0.05, 0.05, 0.0);

        HitResult hit = ProjectileUtil.getCollision(this, this::canHit);
        if (hit.getType() != HitResult.Type.MISS) {
            strike(world, owner, hit);
            return;
        }
        setPosition(getPos().add(vel));
    }

    @Override
    protected boolean canHit(Entity entity) {
        return super.canHit(entity) && entity != getOwner() && Rules.canAffect(getOwner(), entity);
    }

    private void strike(ServerWorld world, Entity owner, HitResult hit) {
        Vec3d pos = hit.getPos();
        if (hit instanceof EntityHitResult entityHit && entityHit.getEntity() instanceof LivingEntity victim) {
            victim.damage(getDamageSources().thrown(this, owner), DAMAGE);
        }
        boolean griefing = FateUBW.breaksBlocks(world, pos);
        world.createExplosion(this, FateUBW.explosion(world, this, owner), null, pos.x, pos.y, pos.z, griefing ? 2.0F : 1.5F, false,
                griefing ? World.ExplosionSourceType.TNT : World.ExplosionSourceType.NONE);
        world.spawnParticles(RED, pos.x, pos.y, pos.z, 30, 0.6, 0.6, 0.6, 0.0);

        // El perro de caza vuelve a por su presa mientras siga viva
        if (target != null && target.isAlive() && ++strikes < MAX_STRIKES) {
            Vec3d above = target.getPos().add(world.random.nextGaussian() * 3, 6.0, world.random.nextGaussian() * 3);
            setPosition(above);
            setVelocity(target.getBoundingBox().getCenter().subtract(above).normalize().multiply(SPEED));
            world.playSound(null, above.x, above.y, above.z, SoundEvents.ENTITY_WOLF_GROWL, SoundCategory.PLAYERS, 1.0F, 0.6F);
            return;
        }
        discard();
    }

    @Override
    protected void initDataTracker(DataTracker.Builder builder) {
    }
}
