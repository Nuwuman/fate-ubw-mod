package com.nuwuman.fateubw.npc;

import com.nuwuman.fateubw.FateUBW;
import com.nuwuman.fateubw.Voices;
import com.nuwuman.fateubw.gilgamesh.BabylonWeaponEntity;
import com.nuwuman.fateubw.lancer.GaeBolgItem;
import net.minecraft.entity.EntityData;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.LightningEntity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.SpawnReason;
import net.minecraft.entity.ai.goal.ActiveTargetGoal;
import net.minecraft.entity.ai.goal.GoToWalkTargetGoal;
import net.minecraft.entity.ai.goal.LookAroundGoal;
import net.minecraft.entity.ai.goal.LookAtEntityGoal;
import net.minecraft.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.entity.ai.goal.RevengeGoal;
import net.minecraft.entity.ai.goal.SwimGoal;
import net.minecraft.entity.ai.goal.WanderAroundFarGoal;
import net.minecraft.entity.attribute.DefaultAttributeContainer;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.boss.BossBar;
import net.minecraft.entity.boss.ServerBossBar;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.damage.DamageTypes;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.mob.HostileEntity;
import net.minecraft.entity.passive.IronGolemEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.particle.DustParticleEffect;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.registry.tag.DamageTypeTags;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Hand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.LocalDifficulty;
import net.minecraft.world.ServerWorldAccess;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3f;

import java.util.List;

/**
 * Un servant enemigo con su ropa y su arma.
 * <ul>
 *   <li>Berserker (jefe): 40 de vida (480 contando sus vidas, como un Warden), barra de jefe, God Hand (once vidas; los
 *   golpes de menos de 4 no le hacen nada) y Nine Lives contra quien tenga cerca.</li>
 *   <li>Lancer: rápido, esquiva tres de cada cuatro proyectiles y se lanza con la Gáe Bolg.</li>
 *   <li>Assassin: invisible mientras no tiene a nadie cerca, esquiva uno de cada cuatro golpes y usa Tsubame Gaeshi.</li>
 *   <li>Saber (jefe): inmune a la magia; Strike Air a quien tenga delante.</li>
 *   <li>Archer: Kanshō y Bakuya de cerca; de lejos dispara Caladbolg II, que estalla.</li>
 *   <li>Rider: muy rápida; los Ojos Místicos frenan casi en seco a quien la mira.</li>
 *   <li>Gilgamesh (jefe): Gate of Babylon, una lluvia de armas del tesoro.</li>
 *   <li>Caster: Palabras Divinas (rayos) de lejos y se teletransporta si la acorralan.</li>
 * </ul>
 * Lancer y Assassin aparecen de noche, muy de vez en cuando (/gamerule fateServantSpawns false lo quita). Todos guardan
 * su santuario (estructura): si se alejan, vuelven a él.
 */
public class HostileServantEntity extends HostileEntity {
    public enum Kind {
        //         vida daño velocidad especial(s) alcance vidas soltar-arma xp jefe
        BERSERKER(40, 14, 0.30, 15, 5.0, 11, 1.0F, 50, true),
        LANCER(80, 9, 0.38, 20, 12.0, 0, 0.1F, 25, false),
        ASSASSIN(70, 10, 0.34, 12, 4.5, 0, 0.1F, 25, false),
        SABER(110, 11, 0.33, 10, 8.0, 0, 0.1F, 50, true),
        ARCHER(80, 9, 0.34, 9, 24.0, 0, 0.1F, 35, false),
        RIDER(75, 9, 0.42, 14, 12.0, 0, 0.1F, 35, false),
        GILGAMESH(120, 10, 0.30, 7, 22.0, 0, 0.08F, 60, true),
        CASTER(60, 6, 0.32, 6, 18.0, 0, 0.1F, 35, false);

        final double health, damage, speed;
        final int specialCooldown;
        final double specialRange;
        final int lives;
        final float weaponDropChance;
        final int xp;
        final boolean boss;

        Kind(double health, double damage, double speed, int specialSeconds, double specialRange, int lives,
             float weaponDropChance, int xp, boolean boss) {
            this.health = health;
            this.damage = damage;
            this.speed = speed;
            this.specialCooldown = specialSeconds * 20;
            this.specialRange = specialRange;
            this.lives = lives;
            this.weaponDropChance = weaponDropChance;
            this.xp = xp;
            this.boss = boss;
        }

        public String id() {
            return name().toLowerCase();
        }
    }

    private static final DustParticleEffect PURPLE = new DustParticleEffect(new Vector3f(0.6F, 0.2F, 0.9F), 1.2F);
    private static final int GUARD_RADIUS = 10;

    private final Kind kind;
    private int lives;
    private int special;
    // Santuario que guarda (null si apareció suelto o con su huevo)
    @Nullable
    private BlockPos home;
    @Nullable
    private final ServerBossBar bossBar;

    public HostileServantEntity(EntityType<? extends HostileServantEntity> type, World world, Kind kind) {
        super(type, world);
        this.kind = kind;
        this.lives = kind.lives;
        this.special = 40;
        this.experiencePoints = kind.xp;
        this.bossBar = kind.boss ? new ServerBossBar(kind == Kind.BERSERKER ? Text.translatable("entity.fate_ubw.berserker")
                : type.getName(), BossBar.Color.RED, kind == Kind.BERSERKER ? BossBar.Style.NOTCHED_12 : BossBar.Style.PROGRESS) : null;
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
        goalSelector.add(5, new GoToWalkTargetGoal(this, 1.0));      // vuelve a su santuario si se aleja
        goalSelector.add(6, new WanderAroundFarGoal(this, 0.8));
        goalSelector.add(7, new LookAtEntityGoal(this, PlayerEntity.class, 10.0F));
        goalSelector.add(8, new LookAroundGoal(this));
        targetSelector.add(1, new RevengeGoal(this));
        targetSelector.add(2, new ActiveTargetGoal<>(this, PlayerEntity.class, true));
        targetSelector.add(3, new ActiveTargetGoal<>(this, IronGolemEntity.class, true));
    }

    /** Lo pone a guardar un santuario: no se aleja de él y no desaparece. */
    public void guard(BlockPos pos) {
        home = pos.toImmutable();
        setPositionTarget(home, GUARD_RADIUS);
        setPersistent();
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
        // peto, grebas, botas, mano principal; y si los tiene, mano izquierda y cabeza
        List<Item> gear = switch (kind) {
            case BERSERKER -> List.of(FateUBW.BERSERKER_CHESTPLATE, FateUBW.BERSERKER_LEGGINGS, FateUBW.BERSERKER_BOOTS, FateUBW.BERSERKER_AXE_SWORD);
            case LANCER -> List.of(FateUBW.LANCER_CHESTPLATE, FateUBW.LANCER_LEGGINGS, FateUBW.LANCER_BOOTS, FateUBW.GAE_BOLG);
            case ASSASSIN -> List.of(FateUBW.ASSASSIN_CHESTPLATE, FateUBW.ASSASSIN_LEGGINGS, FateUBW.ASSASSIN_BOOTS, FateUBW.MONOHOSHIZAO);
            case SABER -> List.of(FateUBW.SABER_CHESTPLATE, FateUBW.SABER_LEGGINGS, FateUBW.SABER_BOOTS, FateUBW.EXCALIBUR);
            case ARCHER -> List.of(FateUBW.ARCHER_CHESTPLATE, FateUBW.ARCHER_LEGGINGS, FateUBW.ARCHER_BOOTS, FateUBW.KANSHOU, FateUBW.BAKUYA);
            case RIDER -> List.of(FateUBW.RIDER_CHESTPLATE, FateUBW.RIDER_LEGGINGS, FateUBW.RIDER_BOOTS, FateUBW.RIDER_DAGGER,
                    Items.AIR, FateUBW.RIDER_HELMET);
            case GILGAMESH -> List.of(FateUBW.GILGAMESH_CHESTPLATE, FateUBW.GILGAMESH_LEGGINGS, FateUBW.GILGAMESH_BOOTS, FateUBW.EA);
            case CASTER -> List.of(FateUBW.CASTER_CHESTPLATE, FateUBW.CASTER_LEGGINGS, FateUBW.CASTER_BOOTS, FateUBW.RULE_BREAKER,
                    Items.AIR, FateUBW.CASTER_HOOD);
        };
        EquipmentSlot[] slots = {EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET, EquipmentSlot.MAINHAND,
                EquipmentSlot.OFFHAND, EquipmentSlot.HEAD};
        for (int i = 0; i < gear.size(); i++) {
            if (gear.get(i) != Items.AIR) equipStack(slots[i], new ItemStack(gear.get(i)));
        }
        float armorDrop = kind == Kind.BERSERKER ? 0.3F : 0.08F;
        for (EquipmentSlot slot : new EquipmentSlot[]{EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET, EquipmentSlot.HEAD}) {
            setEquipmentDropChance(slot, armorDrop);
        }
        setEquipmentDropChance(EquipmentSlot.MAINHAND, kind.weaponDropChance);
        setEquipmentDropChance(EquipmentSlot.OFFHAND, kind.weaponDropChance);
        if (kind == Kind.BERSERKER) setPersistent();
    }

    @Override
    public boolean damage(DamageSource source, float amount) {
        boolean bypass = source.isIn(DamageTypeTags.BYPASSES_INVULNERABILITY);
        if (!bypass && !getWorld().isClient) {
            // God Hand, Protección contra Proyectiles, Ojo de la Mente y Resistencia Mágica, como su ropa en un jugador
            if (kind == Kind.BERSERKER && amount < 4.0F) return false;
            if (kind == Kind.LANCER && source.isIn(DamageTypeTags.IS_PROJECTILE) && random.nextFloat() < 0.75F) return false;
            if (kind == Kind.ASSASSIN && source.getAttacker() != null && source.getSource() == source.getAttacker()
                    && random.nextFloat() < 0.25F) {
                ((ServerWorld) getWorld()).spawnParticles(ParticleTypes.CLOUD, getX(), getBodyY(0.5), getZ(), 8, 0.3, 0.4, 0.3, 0.05);
                return false;
            }
            if (kind == Kind.SABER && (source.isOf(DamageTypes.MAGIC) || source.isOf(DamageTypes.INDIRECT_MAGIC)
                    || source.isOf(DamageTypes.WITHER) || source.isOf(DamageTypes.DRAGON_BREATH))) return false;
            // Transferencia Espacial: si la acorralan, desaparece y reaparece lejos
            if (kind == Kind.CASTER && source.getAttacker() != null && squaredDistanceTo(source.getAttacker()) < 16
                    && random.nextFloat() < 0.35F) {
                boolean hurt = super.damage(source, amount);
                spatialTransfer();
                return hurt;
            }
        }
        return super.damage(source, amount);
    }

    private void spatialTransfer() {
        Vec3d center = home != null ? Vec3d.ofBottomCenter(home) : getPos();
        for (int tries = 0; tries < 12; tries++) {
            double x = center.x + (random.nextDouble() - 0.5) * 16, z = center.z + (random.nextDouble() - 0.5) * 16;
            double y = center.y + random.nextInt(5) - 2;
            if (teleport(x, y, z, true)) {
                getWorld().playSound(null, getX(), getY(), getZ(), SoundEvents.ENTITY_ENDERMAN_TELEPORT, SoundCategory.HOSTILE, 1.0F, 1.3F);
                return;
            }
        }
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
            if (kind == Kind.BERSERKER) bossBar.setName(Text.translatable("entity.fate_ubw.berserker.bar", lives));
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
        // Archer y Caster lo usan de lejos: pegados a su presa prefieren las armas
        if ((kind == Kind.ARCHER || kind == Kind.CASTER) && squaredDistanceTo(target) < 5 * 5) return;
        special = kind.specialCooldown;
        switch (kind) {
            case BERSERKER -> nineLives(world, target);
            case LANCER -> gaeBolg(world, target);
            case ASSASSIN -> tsubameGaeshi(world, target);
            case SABER -> strikeAir(world, target);
            case ARCHER -> caladbolg(world, target);
            case RIDER -> mysticEyes(world, target);
            case GILGAMESH -> gateOfBabylon(world, target);
            case CASTER -> divineWords(world, target);
        }
    }

    private boolean foe(LivingEntity e) {
        return e != this && e.isAlive() && !(e instanceof HostileServantEntity);
    }

    // Nueve golpes en un instante a todo lo que tenga delante
    private void nineLives(ServerWorld world, LivingEntity target) {
        Vec3d dir = target.getPos().subtract(getPos()).normalize();
        for (LivingEntity victim : world.getEntitiesByClass(LivingEntity.class, getBoundingBox().stretch(dir.multiply(5.0)).expand(2.0), this::foe)) {
            for (int i = 0; i < 9; i++) {
                victim.timeUntilRegen = 0;
                victim.damage(world.getDamageSources().mobAttack(this), 4.0F);
            }
            victim.takeKnockback(2.5, -dir.x, -dir.z);
        }
        swingHand(Hand.MAIN_HAND);
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
        swingHand(Hand.MAIN_HAND);
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
        swingHand(Hand.MAIN_HAND);
        world.playSound(null, getX(), getY(), getZ(), SoundEvents.ENTITY_PLAYER_ATTACK_SWEEP, SoundCategory.HOSTILE, 1.5F, 1.4F);
        Voices.say(world, this, "tsubame_gaeshi");
    }

    // Strike Air: el viento que oculta Excalibur sale disparado en cono y lo barre todo
    private void strikeAir(ServerWorld world, LivingEntity target) {
        Vec3d dir = target.getPos().subtract(getPos()).normalize();
        Vec3d eye = getEyePos();
        for (LivingEntity victim : world.getEntitiesByClass(LivingEntity.class, getBoundingBox().stretch(dir.multiply(8.0)).expand(2.0), this::foe)) {
            Vec3d to = victim.getBoundingBox().getCenter().subtract(eye);
            if (to.length() > 8.0 || to.normalize().dotProduct(dir) < 0.5) continue;
            victim.damage(world.getDamageSources().mobAttack(this), 9.0F);
            victim.takeKnockback(2.0, -dir.x, -dir.z);
            victim.addVelocity(0.0, 0.35, 0.0);
            victim.velocityModified = true;
        }
        for (int i = 1; i <= 8; i++) {
            Vec3d p = eye.add(dir.multiply(i));
            world.spawnParticles(ParticleTypes.GUST, p.x, p.y - 0.3, p.z, 1, 0.1, 0.1, 0.1, 0.0);
            world.spawnParticles(ParticleTypes.CLOUD, p.x, p.y - 0.3, p.z, 4, 0.3, 0.3, 0.3, 0.05);
        }
        swingHand(Hand.MAIN_HAND);
        world.playSound(null, getX(), getY(), getZ(), SoundEvents.ENTITY_BREEZE_SHOOT, SoundCategory.HOSTILE, 1.5F, 0.8F);
    }

    // Caladbolg II: una espada-flecha que estalla donde está su presa
    private void caladbolg(ServerWorld world, LivingEntity target) {
        Vec3d from = getEyePos(), to = target.getBoundingBox().getCenter();
        for (int i = 0; i <= 16; i++) {
            Vec3d p = from.lerp(to, i / 16.0);
            world.spawnParticles(ParticleTypes.CRIT, p.x, p.y, p.z, 2, 0.05, 0.05, 0.05, 0.0);
        }
        world.createExplosion(this, FateUBW.explosion(world, this, this), null, to.x, to.y, to.z, 2.2F, false, World.ExplosionSourceType.NONE);
        swingHand(Hand.MAIN_HAND);
        world.playSound(null, getX(), getY(), getZ(), SoundEvents.ENTITY_ARROW_SHOOT, SoundCategory.HOSTILE, 1.5F, 0.6F);
        Voices.say(world, this, "caladbolg");
    }

    // Ojos Místicos: quien la mira casi se queda de piedra
    private void mysticEyes(ServerWorld world, LivingEntity target) {
        target.addStatusEffect(new StatusEffectInstance(StatusEffects.SLOWNESS, 80, 3));
        target.addStatusEffect(new StatusEffectInstance(StatusEffects.MINING_FATIGUE, 80, 1));
        world.spawnParticles(PURPLE, target.getX(), target.getBodyY(0.5), target.getZ(), 30, 0.4, 0.8, 0.4, 0.0);
        world.playSound(null, target.getX(), target.getY(), target.getZ(), SoundEvents.BLOCK_STONE_PLACE, SoundCategory.HOSTILE, 1.5F, 0.6F);
        Voices.say(world, this, "mystic_eyes");
    }

    // Gate of Babylon: armas del tesoro que salen de detrás de él hacia su presa
    private void gateOfBabylon(ServerWorld world, LivingEntity target) {
        Item[] treasure = {FateUBW.GAE_BOLG, FateUBW.KANSHOU, FateUBW.BAKUYA, Items.GOLDEN_SWORD, Items.DIAMOND_SWORD, Items.TRIDENT};
        Vec3d back = getRotationVector().multiply(-1.2);
        for (int i = 0; i < 5; i++) {
            Vec3d at = getPos().add(back).add((random.nextDouble() - 0.5) * 3.0, 2.0 + random.nextDouble() * 1.5, (random.nextDouble() - 0.5) * 3.0);
            BabylonWeaponEntity weapon = new BabylonWeaponEntity(world, this, new ItemStack(treasure[random.nextInt(treasure.length)]));
            weapon.setPosition(at);
            Vec3d dir = target.getBoundingBox().getCenter().subtract(at);
            weapon.setVelocity(dir.x, dir.y, dir.z, 2.0F, 2.0F);
            world.spawnEntity(weapon);
            world.spawnParticles(ParticleTypes.END_ROD, at.x, at.y, at.z, 6, 0.3, 0.3, 0.3, 0.02);
        }
        world.playSound(null, getX(), getY(), getZ(), SoundEvents.BLOCK_ENCHANTMENT_TABLE_USE, SoundCategory.HOSTILE, 1.5F, 0.6F);
        Voices.say(world, this, "gate_of_babylon");
    }

    // Palabras Divinas Rápidas: tres rayos alrededor de su presa
    private void divineWords(ServerWorld world, LivingEntity target) {
        for (int i = 0; i < 3; i++) {
            Vec3d at = target.getPos().add(i == 0 ? 0.0 : (random.nextDouble() - 0.5) * 4.0, 0.0, i == 0 ? 0.0 : (random.nextDouble() - 0.5) * 4.0);
            LightningEntity bolt = EntityType.LIGHTNING_BOLT.create(world);
            if (bolt == null) continue;
            bolt.setCosmetic(true);
            bolt.refreshPositionAfterTeleport(at);
            world.spawnEntity(bolt);
            for (LivingEntity victim : world.getEntitiesByClass(LivingEntity.class, new net.minecraft.util.math.Box(at, at).expand(1.5, 3.0, 1.5), this::foe)) {
                victim.damage(world.getDamageSources().indirectMagic(this, this), 6.0F);
            }
        }
        world.spawnParticles(PURPLE, getX(), getBodyY(0.8), getZ(), 20, 0.5, 0.5, 0.5, 0.0);
        Voices.say(world, this, "divine_words");
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
        if (home != null) nbt.putLong("Home", home.asLong());
    }

    @Override
    public void readCustomDataFromNbt(NbtCompound nbt) {
        super.readCustomDataFromNbt(nbt);
        if (nbt.contains("GodHandLives")) lives = nbt.getInt("GodHandLives");
        if (nbt.contains("Home")) guard(BlockPos.fromLong(nbt.getLong("Home")));
    }

    @Override
    public boolean cannotDespawn() {
        return kind == Kind.BERSERKER || home != null || super.cannotDespawn();
    }
}
