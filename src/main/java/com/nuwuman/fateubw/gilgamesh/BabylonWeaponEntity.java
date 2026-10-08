package com.nuwuman.fateubw.gilgamesh;

import com.nuwuman.fateubw.FateUBW;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.data.DataTracker;
import net.minecraft.entity.data.TrackedData;
import net.minecraft.entity.data.TrackedDataHandlerRegistry;
import net.minecraft.entity.projectile.PersistentProjectileEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.world.World;

/** Un arma del tesoro disparada desde un portal: vuela recta, hiere y se desvanece al chocar. */
public class BabylonWeaponEntity extends PersistentProjectileEntity {
    private static final TrackedData<ItemStack> STACK = DataTracker.registerData(BabylonWeaponEntity.class, TrackedDataHandlerRegistry.ITEM_STACK);

    public BabylonWeaponEntity(EntityType<? extends BabylonWeaponEntity> type, World world) {
        super(type, world);
    }

    public BabylonWeaponEntity(World world, LivingEntity owner, ItemStack weapon) {
        super(FateUBW.BABYLON_WEAPON, owner, world, new ItemStack(Items.ARROW), null);
        dataTracker.set(STACK, weapon);
        pickupType = PickupPermission.DISALLOWED;
        setNoGravity(true);
        setDamage(2.5);
    }

    public ItemStack getStack() {
        return dataTracker.get(STACK);
    }

    @Override
    protected void initDataTracker(DataTracker.Builder builder) {
        super.initDataTracker(builder);
        builder.add(STACK, ItemStack.EMPTY);
    }

    @Override
    public void tick() {
        super.tick();
        if (getWorld().isClient) {
            if (age % 2 == 0) getWorld().addParticle(ParticleTypes.END_ROD, getX(), getY(), getZ(), 0.0, 0.0, 0.0);
        } else if (age > 100) {
            discard();
        }
    }

    @Override
    protected void onBlockHit(BlockHitResult hit) {
        if (getWorld() instanceof ServerWorld world) {
            world.spawnParticles(ParticleTypes.CRIT, getX(), getY(), getZ(), 8, 0.2, 0.2, 0.2, 0.2);
        }
        discard();
    }

    @Override
    protected ItemStack getDefaultItemStack() {
        return new ItemStack(Items.ARROW);
    }
}
