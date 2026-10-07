package com.nuwuman.fateubw.archer;

import com.nuwuman.fateubw.FateUBW;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.data.DataTracker;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.projectile.ProjectileEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

/** Rho Aias: escudo de siete pétalos delante del dueño que destruye los proyectiles ajenos que lo cruzan. */
public class RhoAiasEntity extends Entity {
    public static final int LIFETIME = 120;
    public static final int GROW_TICKS = 6;
    public static final float RADIUS = 2.4F;

    @Nullable
    private PlayerEntity owner;

    public RhoAiasEntity(EntityType<? extends RhoAiasEntity> type, World world) {
        super(type, world);
        this.noClip = true;
    }

    public static void deploy(ServerWorld world, PlayerEntity owner) {
        RhoAiasEntity shield = new RhoAiasEntity(FateUBW.RHO_AIAS_ENTITY, world);
        shield.owner = owner;
        shield.follow(owner);
        world.spawnEntity(shield);
        world.playSound(null, owner.getX(), owner.getY(), owner.getZ(), SoundEvents.BLOCK_BEACON_ACTIVATE, SoundCategory.PLAYERS, 1.5F, 1.6F);
        world.playSound(null, owner.getX(), owner.getY(), owner.getZ(), SoundEvents.BLOCK_AMETHYST_BLOCK_CHIME, SoundCategory.PLAYERS, 2.0F, 0.8F);
    }

    private void follow(PlayerEntity player) {
        Vec3d pos = player.getEyePos().add(player.getRotationVec(1.0F).multiply(1.8)).add(0.0, -0.4, 0.0);
        refreshPositionAndAngles(pos.x, pos.y, pos.z, player.getYaw(), player.getPitch());
    }

    public float scale(float age) {
        return Math.min(1.0F, age / GROW_TICKS);
    }

    public float fade(float age) {
        return age < LIFETIME - 10 ? 1.0F : Math.max(0.0F, (LIFETIME - age) / 10.0F);
    }

    @Override
    public void tick() {
        super.tick();
        if (!(getWorld() instanceof ServerWorld world)) return;

        if (owner == null || !owner.isAlive() || age > LIFETIME) {
            world.spawnParticles(ParticleTypes.CHERRY_LEAVES, getX(), getY(), getZ(), 30, 1.2, 1.2, 1.2, 0.0);
            world.playSound(null, getX(), getY(), getZ(), SoundEvents.BLOCK_AMETHYST_CLUSTER_BREAK, SoundCategory.PLAYERS, 1.5F, 1.0F);
            discard();
            return;
        }
        follow(owner);

        Vec3d normal = getRotationVector();
        for (ProjectileEntity p : world.getEntitiesByClass(ProjectileEntity.class, getBoundingBox().expand(RADIUS + 2.0),
                p -> p.getOwner() != owner)) {
            Vec3d rel = p.getPos().subtract(getPos());
            double depth = rel.dotProduct(normal);
            if (Math.abs(depth) > 1.5 || rel.subtract(normal.multiply(depth)).length() > RADIUS) continue;
            world.spawnParticles(ParticleTypes.FLASH, p.getX(), p.getY(), p.getZ(), 1, 0.0, 0.0, 0.0, 0.0);
            world.spawnParticles(ParticleTypes.CHERRY_LEAVES, p.getX(), p.getY(), p.getZ(), 8, 0.4, 0.4, 0.4, 0.0);
            world.playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.BLOCK_AMETHYST_BLOCK_HIT, SoundCategory.PLAYERS, 1.5F, 0.8F);
            p.discard();
        }
    }

    @Override
    public boolean shouldRender(double distance) {
        return true;
    }

    @Override
    protected void initDataTracker(DataTracker.Builder builder) {
    }

    @Override
    protected void readCustomDataFromNbt(NbtCompound nbt) {
    }

    @Override
    protected void writeCustomDataToNbt(NbtCompound nbt) {
    }
}
