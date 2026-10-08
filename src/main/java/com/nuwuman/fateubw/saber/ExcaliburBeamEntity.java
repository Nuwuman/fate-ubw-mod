package com.nuwuman.fateubw.saber;

import com.nuwuman.fateubw.FateUBW;
import net.minecraft.block.BlockState;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.damage.DamageType;
import net.minecraft.entity.data.DataTracker;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.registry.RegistryKey;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

import java.util.HashSet;
import java.util.Set;

/**
 * El haz de luz de Excalibur: crece hacia delante, daña lo que toca y se desvanece.
 * Es también la base de otros Noble Phantasm en línea recta (Enuma Elish), que cambian
 * longitud, radio, daño y efectos sobrescribiendo los métodos protegidos.
 */
public class ExcaliburBeamEntity extends Entity {
    public static final int GROW_TICKS = 8;
    public static final int DAMAGE_TICKS = 24;
    public static final int LIFETIME = 36;

    // Los primeros bloques del haz no se rompen, para no cavar bajo los pies del que dispara
    private static final float CARVE_START = 2.0F;
    // Obsidiana y más duros resisten el haz
    private static final float MAX_BLAST_RESISTANCE = 1200.0F;

    @Nullable
    protected Entity owner;
    protected final Set<Integer> hit = new HashSet<>();
    private float carved = CARVE_START;

    public ExcaliburBeamEntity(EntityType<? extends ExcaliburBeamEntity> type, World world) {
        super(type, world);
        this.noClip = true;
    }

    public static void fire(ServerWorld world, PlayerEntity owner) {
        new ExcaliburBeamEntity(FateUBW.BEAM, world).launch(world, owner);
    }

    protected void launch(ServerWorld world, PlayerEntity owner) {
        Vec3d dir = owner.getRotationVec(1.0F);
        Vec3d start = owner.getEyePos().add(0.0, -0.3, 0.0).add(dir.multiply(1.2));
        refreshPositionAndAngles(start.x, start.y, start.z, owner.getYaw(), owner.getPitch());
        this.owner = owner;
        world.spawnEntity(this);
    }

    public float maxLength() {
        return 48.0F;
    }

    public float radius() {
        return 2.5F;
    }

    protected float damage() {
        return 40.0F;
    }

    protected RegistryKey<DamageType> damageType() {
        return FateUBW.EXCALIBUR_DAMAGE;
    }

    /** Efecto extra sobre cada objetivo alcanzado (además del daño). */
    protected void onHit(ServerWorld world, LivingEntity target, Vec3d dir) {
        target.setOnFireFor(5);
        target.takeKnockback(1.5, -dir.x, -dir.z);
    }

    /** Efecto sobre lo que está cerca del haz pero aún no ha sido alcanzado. */
    protected void affectNearby(ServerWorld world, Vec3d start, Vec3d end) {
    }

    public float length(float age) {
        return maxLength() * Math.min(1.0F, age / GROW_TICKS);
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
        if (FateUBW.breaksBlocks(world, start)) carve(world, start, dir, len);
        affectNearby(world, start, end);

        float radius = radius();
        DamageSource source = world.getDamageSources().create(damageType(), this, owner);
        Box area = new Box(start, end).expand(radius + 1.0);
        for (LivingEntity target : world.getEntitiesByClass(LivingEntity.class, area,
                e -> e != owner && e.isAlive() && !hit.contains(e.getId()))) {
            Vec3d center = target.getBoundingBox().getCenter();
            if (distanceToSegment(center, start, end) > radius + target.getWidth() / 2) continue;
            hit.add(target.getId());
            target.damage(source, damage());
            onHit(world, target, dir);
            world.spawnParticles(ParticleTypes.EXPLOSION, center.x, center.y, center.z, 1, 0.0, 0.0, 0.0, 0.0);
        }
    }

    // Rompe (sin soltar objetos) una esfera del radio del haz en cada paso del tramo que avanzó este tick
    private void carve(ServerWorld world, Vec3d start, Vec3d dir, float len) {
        float radius = radius();
        int r = MathHelper.ceil(radius);
        for (float d = carved; d <= len; d += 1.0F) {
            Vec3d point = start.add(dir.multiply(d));
            BlockPos center = BlockPos.ofFloored(point);
            for (BlockPos pos : BlockPos.iterate(center.add(-r, -r, -r), center.add(r, r, r))) {
                if (Vec3d.ofCenter(pos).squaredDistanceTo(point) > radius * radius) continue;
                BlockState state = world.getBlockState(pos);
                if (state.isAir() || state.getHardness(world, pos) < 0
                        || state.getBlock().getBlastResistance() >= MAX_BLAST_RESISTANCE) continue;
                world.breakBlock(pos, false, this);
            }
        }
        carved = Math.max(carved, len);
    }

    /** Punto del segmento a-b más cercano a p. */
    protected static Vec3d closestOnSegment(Vec3d p, Vec3d a, Vec3d b) {
        Vec3d ab = b.subtract(a);
        double t = MathHelper.clamp(p.subtract(a).dotProduct(ab) / ab.lengthSquared(), 0.0, 1.0);
        return a.add(ab.multiply(t));
    }

    private static double distanceToSegment(Vec3d p, Vec3d a, Vec3d b) {
        return p.distanceTo(closestOnSegment(p, a, b));
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
