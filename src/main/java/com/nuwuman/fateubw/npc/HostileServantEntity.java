package com.nuwuman.fateubw.npc;

import com.nuwuman.fateubw.FateUBW;
import com.nuwuman.fateubw.Voices;
import com.nuwuman.fateubw.lancer.GaeBolgItem;
import net.minecraft.entity.EntityData;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.SpawnReason;
import net.minecraft.entity.ai.goal.ActiveTargetGoal;
import net.minecraft.entity.ai.goal.LookAroundGoal;
import net.minecraft.entity.ai.goal.LookAtEntityGoal;
import net.minecraft.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.entity.ai.goal.RevengeGoal;
import net.minecraft.entity.ai.goal.SwimGoal;
import net.minecraft.entity.ai.goal.WanderAroundFarGoal;
import net.minecraft.entity.attribute.DefaultAttributeContainer;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.boss.BossBar;
import net.minecraft.entity.boss.ServerBossBar;
import net.minecraft.entity.mob.HostileEntity;
import net.minecraft.entity.passive.IronGolemEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.registry.tag.DamageTypeTags;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.LocalDifficulty;
import net.minecraft.world.ServerWorldAccess;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * Un servant enemigo con su ropa y su arma.
 * <ul>
 *   <li>Berserker (jefe, solo con su huevo): 300 de vida, barra de jefe, God Hand (once vidas; los golpes de menos de 4
 *   no le hacen nada) y Nine Lives contra quien tenga cerca.</li>
 *   <li>Lancer: rápido, esquiva tres de cada cuatro proyectiles y se lanza con la Gáe Bolg.</li>
 *   <li>Assassin: invisible mientras no tiene a nadie cerca, esquiva uno de cada cuatro golpes y usa Tsubame Gaeshi.</li>
 * </ul>
 * Lancer y Assassin aparecen de noche, muy de vez en cuando (/gamerule fateServantSpawns false lo quita).
 */
public class HostileServantEntity extends HostileEntity {
    public enum Kind {
        BERSERKER(300, 14, 0.30, 15, 5.0, 11, 1.0F, 50),
        LANCER(80, 9, 0.38, 20, 12.0, 0, 0.1F, 25),
        ASSASSIN(70, 10, 0.34, 12, 4.5, 0, 0.1F, 25);

        final double health, damage, speed;
        final int specialCooldown;
        final double specialRange;
        final int lives;
        final float weaponDropChance;
        final int xp;

        Kind(double health, double damage, double speed, int specialSeconds, double specialRange, int lives, float weaponDropChance, int xp) {
            this.health = health;
            this.damage = damage;
            this.speed = speed;
            this.specialCooldown = specialSeconds * 20;
            this.specialRange = specialRange;
            this.lives = lives;
            this.weaponDropChance = weaponDropChance;
            this.xp = xp;
        }

        public String id() {
            return name().toLowerCase();
        }
    }

    private final Kind kind;
    private int lives;
    private int special;
    @Nullable
    private final ServerBossBar bossBar;

    public HostileServantEntity(EntityType<? extends HostileServantEntity> type, World world, Kind kind) {
        super(type, world);
        this.kind = kind;
        this.lives = kind.lives;
        this.special = 40;
        this.experiencePoints = kind.xp;
        this.bossBar = kind == Kind.BERSERKER ? new ServerBossBar(Text.translatable("entity.fate_ubw.berserker"),
                BossBar.Color.RED, BossBar.Style.NOTCHED_12) : null;
    }

    public Kind kind() {
        return kind;
    }

    public static DefaultAttributeContainer.Builder attributes(Kind kind) {
        return HostileEntity.createHostileAttributes()
                .add(EntityAttributes.GENERIC_MAX_HEALTH, kind.health)
                .add(EntityAttributes.GENERIC_ATTACK_DAMAGE, kind.damage)
                .add(EntityAttributes.GENERIC_MOVEMENT_SPEED, kind.speed)
                .add(EntityAttributes.GENERIC_FOLLOW_RANGE, 40.0)
                .add(EntityAttributes.GENERIC_ARMOR, 10.0)
                .add(EntityAttributes.GENERIC_KNOCKBACK_RESISTANCE, kind == Kind.BERSERKER ? 1.0 : 0.4);
    }

    @Override
    protected void initGoals() {
        goalSelector.add(0, new SwimGoal(this));
        goalSelector.add(2, new MeleeAttackGoal(this, 1.15, false));
        goalSelector.add(6, new WanderAroundFarGoal(this, 0.8));
        goalSelector.add(7, new LookAtEntityGoal(this, PlayerEntity.class, 10.0F));
        goalSelector.add(8, new LookAroundGoal(this));
        targetSelector.add(1, new RevengeGoal(this));
        targetSelector.add(2, new ActiveTargetGoal<>(this, PlayerEntity.class, true));
        targetSelector.add(3, new ActiveTargetGoal<>(this, IronGolemEntity.class, true));
    }

    @Override
    public EntityData initialize(ServerWorldAccess world, LocalDifficulty difficulty, SpawnReason reason, @Nullable EntityData data) {
        EntityData result = super.initialize(world, difficulty, reason, data);
        equipGear();
        return result;
    }

    // /summon con NBT no llama a initialize: se viste en su primer tick si no lleva nada
    @Override
    public void tick() {
        super.tick();
        if (!getWorld().isClient && age == 1 && getEquippedStack(EquipmentSlot.CHEST).isEmpty()) equipGear();
    }

    private void equipGear() {
        List<Item> gear = switch (kind) {
            case BERSERKER -> List.of(FateUBW.BERSERKER_CHESTPLATE, FateUBW.BERSERKER_LEGGINGS, FateUBW.BERSERKER_BOOTS, FateUBW.BERSERKER_AXE_SWORD);
            case LANCER -> List.of(FateUBW.LANCER_CHESTPLATE, FateUBW.LANCER_LEGGINGS, FateUBW.LANCER_BOOTS, FateUBW.GAE_BOLG);
            case ASSASSIN -> List.of(FateUBW.ASSASSIN_CHESTPLATE, FateUBW.ASSASSIN_LEGGINGS, FateUBW.ASSASSIN_BOOTS, FateUBW.MONOHOSHIZAO);
        };
        equipStack(EquipmentSlot.CHEST, new ItemStack(gear.get(0)));
        equipStack(EquipmentSlot.LEGS, new ItemStack(gear.get(1)));
        equipStack(EquipmentSlot.FEET, new ItemStack(gear.get(2)));
        equipStack(EquipmentSlot.MAINHAND, new ItemStack(gear.get(3)));
        float armorDrop = kind == Kind.BERSERKER ? 0.3F : 0.08F;
        for (EquipmentSlot slot : new EquipmentSlot[]{EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET}) {
            setEquipmentDropChance(slot, armorDrop);
        }
        setEquipmentDropChance(EquipmentSlot.MAINHAND, kind.weaponDropChance);
        if (kind == Kind.BERSERKER) setPersistent();
    }

    @Override
    public boolean damage(DamageSource source, float amount) {
        boolean bypass = source.isIn(DamageTypeTags.BYPASSES_INVULNERABILITY);
        if (!bypass && !getWorld().isClient) {
            // God Hand, Protección contra Proyectiles y Ojo de la Mente, como su ropa en un jugador
            if (kind == Kind.BERSERKER && amount < 4.0F) return false;
            if (kind == Kind.LANCER && source.isIn(DamageTypeTags.IS_PROJECTILE) && random.nextFloat() < 0.75F) return false;
            if (kind == Kind.ASSASSIN && source.getAttacker() != null && source.getSource() == source.getAttacker()
                    && random.nextFloat() < 0.25F) {
                ((ServerWorld) getWorld()).spawnParticles(ParticleTypes.CLOUD, getX(), getBodyY(0.5), getZ(), 8, 0.3, 0.4, 0.3, 0.05);
                return false;
            }
        }
        return super.damage(source, amount);
    }

    /** God Hand: llamado desde ALLOW_DEATH. Devuelve true si ha resucitado. */
    public boolean tryRevive() {
        if (lives <= 0) return false;
        lives--;
        setHealth(getMaxHealth());
        addStatusEffect(new StatusEffectInstance(StatusEffects.RESISTANCE, 60, 2));
        if (getWorld() instanceof ServerWorld world) {
            world.spawnParticles(ParticleTypes.TOTEM_OF_UNDYING, getX(), getBodyY(0.5), getZ(), 60, 0.7, 1.0, 0.7, 0.3);
            world.playSound(null, getX(), getY(), getZ(), SoundEvents.ENTITY_RAVAGER_ROAR, SoundCategory.HOSTILE, 3.0F, 0.5F);
        }
        return true;
    }

    @Override
    protected void mobTick() {
        super.mobTick();
        if (!(getWorld() instanceof ServerWorld world)) return;
        if (bossBar != null) {
            bossBar.setPercent(getHealth() / getMaxHealth());
            bossBar.setName(Text.translatable("entity.fate_ubw.berserker.bar", lives));
        }
        LivingEntity target = getTarget();
        if (kind == Kind.ASSASSIN) {
            // Ocultación de Presencia: invisible hasta que tiene a su presa cerca
            boolean hidden = target == null || squaredDistanceTo(target) > 10 * 10;
            if (hidden) addStatusEffect(new StatusEffectInstance(StatusEffects.INVISIBILITY, 30, 0, false, false));
            else removeStatusEffect(StatusEffects.INVISIBILITY);
        }
        if (special > 0) special--;
        if (special > 0 || target == null || !target.isAlive() || !canSee(target)
                || squaredDistanceTo(target) > kind.specialRange * kind.specialRange) return;
        special = kind.specialCooldown;
        switch (kind) {
            case BERSERKER -> nineLives(world, target);
            case LANCER -> gaeBolg(world, target);
            case ASSASSIN -> tsubameGaeshi(world, target);
        }
    }

    // Nueve golpes en un instante a todo lo que tenga delante
    private void nineLives(ServerWorld world, LivingEntity target) {
        Vec3d dir = target.getPos().subtract(getPos()).normalize();
        for (LivingEntity victim : world.getEntitiesByClass(LivingEntity.class, getBoundingBox().stretch(dir.multiply(5.0)).expand(2.0),
                e -> e != this && e.isAlive() && !(e instanceof HostileServantEntity))) {
            for (int i = 0; i < 9; i++) {
                victim.timeUntilRegen = 0;
                victim.damage(world.getDamageSources().mobAttack(this), 4.0F);
            }
            victim.takeKnockback(2.5, -dir.x, -dir.z);
        }
        swingHand(net.minecraft.util.Hand.MAIN_HAND);
        world.spawnParticles(ParticleTypes.SWEEP_ATTACK, target.getX(), target.getBodyY(0.5), target.getZ(), 9, 1.2, 0.8, 1.2, 0.0);
        world.spawnParticles(ParticleTypes.EXPLOSION, target.getX(), target.getBodyY(0.5), target.getZ(), 3, 0.8, 0.5, 0.8, 0.0);
        world.playSound(null, getX(), getY(), getZ(), SoundEvents.ENTITY_RAVAGER_ROAR, SoundCategory.HOSTILE, 2.0F, 0.6F);
        Voices.say(world, this, "nine_lives");
    }

    // Se lanza contra su objetivo y le atraviesa el corazón
    private void gaeBolg(ServerWorld world, LivingEntity target) {
        Vec3d to = target.getPos().subtract(getPos());
        setVelocity(to.normalize().multiply(Math.min(2.0, to.length() * 0.3)).add(0.0, 0.2, 0.0));
        velocityModified = true;
        target.damage(world.getDamageSources().create(FateUBW.GAE_BOLG_DAMAGE, this, this), 16.0F);
        target.addStatusEffect(new StatusEffectInstance(StatusEffects.WITHER, 100, 1));
        swingHand(net.minecraft.util.Hand.MAIN_HAND);
        world.spawnParticles(GaeBolgItem.CRIMSON, target.getX(), target.getBodyY(0.6), target.getZ(), 30, 0.3, 0.4, 0.3, 0.0);
        world.playSound(null, getX(), getY(), getZ(), SoundEvents.ENTITY_PLAYER_ATTACK_CRIT, SoundCategory.HOSTILE, 1.5F, 0.6F);
        Voices.say(world, this, "gae_bolg");
    }

    // Tres cortes en el mismo instante
    private void tsubameGaeshi(ServerWorld world, LivingEntity target) {
        for (int cut = 0; cut < 3; cut++) {
            target.timeUntilRegen = 0;
            target.damage(world.getDamageSources().mobAttack(this), 7.0F);
            world.spawnParticles(ParticleTypes.SWEEP_ATTACK, target.getX(), target.getBodyY(0.3 + cut * 0.3), target.getZ(), 1, 0.0, 0.0, 0.0, 0.0);
        }
        swingHand(net.minecraft.util.Hand.MAIN_HAND);
        world.playSound(null, getX(), getY(), getZ(), SoundEvents.ENTITY_PLAYER_ATTACK_SWEEP, SoundCategory.HOSTILE, 1.5F, 1.4F);
        Voices.say(world, this, "tsubame_gaeshi");
    }

    @Override
    public void onStartedTrackingBy(ServerPlayerEntity player) {
        super.onStartedTrackingBy(player);
        if (bossBar != null) bossBar.addPlayer(player);
    }

    @Override
    public void onStoppedTrackingBy(ServerPlayerEntity player) {
        super.onStoppedTrackingBy(player);
        if (bossBar != null) bossBar.removePlayer(player);
    }

    @Override
    public void writeCustomDataToNbt(NbtCompound nbt) {
        super.writeCustomDataToNbt(nbt);
        nbt.putInt("GodHandLives", lives);
    }

    @Override
    public void readCustomDataFromNbt(NbtCompound nbt) {
        super.readCustomDataFromNbt(nbt);
        if (nbt.contains("GodHandLives")) lives = nbt.getInt("GodHandLives");
    }

    @Override
    public boolean cannotDespawn() {
        return kind == Kind.BERSERKER || super.cannotDespawn();
    }
}
