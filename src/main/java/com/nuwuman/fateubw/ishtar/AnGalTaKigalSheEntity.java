package com.nuwuman.fateubw.ishtar;

import com.nuwuman.fateubw.FateUBW;
import com.nuwuman.fateubw.saber.ExcaliburBeamEntity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.damage.DamageType;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.registry.RegistryKey;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;
import net.minecraft.world.World;

/**
 * An Gal Ta Kigal Shè: Ishtar dispara Venus con Maanna. Un haz dorado más largo que Enuma Elish que, donde choca (o al
 * final de su recorrido), estalla como una estrella.
 */
public class AnGalTaKigalSheEntity extends ExcaliburBeamEntity {
    public AnGalTaKigalSheEntity(EntityType<? extends AnGalTaKigalSheEntity> type, World world) {
        super(type, world);
    }

    public static void fire(ServerWorld world, PlayerEntity owner) {
        new AnGalTaKigalSheEntity(FateUBW.AN_GAL_TA_KIGAL_SHE, world).launch(world, owner);
    }

    @Override
    public float maxLength() {
        return 72.0F;
    }

    @Override
    public float radius() {
        return 3.0F;
    }

    @Override
    protected float damage() {
        return 45.0F;
    }

    @Override
    protected RegistryKey<DamageType> damageType() {
        return FateUBW.AN_GAL_TA_KIGAL_SHE_DAMAGE;
    }

    @Override
    protected void onHit(ServerWorld world, LivingEntity target, Vec3d dir) {
        target.takeKnockback(2.0, -dir.x, -dir.z);
    }

    // Cuando el haz llega a su largo, Venus estalla en el primer bloque que encuentra o en la punta
    @Override
    protected void affectNearby(ServerWorld world, Vec3d start, Vec3d end) {
        if (age != GROW_TICKS) return;
        HitResult block = world.raycast(new RaycastContext(start, end, RaycastContext.ShapeType.COLLIDER, RaycastContext.FluidHandling.NONE, this));
        Vec3d at = block.getType() == HitResult.Type.MISS ? end : block.getPos();
        boolean griefing = FateUBW.breaksBlocks(world, at);
        world.createExplosion(this, at.x, at.y, at.z, griefing ? 7.0F : 5.0F,
                griefing ? World.ExplosionSourceType.TNT : World.ExplosionSourceType.NONE);
        world.spawnParticles(ParticleTypes.EXPLOSION_EMITTER, at.x, at.y, at.z, 3, 1.5, 1.5, 1.5, 0.0);
        world.spawnParticles(ParticleTypes.END_ROD, at.x, at.y, at.z, 80, 3.0, 3.0, 3.0, 0.3);
    }
}
