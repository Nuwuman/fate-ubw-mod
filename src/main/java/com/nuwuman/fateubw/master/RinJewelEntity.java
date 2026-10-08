package com.nuwuman.fateubw.master;

import com.nuwuman.fateubw.FateUBW;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.projectile.thrown.ThrownItemEntity;
import net.minecraft.item.Item;
import net.minecraft.particle.DustParticleEffect;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.hit.HitResult;
import net.minecraft.world.World;
import org.joml.Vector3f;

/** Una joya de Tohsaka lanzada: libera el maná que guarda en una explosión al chocar. */
public class RinJewelEntity extends ThrownItemEntity {
    private static final DustParticleEffect GEM_RED = new DustParticleEffect(new Vector3f(1.0F, 0.15F, 0.25F), 1.4F);

    public RinJewelEntity(EntityType<? extends RinJewelEntity> type, World world) {
        super(type, world);
    }

    public RinJewelEntity(World world, LivingEntity owner) {
        super(FateUBW.RIN_JEWEL_ENTITY, owner, world);
    }

    @Override
    protected Item getDefaultItem() {
        return FateUBW.RIN_JEWEL;
    }

    @Override
    protected void onCollision(HitResult hit) {
        super.onCollision(hit);
        if (!(getWorld() instanceof ServerWorld world)) return;
        boolean griefing = FateUBW.breaksBlocks(world, getPos());
        world.createExplosion(this, getX(), getY(), getZ(), griefing ? 2.5F : 2.0F,
                griefing ? World.ExplosionSourceType.TNT : World.ExplosionSourceType.NONE);
        world.spawnParticles(GEM_RED, getX(), getY(), getZ(), 40, 0.8, 0.8, 0.8, 0.0);
        world.spawnParticles(ParticleTypes.END_ROD, getX(), getY(), getZ(), 15, 0.4, 0.4, 0.4, 0.1);
        discard();
    }
}
