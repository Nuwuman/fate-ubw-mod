package com.nuwuman.fateubw.gilgamesh;

import com.nuwuman.fateubw.FateUBW;
import com.nuwuman.fateubw.saber.ExcaliburBeamEntity;
import net.minecraft.block.BlockState;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.FallingBlockEntity;
import net.minecraft.entity.data.DataTracker;
import net.minecraft.entity.data.TrackedData;
import net.minecraft.entity.data.TrackedDataHandlerRegistry;
import net.minecraft.util.math.BlockPos;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.damage.DamageType;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.registry.RegistryKey;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

/** Enuma Elish: un vórtice en espiral, más largo y ancho que Excalibur, que además arrastra hacia su eje lo que pasa cerca. */
public class EnumaElishEntity extends ExcaliburBeamEntity {
    private static final double PULL_RANGE = 5.0;

    public EnumaElishEntity(EntityType<? extends EnumaElishEntity> type, World world) {
        super(type, world);
    }

    // World Severance: el tajo es un 40 % más ancho y los bloques que arranca salen volando como escombros
    private static final TrackedData<Boolean> SEVERANCE = DataTracker.registerData(EnumaElishEntity.class, TrackedDataHandlerRegistry.BOOLEAN);

    public static void fire(ServerWorld world, PlayerEntity owner, boolean severance) {
        EnumaElishEntity entity = new EnumaElishEntity(FateUBW.ENUMA_ELISH, world);
        entity.dataTracker.set(SEVERANCE, severance);
        entity.launch(world, owner);
    }

    @Override
    protected void initDataTracker(DataTracker.Builder builder) {
        super.initDataTracker(builder);
        builder.add(SEVERANCE, false);
    }

    // Las explosiones que recorren el vórtice: humo, fuego y polvo rojo
    @Override
    protected void burst(ServerWorld world, Vec3d at, double spread) {
        world.spawnParticles(net.minecraft.particle.ParticleTypes.EXPLOSION, at.x, at.y, at.z, 2, spread, spread, spread, 0.0);
        world.spawnParticles(net.minecraft.particle.ParticleTypes.LARGE_SMOKE, at.x, at.y, at.z, 6, spread, spread, spread, 0.08);
        world.spawnParticles(net.minecraft.particle.ParticleTypes.CRIMSON_SPORE, at.x, at.y, at.z, 15, spread, spread, spread, 0.2);
    }

    // Uno de cada tres bloques sale despedido como bloque que cae; el resto se rompe
    @Override
    protected void breakCarved(ServerWorld world, BlockPos pos, BlockState state) {
        if (!dataTracker.get(SEVERANCE) || world.random.nextInt(3) != 0) {
            super.breakCarved(world, pos, state);
            return;
        }
        FallingBlockEntity debris = FallingBlockEntity.spawnFromBlock(world, pos, state);
        debris.setVelocity((world.random.nextDouble() - 0.5) * 0.8, 0.5 + world.random.nextDouble() * 0.5, (world.random.nextDouble() - 0.5) * 0.8);
        debris.velocityModified = true;
        debris.dropItem = false;
    }

    @Override
    public float maxLength() {
        return 64.0F;
    }

    @Override
    public float radius() {
        return dataTracker.get(SEVERANCE) ? 3.5F * 1.4F : 3.5F;
    }

    @Override
    protected float damage() {
        return 50.0F;
    }

    @Override
    protected RegistryKey<DamageType> damageType() {
        return FateUBW.ENUMA_ELISH_DAMAGE;
    }

    // La espiral los lanza hacia delante y hacia arriba, desgarrados
    @Override
    protected void onHit(ServerWorld world, LivingEntity target, Vec3d dir) {
        target.setVelocity(dir.x * 1.5, 0.8, dir.z * 1.5);
        target.velocityModified = true;
    }

    // Lo que está cerca del vórtice es arrastrado hacia su eje
    @Override
    protected void affectNearby(ServerWorld world, Vec3d start, Vec3d end) {
        Box area = new Box(start, end).expand(radius() + PULL_RANGE);
        for (LivingEntity e : world.getEntitiesByClass(LivingEntity.class, area,
                e -> e != owner && e.isAlive() && !hit.contains(e.getId()) && sameSide(world, start, e) && com.nuwuman.fateubw.Rules.canAffect(owner, e))) {
            Vec3d center = e.getBoundingBox().getCenter();
            Vec3d toAxis = closestOnSegment(center, start, end).subtract(center);
            if (toAxis.length() > radius() + PULL_RANGE) continue;
            Vec3d pull = toAxis.normalize().multiply(0.45);
            e.addVelocity(pull.x, pull.y + 0.05, pull.z);
            e.velocityModified = true;
        }
    }
}
