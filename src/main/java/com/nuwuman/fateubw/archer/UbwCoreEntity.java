package com.nuwuman.fateubw.archer;

import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.data.DataTracker;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.world.World;

/** Centro de un Marble activo: su renderer pinta los engranajes del cielo; en el servidor suelta ceniza. */
public class UbwCoreEntity extends Entity {
    public UbwCoreEntity(EntityType<? extends UbwCoreEntity> type, World world) {
        super(type, world);
        this.noClip = true;
    }

    @Override
    public void tick() {
        super.tick();
        if (!(getWorld() instanceof ServerWorld world)) return;
        // Respaldo por si su sesión se pierde: nunca dura más que un Marble
        if (age > UnlimitedBladeWorks.DURATION + 100) {
            discard();
            return;
        }
        if (age % 4 == 0) world.spawnParticles(ParticleTypes.WHITE_ASH, getX(), getY() + 4.0, getZ(), 30, 14.0, 4.0, 14.0, 0.01);
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
