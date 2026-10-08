package com.nuwuman.fateubw.archer;

import com.nuwuman.fateubw.FateUBW;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.decoration.ArmorStandEntity;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.math.random.Random;
import net.minecraft.world.TeleportTarget;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * El Reality Marble: lleva al lanzador y a los seres vivos cercanos a la dimensión Unlimited Blade Works
 * y, al terminar, devuelve a cada uno a donde estaba. Cada activación usa su propia parcela de la dimensión.
 * Las sesiones solo viven en memoria: si el servidor se cierra durante una, los jugadores que sigan dentro
 * vuelven a su punto de respawn al entrar de nuevo.
 */
public final class UnlimitedBladeWorks {
    public static final RegistryKey<World> WORLD = RegistryKey.of(RegistryKeys.WORLD, FateUBW.id("unlimited_blade_works"));
    public static final int DURATION = 20 * 60;
    private static final double PULL_RADIUS = 16.0;
    private static final int GROUND_Y = 5; // encima de las 5 capas del mundo plano
    private static final int SLOT_SPACING = 2000;
    private static final int SWORDS = 260;
    private static final double ARENA_RADIUS = 9.0;
    private static final double FIELD_RADIUS = 48.0;

    private record Return(RegistryKey<World> world, Vec3d pos, float yaw, float pitch) {
    }

    private static final class Session {
        final UUID caster;
        final BlockPos center;
        final Map<UUID, Return> returns = new HashMap<>();
        int ticksLeft = DURATION;
        @Nullable
        UUID core;

        Session(UUID caster, BlockPos center) {
            this.caster = caster;
            this.center = center;
        }
    }

    private static final Map<UUID, Session> SESSIONS = new HashMap<>();
    private static final Set<Integer> DECORATED = new HashSet<>();
    private static int nextSlot;

    private UnlimitedBladeWorks() {
    }

    public static void register() {
        ServerTickEvents.END_SERVER_TICK.register(UnlimitedBladeWorks::tick);
        ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
            SESSIONS.clear();
            DECORATED.clear();
            nextSlot = 0;
        });
    }

    public static boolean isInside(Entity entity) {
        return entity.getWorld().getRegistryKey() == WORLD;
    }

    /** Despliega el Marble. Devuelve false si no se puede (ya hay uno activo, ya está dentro o falta la dimensión). */
    public static boolean open(ServerPlayerEntity caster) {
        if (SESSIONS.containsKey(caster.getUuid()) || isInside(caster)) return false;
        ServerWorld ubw = caster.getServer().getWorld(WORLD);
        if (ubw == null) return false;

        int slot = nextSlot++;
        BlockPos center = new BlockPos(slot * SLOT_SPACING, GROUND_Y, 0);
        if (DECORATED.add(slot)) decorate(ubw, center);
        Session session = new Session(caster.getUuid(), center);

        List<LivingEntity> taken = caster.getServerWorld().getEntitiesByClass(LivingEntity.class,
                caster.getBoundingBox().expand(PULL_RADIUS),
                e -> e != caster && e.isAlive() && !e.isSpectator() && !(e instanceof ArmorStandEntity));
        Vec3d base = Vec3d.ofBottomCenter(center);
        send(session, caster, ubw, base.add(0.0, 0.0, -6.0), 0.0F);
        for (int i = 0; i < taken.size(); i++) {
            // Repartidos en espiral frente al lanzador
            double angle = i * 2.399;
            double r = Math.min(2.0 + i * 0.6, ARENA_RADIUS - 1.0);
            send(session, taken.get(i), ubw, base.add(Math.cos(angle) * r, 0.0, 6.0 + Math.sin(angle) * r * 0.5), 180.0F);
        }

        UbwCoreEntity core = new UbwCoreEntity(FateUBW.UBW_CORE, ubw);
        core.setPosition(base);
        ubw.spawnEntity(core);
        session.core = core.getUuid();
        SESSIONS.put(caster.getUuid(), session);

        ubw.playSound(null, base.x, base.y, base.z, SoundEvents.BLOCK_END_PORTAL_SPAWN, SoundCategory.PLAYERS, 1.0F, 0.6F);
        ubw.playSound(null, base.x, base.y, base.z, SoundEvents.ITEM_FIRECHARGE_USE, SoundCategory.PLAYERS, 2.0F, 0.5F);
        caster.sendMessage(Text.translatable("message.fate_ubw.ubw_open").formatted(Formatting.RED), true);
        return true;
    }

    /** Deshace el Marble del lanzador antes de tiempo. */
    public static void end(ServerPlayerEntity caster) {
        Session session = SESSIONS.remove(caster.getUuid());
        if (session != null) close(caster.getServer(), session);
    }

    private static void send(Session session, LivingEntity entity, ServerWorld to, Vec3d pos, float yaw) {
        session.returns.put(entity.getUuid(), new Return(entity.getWorld().getRegistryKey(), entity.getPos(), entity.getYaw(), entity.getPitch()));
        entity.teleportTo(new TeleportTarget(to, pos, Vec3d.ZERO, yaw, 0.0F, TeleportTarget.NO_OP));
    }

    // Cientos de espadas clavadas alrededor de la arena (una sola vez por parcela)
    private static void decorate(ServerWorld ubw, BlockPos center) {
        Random random = ubw.getRandom();
        for (int i = 0; i < SWORDS; i++) {
            double angle = random.nextDouble() * Math.PI * 2;
            double dist = ARENA_RADIUS + random.nextDouble() * (FIELD_RADIUS - ARENA_RADIUS);
            BlockPos pos = center.add((int) Math.round(Math.cos(angle) * dist), 0, (int) Math.round(Math.sin(angle) * dist));
            ubw.setBlockState(pos, FateUBW.UBW_SWORD.getDefaultState());
        }
    }

    private static void close(MinecraftServer server, Session session) {
        ServerWorld ubw = server.getWorld(WORLD);
        if (ubw == null) return;
        for (Map.Entry<UUID, Return> entry : session.returns.entrySet()) {
            Entity entity = ubw.getEntity(entry.getKey());
            if (entity == null || !entity.isAlive()) continue;
            Return back = entry.getValue();
            ServerWorld world = server.getWorld(back.world());
            if (world == null) world = server.getOverworld();
            entity.teleportTo(new TeleportTarget(world, back.pos(), Vec3d.ZERO, back.yaw(), back.pitch(), TeleportTarget.NO_OP));
        }
        if (session.core != null) {
            Entity core = ubw.getEntity(session.core);
            if (core != null) core.discard();
        }
        ServerPlayerEntity caster = server.getPlayerManager().getPlayer(session.caster);
        if (caster != null) {
            caster.getWorld().playSound(null, caster.getX(), caster.getY(), caster.getZ(), SoundEvents.BLOCK_GLASS_BREAK, SoundCategory.PLAYERS, 1.5F, 0.6F);
        }
    }

    private static void tick(MinecraftServer server) {
        Iterator<Session> it = SESSIONS.values().iterator();
        List<Session> ended = new ArrayList<>();
        while (it.hasNext()) {
            Session session = it.next();
            session.ticksLeft--;
            ServerPlayerEntity caster = server.getPlayerManager().getPlayer(session.caster);
            if (caster == null || !caster.isAlive() || !isInside(caster) || session.ticksLeft <= 0) {
                ended.add(session);
                it.remove();
            } else if (session.ticksLeft == 200) {
                caster.sendMessage(Text.translatable("message.fate_ubw.ubw_ending").formatted(Formatting.GOLD), true);
            }
        }
        for (Session session : ended) close(server, session);

        // Red de seguridad: un jugador dentro sin Marble activo (p. ej. tras reiniciar el servidor) vuelve a su respawn
        ServerWorld ubw = server.getWorld(WORLD);
        if (ubw == null || ubw.getPlayers().isEmpty()) return;
        for (ServerPlayerEntity player : List.copyOf(ubw.getPlayers())) {
            boolean inSession = SESSIONS.values().stream().anyMatch(s -> s.returns.containsKey(player.getUuid()));
            if (!inSession) player.teleportTo(player.getRespawnTarget(true, TeleportTarget.NO_OP));
        }
    }
}
