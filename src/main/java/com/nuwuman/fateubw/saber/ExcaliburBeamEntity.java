package com.nuwuman.fateubw.saber;

import com.nuwuman.fateubw.FateUBW;
import net.minecraft.block.BlockState;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.data.DataTracker;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

import java.util.HashSet;
import java.util.Set;

/** El haz de luz del Noble Phantasm. Crece hacia delante, daña lo que toca y se desvanece. */
public class ExcaliburBeamEntity extends Entity {
    public static final int GROW_TICKS = 8;
    public static final int DAMAGE_TICKS = 24;
    public static final int LIFETIME = 36;
    public static final float MAX_LENGTH = 48.0F;
    public static final float RADIUS = 2.5F;
    private static final float DAMAGE = 40.0F;

    // Los primeros bloques del haz no se rompen, para no cavar bajo los pies del que dispara
    private static final float CARVE_START = 2.0F;
    // Obsidiana y más duros resisten el haz
    private static final float MAX_BLAST_RESISTANCE = 1200.0F;

    @Nullable
    private Entity owner;
    private final Set<Integer> hit = new HashSet<>();
    private float carved = CARVE_START;

    public ExcaliburBeamEntity(EntityType<? extends ExcaliburBeamEntity> type, World world) {
        super(type, world);
        this.noClip = true;
    }

    public static void fire(ServerWorld world, PlayerEntity owner) {
        ExcaliburBeamEntity beam = new ExcaliburBeamEntity(FateUBW.BEAM, world);
        Vec3d dir = owner.getRotationVec(1.0F);
        Vec3d start = owner.getEyePos().add(0.0, -0.3, 0.0).add(dir.multiply(1.2));
        beam.refreshPositionAndAngles(start.x, start.y, start.z, owner.getYaw(), owner.getPitch());
        beam.owner = owner;
        world.spawnEntity(beam);
    }

    public float length(float age) {
        return MAX_LENGTH * Math.min(1.0F, age / GROW_TICKS);
    }

    public float fade(float age) {
        return age < LIFETIME - 10 ? 1.0F : Math.max(0.0F, (LIFETIME - age) / 10.0F);
    }

    @Override
    public void tick() {
        super.tick();
        if (getWorld() instanceof ServerWorld world) {
            if (age <= DAMAGE_TICKS) sweep(world);
            if (age > LIFETIME) discard();
        }
    }

    private void sweep(ServerWorld world) {
        float len = length(age);
        if (len < 0.1F) return;
        Vec3d start = getPos();
        Vec3d dir = getRotationVector();
        Vec3d end = start.add(dir.multiply(len));

        // Frente del haz mientras avanza
        if (age <= GROW_TICKS) {
            world.spawnParticles(ParticleTypes.FLASH, end.x, end.y, end.z, 1, 0.0, 0.0, 0.0, 0.0);
            world.spawnParticles(ParticleTypes.END_ROD, end.x, end.y, end.z, 15, 1.0, 1.0, 1.0, 0.15);
        }
        if (FateUBW.breaksBlocks(world)) carve(world, start, dir, len);

        DamageSource source = world.getDamageSources().create(FateUBW.EXCALIBUR_DAMAGE, this, owner);
        Box area = new Box(start, end).expand(RADIUS + 1.0);
        for (LivingEntity target : world.getEntitiesByClass(LivingEntity.class, area,
                e -> e != owner && e.isAlive() && !hit.contains(e.getId()))) {
            Vec3d center = target.getBoundingBox().getCenter();
            if (distanceToSegment(center, start, end) > RADIUS + target.getWidth() / 2) continue;
            hit.add(target.getId());
            target.damage(source, DAMAGE);
            target.setOnFireFor(5);
            target.takeKnockback(1.5, -dir.x, -dir.z);
            world.spawnParticles(ParticleTypes.EXPLOSION, center.x, center.y, center.z, 1, 0.0, 0.0, 0.0, 0.0);
        }
    }

    // Rompe (sin soltar objetos) una esfera de radio RADIUS en cada paso del tramo que el haz avanzó este tick
    private void carve(ServerWorld world, Vec3d start, Vec3d dir, float len) {
        int r = MathHelper.ceil(RADIUS);
        for (float d = carved; d <= len; d += 1.0F) {
            Vec3d point = start.add(dir.multiply(d));
            BlockPos center = BlockPos.ofFloored(point);
            for (BlockPos pos : BlockPos.iterate(center.add(-r, -r, -r), center.add(r, r, r))) {
                if (Vec3d.ofCenter(pos).squaredDistanceTo(point) > RADIUS * RADIUS) continue;
                BlockState state = world.getBlockState(pos);
                if (state.isAir() || state.getHardness(world, pos) < 0
                        || state.getBlock().getBlastResistance() >= MAX_BLAST_RESISTANCE) continue;
                world.breakBlock(pos, false, this);
            }
        }
        carved = Math.max(carved, len);
    }

    private static double distanceToSegment(Vec3d p, Vec3d a, Vec3d b) {
        Vec3d ab = b.subtract(a);
        double t = MathHelper.clamp(p.subtract(a).dotProduct(ab) / ab.lengthSquared(), 0.0, 1.0);
        return p.distanceTo(a.add(ab.multiply(t)));
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
