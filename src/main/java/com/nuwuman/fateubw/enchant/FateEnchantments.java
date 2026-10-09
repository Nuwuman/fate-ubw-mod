package com.nuwuman.fateubw.enchant;

import com.nuwuman.fateubw.FateUBW;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.enchantment.Enchantment;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.damage.DamageTypes;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.particle.DustParticleEffect;
import net.minecraft.particle.ParticleEffect;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.Hand;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.BiConsumer;

/**
 * Encantamientos propios de cada arma (data/fate_ubw/enchantment). Los datos solo dicen a qué arma van y cuántos
 * niveles tienen; lo que hacen está en el código de cada arma, que pregunta el nivel con {@link #level}. Aquí viven
 * los efectos que no son de un arma concreta: las zonas en el suelo, las tareas con retraso, la estela de sangre
 * de Bloodied Spear y God Hand.
 */
public final class FateEnchantments {
    public static final RegistryKey<Enchantment> RADIANT_BLADE = key("radiant_blade");
    public static final RegistryKey<Enchantment> AVALONS_GRACE = key("avalons_grace");
    public static final RegistryKey<Enchantment> YIN_YANG_RESONANCE = key("yin_yang_resonance");
    public static final RegistryKey<Enchantment> TRACE_RESILIENCE = key("trace_resilience");
    public static final RegistryKey<Enchantment> BROKEN_BLADE = key("broken_blade");
    public static final RegistryKey<Enchantment> PHANTASM_BLOOM = key("phantasm_bloom");
    public static final RegistryKey<Enchantment> CURSED_THRUST = key("cursed_thrust");
    public static final RegistryKey<Enchantment> BLOODIED_SPEAR = key("bloodied_spear");
    public static final RegistryKey<Enchantment> CHAIN_WHIP = key("chain_whip");
    public static final RegistryKey<Enchantment> GORGONS_GRIP = key("gorgons_grip");
    public static final RegistryKey<Enchantment> WORLD_SEVERANCE = key("world_severance");
    public static final RegistryKey<Enchantment> CONTRACT_BREAKER = key("contract_breaker");
    public static final RegistryKey<Enchantment> HEARTBEAT = key("heartbeat");
    public static final RegistryKey<Enchantment> GOD_HAND = key("god_hand");

    private static final DustParticleEffect BLOOD = new DustParticleEffect(new Vector3f(0.55F, 0.02F, 0.05F), 1.4F);
    private static final int GOD_HAND_COOLDOWN = 20 * 60;

    /** Un área en el suelo que cada medio segundo afecta a quien la pisa. */
    private record Zone(ServerWorld world, Vec3d center, double radius, int[] left, @Nullable Entity owner,
                        ParticleEffect particle, BiConsumer<Zone, LivingEntity> effect) {
    }

    private record Task(int due, Runnable run) {
    }

    private static final List<Zone> ZONES = new ArrayList<>();
    private static final List<Task> TASKS = new ArrayList<>();
    private static final Map<UUID, Integer> GOD_HAND_USED = new HashMap<>();
    private static MinecraftServer server;

    private FateEnchantments() {
    }

    private static RegistryKey<Enchantment> key(String name) {
        return RegistryKey.of(RegistryKeys.ENCHANTMENT, FateUBW.id(name));
    }

    /** Nivel de un encantamiento del mod en un objeto (0 si no lo tiene). */
    public static int level(World world, ItemStack stack, RegistryKey<Enchantment> key) {
        if (stack.isEmpty()) return 0;
        return world.getRegistryManager().get(RegistryKeys.ENCHANTMENT).getEntry(key)
                .map(entry -> EnchantmentHelper.getLevel(entry, stack)).orElse(0);
    }

    /** El nivel más alto de ese encantamiento en el objeto {@code item} que lleve en cualquiera de las dos manos. */
    public static int held(LivingEntity entity, Item item, RegistryKey<Enchantment> key) {
        int best = 0;
        for (Hand hand : Hand.values()) {
            ItemStack stack = entity.getStackInHand(hand);
            if (stack.isOf(item)) best = Math.max(best, level(entity.getWorld(), stack, key));
        }
        return best;
    }

    /** Ejecuta algo dentro de unos ticks (en el hilo del servidor). */
    public static void later(int ticks, Runnable run) {
        if (server != null) TASKS.add(new Task(server.getTicks() + ticks, run));
    }

    public static void register() {
        ServerLifecycleEvents.SERVER_STARTED.register(s -> server = s);
        ServerLifecycleEvents.SERVER_STOPPED.register(s -> {
            server = null;
            ZONES.clear();
            TASKS.clear();
            GOD_HAND_USED.clear();
        });
        ServerTickEvents.END_SERVER_TICK.register(FateEnchantments::tick);

        // Bloodied Spear: quien muere a manos de la Gáe Bolg deja un círculo de sangre
        ServerLivingEntityEvents.AFTER_DEATH.register((entity, source) -> {
            if (!(entity.getWorld() instanceof ServerWorld world) || !(source.getAttacker() instanceof PlayerEntity player)) return;
            if (held(player, FateUBW.GAE_BOLG, BLOODIED_SPEAR) == 0) return;
            bloodCircle(world, entity.getPos(), player);
        });

        // God Hand: con poca vida, la hacha-espada da resistencia y absorción un momento (una vez por minuto)
        ServerLivingEntityEvents.AFTER_DAMAGE.register((entity, source, baseDamage, damageTaken, blocked) -> {
            if (!(entity instanceof PlayerEntity player) || player.isDead() || player.getHealth() > player.getMaxHealth() * 0.3F) return;
            int level = held(player, FateUBW.BERSERKER_AXE_SWORD, GOD_HAND);
            if (level == 0 || server == null) return;
            Integer used = GOD_HAND_USED.get(player.getUuid());
            if (used != null && server.getTicks() - used < GOD_HAND_COOLDOWN) return;
            GOD_HAND_USED.put(player.getUuid(), server.getTicks());
            int duration = 20 * (2 + 2 * level);
            player.addStatusEffect(new StatusEffectInstance(StatusEffects.RESISTANCE, duration, 2));
            player.addStatusEffect(new StatusEffectInstance(StatusEffects.ABSORPTION, duration, 1));
            if (player.getWorld() instanceof ServerWorld world) {
                world.spawnParticles(ParticleTypes.TOTEM_OF_UNDYING, player.getX(), player.getBodyY(0.5), player.getZ(), 30, 0.5, 0.8, 0.5, 0.3);
                world.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.ITEM_TOTEM_USE, SoundCategory.PLAYERS, 0.6F, 0.7F);
            }
        });
    }

    private static void tick(MinecraftServer s) {
        int now = s.getTicks();
        List<Task> due = new ArrayList<>();
        TASKS.removeIf(t -> {
            if (t.due() > now) return false;
            due.add(t);
            return true;
        });
        due.forEach(t -> t.run().run());

        ZONES.removeIf(zone -> {
            if (zone.left()[0]-- <= 0) return true;
            ServerWorld world = zone.world();
            Vec3d c = zone.center();
            for (int i = 0; i < 6; i++) {
                double a = world.random.nextDouble() * Math.PI * 2, r = Math.sqrt(world.random.nextDouble()) * zone.radius();
                world.spawnParticles(zone.particle(), c.x + Math.cos(a) * r, c.y + 0.1, c.z + Math.sin(a) * r, 1, 0.0, 0.05, 0.0, 0.01);
            }
            // Nada más aparecer y luego cada medio segundo
            if (zone.left()[0] % 10 == 9) {
                for (LivingEntity e : world.getEntitiesByClass(LivingEntity.class,
                        new net.minecraft.util.math.Box(c, c).expand(zone.radius(), 1.5, zone.radius()),
                        e -> e != zone.owner() && e.isAlive() && e.getPos().squaredDistanceTo(c.x, e.getY(), c.z) <= zone.radius() * zone.radius()
                                && com.nuwuman.fateubw.Rules.canAffect(zone.owner(), e))) {
                    zone.effect().accept(zone, e);
                }
            }
            return false;
        });
    }

    /** Radiant Blade: llamas en el suelo; el nivel sube el radio y el daño. */
    public static void radiantArea(ServerWorld world, Vec3d center, PlayerEntity owner, int level) {
        float damage = 0.5F + 0.5F * level;
        ZONES.add(new Zone(world, center, 1.5 + 0.5 * level, new int[]{60}, owner, ParticleTypes.FLAME, (zone, e) -> {
            e.setOnFireFor(2);
            e.damage(world.getDamageSources().create(DamageTypes.IN_FIRE, null, owner), damage);
        }));
        world.spawnParticles(ParticleTypes.FLAME, center.x, center.y + 0.2, center.z, 40, 1.0, 0.1, 1.0, 0.05);
        world.playSound(null, center.x, center.y, center.z, SoundEvents.ITEM_FIRECHARGE_USE, SoundCategory.PLAYERS, 1.0F, 0.8F);
    }

    /** Bloodied Spear: círculo de sangre que marchita y frena a quien lo pisa. */
    private static void bloodCircle(ServerWorld world, Vec3d center, PlayerEntity owner) {
        ZONES.add(new Zone(world, center, 2.5, new int[]{120}, owner, BLOOD, (zone, e) -> {
            e.addStatusEffect(new StatusEffectInstance(StatusEffects.WITHER, 40, 0), owner);
            e.addStatusEffect(new StatusEffectInstance(StatusEffects.SLOWNESS, 40, 1), owner);
        }));
        world.playSound(null, center.x, center.y, center.z, SoundEvents.ENTITY_WITHER_AMBIENT, SoundCategory.PLAYERS, 0.5F, 1.5F);
    }
}
