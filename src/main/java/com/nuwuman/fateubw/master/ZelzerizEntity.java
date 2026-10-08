package com.nuwuman.fateubw.master;

import com.nuwuman.fateubw.FateUBW;
import com.nuwuman.fateubw.Rules;
import com.nuwuman.fateubw.gilgamesh.BabylonWeaponEntity;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.data.DataTracker;
import net.minecraft.entity.mob.Monster;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

import java.util.Comparator;

/**
 * Zelzeriz, un pájaro de alambre de plata de Illya. Vuela en círculo alrededor de su dueña y lanza agujas de alambre
 * contra los monstruos cercanos (o contra quien la ataque). Desaparece a los 15 s.
 */
public class ZelzerizEntity extends Entity {
    private static final int LIFETIME = 20 * 15;
    private static final int FIRE_INTERVAL = 20;
    private static final double RANGE = 16.0;

    @Nullable
    private PlayerEntity owner;
    private float phase;

    public ZelzerizEntity(EntityType<? extends ZelzerizEntity> type, World world) {
        super(type, world);
        this.noClip = true;
    }

    public static void summon(ServerWorld world, PlayerEntity owner, int count) {
        for (int i = 0; i < count; i++) {
            ZelzerizEntity bird = new ZelzerizEntity(FateUBW.ZELZERIZ_ENTITY, world);
            bird.owner = owner;
            bird.phase = (float) (i * Math.PI * 2 / count);
            bird.age = i * 7; // que no disparen todos a la vez
            bird.follow();
            world.spawnEntity(bird);
        }
        world.playSound(null, owner.getX(), owner.getY(), owner.getZ(), SoundEvents.ENTITY_ALLAY_AMBIENT_WITH_ITEM, SoundCategory.PLAYERS, 1.0F, 1.4F);
    }

    // Da vueltas sobre la cabeza de la dueña, mirando hacia donde vuela
    private void follow() {
        if (owner == null) return;
        double angle = phase + age * 0.08;
        Vec3d pos = owner.getPos().add(Math.cos(angle) * 1.8, 2.3 + Math.sin(age * 0.15 + phase) * 0.3, Math.sin(angle) * 1.8);
        Vec3d tangent = new Vec3d(-Math.sin(angle), 0.0, Math.cos(angle));
        refreshPositionAndAngles(pos.x, pos.y, pos.z, (float) Math.toDegrees(Math.atan2(tangent.x, tangent.z)), 0.0F);
    }

    @Override
    public void tick() {
        super.tick();
        if (!(getWorld() instanceof ServerWorld world)) return;
        if (owner == null || !owner.isAlive() || age > LIFETIME) {
            world.spawnParticles(ParticleTypes.END_ROD, getX(), getY(), getZ(), 6, 0.2, 0.2, 0.2, 0.02);
            discard();
            return;
        }
        follow();
        if (age % FIRE_INTERVAL != 0) return;
        LivingEntity target = world.getEntitiesByClass(LivingEntity.class, owner.getBoundingBox().expand(RANGE),
                        e -> e != owner && e.isAlive() && (e instanceof Monster || e instanceof MobEntity mob && mob.getTarget() == owner)
                                && Rules.canAffect(owner, e) && owner.canSee(e))
                .stream().min(Comparator.comparingDouble(e -> e.squaredDistanceTo(owner))).orElse(null);
        if (target == null) return;
        Vec3d from = getPos();
        Vec3d dir = target.getBoundingBox().getCenter().subtract(from).normalize();
        BabylonWeaponEntity needle = new BabylonWeaponEntity(world, owner, new ItemStack(FateUBW.ZELZERIZ));
        needle.setPosition(from);
        needle.setVelocity(dir.x, dir.y, dir.z, 2.2F, 0.5F);
        world.spawnEntity(needle);
        world.playSound(null, from.x, from.y, from.z, SoundEvents.BLOCK_CHAIN_STEP, SoundCategory.PLAYERS, 1.0F, 1.8F);
    }

    @Override
    public boolean shouldRender(double distance) {
        return distance < 64 * 64;
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
