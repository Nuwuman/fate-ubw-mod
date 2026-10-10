package com.nuwuman.fateubw.archer;

import com.nuwuman.fateubw.FateUBW;
import com.nuwuman.fateubw.enchant.FateEnchantments;
import net.minecraft.enchantment.Enchantment;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.registry.RegistryKey;
import net.minecraft.entity.data.DataTracker;
import net.minecraft.entity.data.TrackedData;
import net.minecraft.entity.data.TrackedDataHandlerRegistry;
import net.minecraft.entity.projectile.PersistentProjectileEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.particle.DustParticleEffect;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import org.joml.Vector3f;

/** Espada proyectada que dispara el arco. Si es Caladbolg II, va recta, gira y explota al impactar. */
public class SwordArrowEntity extends PersistentProjectileEntity {
    private static final TrackedData<Boolean> CALADBOLG = DataTracker.registerData(SwordArrowEntity.class, TrackedDataHandlerRegistry.BOOLEAN);
    private static final DustParticleEffect RED_DUST = new DustParticleEffect(new Vector3f(0.9F, 0.15F, 0.2F), 1.0F);

    public SwordArrowEntity(EntityType<? extends SwordArrowEntity> type, World world) {
        super(type, world);
    }

    public SwordArrowEntity(World world, LivingEntity owner, ItemStack bow, boolean caladbolg) {
        super(FateUBW.SWORD_ARROW_ENTITY, owner, world, new ItemStack(Items.ARROW), bow);
        dataTracker.set(CALADBOLG, caladbolg);
        pickupType = PickupPermission.DISALLOWED;
        setDamage(caladbolg ? 12.0 : 3.0);
        setNoGravity(caladbolg);
    }

    public boolean isCaladbolg() {
        return dataTracker.get(CALADBOLG);
    }

    @Override
    protected void initDataTracker(DataTracker.Builder builder) {
        super.initDataTracker(builder);
        builder.add(CALADBOLG, false);
    }

    @Override
    public void tick() {
        super.tick();
        if (getWorld().isClient) {
            trail();
        } else if (age > 200) {
            discard();
        }
    }

    // Estela: chispas normales, o espiral roja para Caladbolg
    private void trail() {
        Vec3d v = getVelocity();
        if (v.lengthSquared() < 1.0E-4) return;
        if (!isCaladbolg()) {
            getWorld().addParticle(ParticleTypes.CRIT, getX(), getY(), getZ(), 0.0, 0.0, 0.0);
            return;
        }
        Vec3d dir = v.normalize();
        Vec3d a = dir.crossProduct(new Vec3d(0.0, 1.0, 0.0));
        a = a.lengthSquared() < 1.0E-4 ? new Vec3d(1.0, 0.0, 0.0) : a.normalize();
        Vec3d b = dir.crossProduct(a);
        for (int i = 0; i < 3; i++) {
            double angle = age * 0.9 + i * Math.PI * 2 / 3;
            Vec3d p = getPos().add(a.multiply(Math.cos(angle) * 0.45)).add(b.multiply(Math.sin(angle) * 0.45));
            getWorld().addParticle(RED_DUST, p.x, p.y, p.z, 0.0, 0.0, 0.0);
        }
        getWorld().addParticle(ParticleTypes.ELECTRIC_SPARK, getX(), getY(), getZ(), 0.0, 0.0, 0.0);
    }

    @Override
    protected void onEntityHit(EntityHitResult hit) {
        if (isCaladbolg()) {
            brokenPhantasm();
            return;
        }
        super.onEntityHit(hit);
        // Broken Blade: a veces la espada proyectada se rompe dentro y marchita un momento
        if (getWorld() instanceof ServerWorld world && hit.getEntity() instanceof LivingEntity target) {
            int level = bowLevel(FateEnchantments.BROKEN_BLADE);
            if (level > 0 && world.random.nextFloat() < 0.15F * level) {
                target.addStatusEffect(new StatusEffectInstance(StatusEffects.WITHER, 30 + 20 * level, 0), getOwner());
                world.spawnParticles(ParticleTypes.SMOKE, target.getX(), target.getBodyY(0.5), target.getZ(), 10, 0.2, 0.3, 0.2, 0.02);
            }
        }
    }

    private int bowLevel(RegistryKey<Enchantment> key) {
        ItemStack bow = getWeaponStack();
        return bow == null ? 0 : FateEnchantments.level(getWorld(), bow, key);
    }

    @Override
    protected void onBlockHit(BlockHitResult hit) {
        if (isCaladbolg()) {
            brokenPhantasm();
            return;
        }
        // Las armas proyectadas se desvanecen en vez de quedarse clavadas
        if (getWorld() instanceof ServerWorld world) {
            world.spawnParticles(ParticleTypes.END_ROD, getX(), getY(), getZ(), 6, 0.1, 0.1, 0.1, 0.05);
        }
        discard();
    }

    // Broken Phantasm: la espada explota. Solo rompe bloques con la gamerule fateAbilitiesBreakBlocks
    private void brokenPhantasm() {
        if (getWorld() instanceof ServerWorld world) {
            boolean griefing = FateUBW.breaksBlocks(world, getPos());
            // Phantasm Bloom: la explosión crece la mitad sin gastar más maná
            float bloom = bowLevel(FateEnchantments.PHANTASM_BLOOM) > 0 ? 1.5F : 1.0F;
            world.createExplosion(this, FateUBW.explosion(world, this, getOwner()), null, getX(), getY(), getZ(), (griefing ? 5.0F : 4.0F) * bloom, false,
                    griefing ? World.ExplosionSourceType.TNT : World.ExplosionSourceType.NONE);
            world.spawnParticles(ParticleTypes.FLASH, getX(), getY(), getZ(), 1, 0.0, 0.0, 0.0, 0.0);
            world.spawnParticles(RED_DUST, getX(), getY(), getZ(), 40, 1.5, 1.5, 1.5, 0.0);
        }
        discard();
    }

    @Override
    protected ItemStack getDefaultItemStack() {
        return new ItemStack(Items.ARROW);
    }
}
