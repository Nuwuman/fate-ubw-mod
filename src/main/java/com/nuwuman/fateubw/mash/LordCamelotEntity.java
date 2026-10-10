package com.nuwuman.fateubw.mash;

import com.nuwuman.fateubw.FateUBW;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.data.DataTracker;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.projectile.ProjectileEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

/**
 * Lord Camelot: un muro de luz con la forma de las murallas de Camelot, delante de Mash y de cara hacia donde mira.
 * Durante 6 s para los proyectiles y los haces de Noble Phantasm que le llegan de frente, y protege a quien está detrás
 * (Resistencia II).
 */
public class LordCamelotEntity extends Entity {
    public static final int LIFETIME = 20 * 6;
    public static final float HALF_WIDTH = 3.5F, HEIGHT = 5.0F;
    private static final double DISTANCE = 2.0, PROTECT_RANGE = 7.0;
    @Nullable
    private Entity owner;

    public LordCamelotEntity(EntityType<? extends LordCamelotEntity> type, World world) {
        super(type, world);
        this.noClip = true;
    }

    /** Levanta el muro delante de {@code player}, de cara hacia donde mira (solo el giro horizontal). */
    public static void raise(ServerWorld world, PlayerEntity player) {
        LordCamelotEntity wall = new LordCamelotEntity(FateUBW.LORD_CAMELOT, world);
        Vec3d facing = Vec3d.fromPolar(0.0F, player.getYaw());
        Vec3d at = player.getPos().add(facing.multiply(DISTANCE));
        wall.refreshPositionAndAngles(at.x, player.getY(), at.z, player.getYaw(), 0.0F);
        wall.owner = player;
        world.spawnEntity(wall);
        world.spawnParticles(ParticleTypes.END_ROD, at.x, at.y + 2.5, at.z, 80, HALF_WIDTH * 0.7, 2.0, 0.2, 0.05);
        world.spawnParticles(ParticleTypes.FLASH, at.x, at.y + 2.5, at.z, 1, 0.0, 0.0, 0.0, 0.0);
        world.playSound(null, at.x, at.y, at.z, SoundEvents.BLOCK_BEACON_ACTIVATE, SoundCategory.PLAYERS, 2.0F, 0.7F);
        world.playSound(null, at.x, at.y, at.z, SoundEvents.ITEM_TRIDENT_THUNDER.value(), SoundCategory.PLAYERS, 1.0F, 1.6F);
    }

    @Nullable
    public Entity getOwner() {
        return owner;
    }

    /** Hacia dónde mira el muro (su cara de delante). */
    public Vec3d facing() {
        return Vec3d.fromPolar(0.0F, getYaw());
    }

    /** Cuánto se ha alzado (0-1) y cuánto le queda antes de desvanecerse, para el dibujo. */
    public float strength(float age) {
        return MathHelper.clamp(age / 6.0F, 0.0F, 1.0F) * MathHelper.clamp((LIFETIME - age) / 10.0F, 0.0F, 1.0F);
    }

    /**
     * Si un haz que sale de {@code start} hacia {@code dir} (largo {@code length}) choca con la cara de delante de algún
     * Lord Camelot que no sea de {@code beamOwner}, a qué distancia; si no, -1.
     */
    public static float blockDistance(World world, Vec3d start, Vec3d dir, float length, @Nullable Entity beamOwner) {
        float best = -1.0F;
        Box area = new Box(start, start.add(dir.multiply(length))).expand(HALF_WIDTH + 1.0);
        for (LordCamelotEntity wall : world.getEntitiesByClass(LordCamelotEntity.class, area, w -> w.isAlive())) {
            if (beamOwner != null && wall.owner == beamOwner) continue;
            Vec3d n = wall.facing();
            double towards = dir.dotProduct(n);
            if (towards > -0.05) continue;                              // solo lo que le llega de frente
            double t = wall.getPos().subtract(start).dotProduct(n) / towards;
            if (t < 0 || t > length) continue;
            Vec3d hit = start.add(dir.multiply(t));
            Vec3d side = new Vec3d(-n.z, 0.0, n.x);
            double across = hit.subtract(wall.getPos()).dotProduct(side), up = hit.y - wall.getY();
            if (Math.abs(across) > HALF_WIDTH + 1.0 || up < -1.0 || up > HEIGHT + 1.0) continue;
            if (best < 0 || t < best) best = (float) t;
        }
        return best;
    }

    @Override
    public void tick() {
        super.tick();
        if (!(getWorld() instanceof ServerWorld world)) return;
        if (age > LIFETIME) {
            discard();
            return;
        }
        Vec3d n = facing(), side = new Vec3d(-n.z, 0.0, n.x), center = getPos();
        Box box = new Box(center.add(side.multiply(HALF_WIDTH)).add(n.multiply(-1.5)), center.add(side.multiply(-HALF_WIDTH)).add(n.multiply(1.5)))
                .stretch(0.0, HEIGHT, 0.0);
        // Para los proyectiles que vienen de frente (los del dueño pasan)
        for (ProjectileEntity p : world.getEntitiesByClass(ProjectileEntity.class, box, e -> e.isAlive() && e.getOwner() != owner)) {
            if (p.getVelocity().dotProduct(n) >= 0) continue;
            world.spawnParticles(ParticleTypes.END_ROD, p.getX(), p.getY(), p.getZ(), 8, 0.2, 0.2, 0.2, 0.05);
            world.playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.ITEM_SHIELD_BLOCK, SoundCategory.PLAYERS, 1.0F, 1.3F);
            p.discard();
        }
        // Protege a los jugadores que tiene detrás
        if (age % 10 == 0) {
            for (PlayerEntity ally : world.getEntitiesByClass(PlayerEntity.class, getBoundingBox().expand(PROTECT_RANGE),
                    p -> p.isAlive() && p.getPos().subtract(center).dotProduct(n) < 0.5 && p.squaredDistanceTo(center) < PROTECT_RANGE * PROTECT_RANGE)) {
                ally.addStatusEffect(new StatusEffectInstance(StatusEffects.RESISTANCE, 25, 1, true, false));
            }
        }
        if (age % 4 == 0) {
            Vec3d p = center.add(side.multiply((random.nextFloat() * 2 - 1) * HALF_WIDTH)).add(0, random.nextFloat() * HEIGHT, 0);
            world.spawnParticles(ParticleTypes.END_ROD, p.x, p.y, p.z, 1, 0.0, 0.05, 0.0, 0.01);
        }
    }

    /** Un haz se estrella contra el muro: chispas en el punto de impacto. */
    public static void impact(ServerWorld world, Vec3d at) {
        world.spawnParticles(ParticleTypes.FLASH, at.x, at.y, at.z, 1, 0.0, 0.0, 0.0, 0.0);
        world.spawnParticles(ParticleTypes.END_ROD, at.x, at.y, at.z, 10, 0.8, 0.8, 0.8, 0.3);
        world.spawnParticles(ParticleTypes.ELECTRIC_SPARK, at.x, at.y, at.z, 8, 1.0, 1.0, 1.0, 0.4);
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
