package com.nuwuman.fateubw.gilgamesh;

import com.nuwuman.fateubw.FateUBW;
import com.nuwuman.fateubw.lancer.GaeBolgItem;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.data.DataTracker;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

/**
 * Un portal dorado del Gate of Babylon. Flota detrás del dueño en una posición fija respecto a él
 * y dispara armas del tesoro hacia lo que el dueño está mirando.
 */
public class BabylonPortalEntity extends Entity {
    public static final int OPEN_TICKS = 6;
    public static final int LIFETIME = 60;
    private static final int PORTALS = 8;
    private static final int FIRE_INTERVAL = 8;
    private static final double RANGE = 64.0;
    private static final double WEAPON_SPEED = 3.0;

    @Nullable
    private PlayerEntity owner;
    private double offsetRight, offsetUp, offsetBack;
    private int firstShot;

    public BabylonPortalEntity(EntityType<? extends BabylonPortalEntity> type, World world) {
        super(type, world);
        this.noClip = true;
    }

    /** Abre un abanico de portales detrás del dueño, a distintas alturas, que disparan escalonados. */
    public static void openGate(ServerWorld world, PlayerEntity owner) {
        for (int i = 0; i < PORTALS; i++) {
            double spread = i / (PORTALS - 1.0) * 2.0 - 1.0; // -1..1 de izquierda a derecha
            BabylonPortalEntity portal = new BabylonPortalEntity(FateUBW.BABYLON_PORTAL, world);
            portal.owner = owner;
            portal.offsetRight = spread * 2.8;
            portal.offsetUp = 1.6 + (i % 3) * 0.9 + world.random.nextDouble() * 0.3;
            portal.offsetBack = 1.0 + Math.abs(spread) * 0.8;
            portal.firstShot = OPEN_TICKS + 2 + i * 2;
            portal.follow();
            world.spawnEntity(portal);
        }
        world.playSound(null, owner.getX(), owner.getY(), owner.getZ(), SoundEvents.BLOCK_END_PORTAL_SPAWN, SoundCategory.PLAYERS, 0.6F, 1.8F);
        world.playSound(null, owner.getX(), owner.getY(), owner.getZ(), SoundEvents.BLOCK_BEACON_ACTIVATE, SoundCategory.PLAYERS, 1.5F, 1.4F);
    }

    // Mantiene la posición relativa al dueño y mira hacia donde mira él
    private void follow() {
        if (owner == null) return;
        Vec3d forward = Vec3d.fromPolar(0.0F, owner.getYaw());
        Vec3d right = forward.crossProduct(new Vec3d(0.0, 1.0, 0.0));
        Vec3d pos = owner.getPos().add(right.multiply(offsetRight)).add(0.0, offsetUp, 0.0).subtract(forward.multiply(offsetBack));
        refreshPositionAndAngles(pos.x, pos.y, pos.z, owner.getYaw(), owner.getPitch());
    }

    public float scale(float age) {
        float open = Math.min(1.0F, age / OPEN_TICKS);
        float close = Math.min(1.0F, Math.max(0.0F, (LIFETIME - age) / 6.0F));
        return Math.min(open, close);
    }

    @Override
    public void tick() {
        super.tick();
        if (!(getWorld() instanceof ServerWorld world)) return;
        if (owner == null || !owner.isAlive() || age > LIFETIME) {
            world.spawnParticles(ParticleTypes.END_ROD, getX(), getY(), getZ(), 6, 0.3, 0.3, 0.3, 0.02);
            discard();
            return;
        }
        follow();
        if (age >= firstShot && age < LIFETIME - 6 && (age - firstShot) % FIRE_INTERVAL == 0) shoot(world);
    }

    private void shoot(ServerWorld world) {
        Vec3d target = aimPoint(world);
        Vec3d from = getPos();
        Vec3d dir = target.subtract(from).normalize();
        BabylonWeaponEntity weapon = new BabylonWeaponEntity(world, owner, randomTreasure(world));
        weapon.setPosition(from.add(dir.multiply(0.5)));
        weapon.setVelocity(dir.x, dir.y, dir.z, (float) WEAPON_SPEED, 0.5F);
        world.spawnEntity(weapon);
        world.spawnParticles(ParticleTypes.END_ROD, from.x, from.y, from.z, 4, 0.2, 0.2, 0.2, 0.05);
        world.playSound(null, from.x, from.y, from.z, SoundEvents.ENTITY_ARROW_SHOOT, SoundCategory.PLAYERS, 1.2F, 0.6F);
    }

    // El enemigo al que mira el dueño o, si no hay, el bloque al que apunta (o 64 bloques al frente)
    private Vec3d aimPoint(ServerWorld world) {
        LivingEntity target = GaeBolgItem.findTarget(world, owner, RANGE, 0.97);
        if (target != null) return target.getBoundingBox().getCenter();
        HitResult hit = owner.raycast(RANGE, 1.0F, false);
        return hit.getType() == HitResult.Type.MISS ? owner.getEyePos().add(owner.getRotationVec(1.0F).multiply(RANGE)) : hit.getPos();
    }

    // El tesoro guarda los prototipos de todos los Noble Phantasm... y armas corrientes de oro y diamante
    private static ItemStack randomTreasure(ServerWorld world) {
        Item[] treasure = {FateUBW.GAE_BOLG, FateUBW.KANSHOU, FateUBW.BAKUYA, FateUBW.CALADBOLG, FateUBW.EXCALIBUR,
                FateUBW.RIDER_DAGGER, Items.GOLDEN_SWORD, Items.DIAMOND_SWORD, Items.NETHERITE_SWORD, Items.GOLDEN_AXE, Items.NETHERITE_AXE};
        return new ItemStack(treasure[world.random.nextInt(treasure.length)]);
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
