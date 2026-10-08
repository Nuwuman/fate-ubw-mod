package com.nuwuman.fateubw.archer;

import com.nuwuman.fateubw.FateUBW;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.datafixer.DataFixTypes;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.decoration.AbstractDecorationEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtHelper;
import net.minecraft.nbt.NbtList;
import net.minecraft.registry.Registries;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Clearable;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.PersistentState;
import net.minecraft.world.TeleportTarget;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * El Reality Marble: convierte una cúpula alrededor del lanzador en Unlimited Blade Works (suelo rojizo, espadas
 * clavadas, una pared invisible y un cielo pintado por {@link com.nuwuman.fateubw.client.UbwCoreRenderer}) y, al
 * terminar, devuelve cada bloque a como estaba, cofres y su contenido incluidos.
 * Los bloques originales se guardan con el mundo: si el servidor se cae durante un Marble, se restauran al arrancar.
 */
public final class UnlimitedBladeWorks {
    /** La dimensión de las versiones anteriores; solo queda para sacar a quien se quedara dentro. */
    private static final RegistryKey<World> LEGACY_WORLD = RegistryKey.of(RegistryKeys.WORLD, FateUBW.id("unlimited_blade_works"));
    public static final int DURATION = 20 * 60;
    /** Radio de la cúpula en bloques. */
    public static final int RADIUS = 32;
    /** Lo que tarda el Marble en extenderse hasta su radio. */
    public static final int SPREAD_TICKS = 30;
    private static final int FLOOR_DEPTH = 3;
    private static final int WALL = RADIUS - 2; // a partir de aquí, barrera
    private static final int ARENA_RADIUS = 6;
    private static final float SWORD_CHANCE = 0.05F;
    // Sin actualizar vecinos (no cae arena ni corre el agua de fuera) y sin soltar objetos
    private static final int FLAGS = Block.NOTIFY_LISTENERS | Block.FORCE_STATE | Block.SKIP_DROPS;

    private static final class Marble {
        final UUID caster;
        final RegistryKey<World> world;
        final BlockPos center;
        final List<BlockPos> positions;
        final BlockState[] original;
        final Map<Integer, NbtCompound> blockEntities = new HashMap<>();
        final List<NbtCompound> hanging = new ArrayList<>();
        int placed;
        int ticksLeft = DURATION;
        @Nullable
        UUID core;

        Marble(UUID caster, ServerWorld world, BlockPos center) {
            this.caster = caster;
            this.world = world.getRegistryKey();
            this.center = center;
            this.positions = positions(world, center);
            this.original = new BlockState[positions.size()];
        }

        NbtCompound toNbt() {
            NbtCompound nbt = new NbtCompound();
            nbt.putUuid("caster", caster);
            nbt.putString("world", world.getValue().toString());
            nbt.putLong("center", center.asLong());
            Map<BlockState, Integer> palette = new HashMap<>();
            NbtList paletteNbt = new NbtList();
            int[] states = new int[placed];
            for (int i = 0; i < placed; i++) {
                states[i] = palette.computeIfAbsent(original[i], s -> {
                    paletteNbt.add(NbtHelper.fromBlockState(s));
                    return paletteNbt.size() - 1;
                });
            }
            nbt.put("palette", paletteNbt);
            nbt.putIntArray("states", states);
            NbtList bes = new NbtList();
            blockEntities.forEach((i, data) -> {
                NbtCompound entry = new NbtCompound();
                entry.putInt("i", i);
                entry.put("data", data);
                bes.add(entry);
            });
            nbt.put("block_entities", bes);
            NbtList hangingNbt = new NbtList();
            hangingNbt.addAll(hanging);
            nbt.put("hanging", hangingNbt);
            return nbt;
        }

        static Marble fromNbt(ServerWorld world, NbtCompound nbt) {
            Marble m = new Marble(nbt.getUuid("caster"), world, BlockPos.fromLong(nbt.getLong("center")));
            NbtList paletteNbt = nbt.getList("palette", NbtElement.COMPOUND_TYPE);
            int[] states = nbt.getIntArray("states");
            m.placed = Math.min(states.length, m.original.length);
            for (int i = 0; i < m.placed; i++) {
                m.original[i] = NbtHelper.toBlockState(Registries.BLOCK.getReadOnlyWrapper(), paletteNbt.getCompound(states[i]));
            }
            for (NbtElement e : nbt.getList("block_entities", NbtElement.COMPOUND_TYPE)) {
                NbtCompound entry = (NbtCompound) e;
                m.blockEntities.put(entry.getInt("i"), entry.getCompound("data"));
            }
            for (NbtElement e : nbt.getList("hanging", NbtElement.COMPOUND_TYPE)) m.hanging.add((NbtCompound) e);
            return m;
        }
    }

    /** Guarda los Marbles activos con el mundo para poder restaurar los bloques tras una caída del servidor. */
    private static final class Saved extends PersistentState {
        NbtList pending = new NbtList();

        @Override
        public NbtCompound writeNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup registries) {
            NbtList list = new NbtList();
            for (Marble m : MARBLES.values()) list.add(m.toNbt());
            for (Marble m : CLOSING) list.add(m.toNbt());
            nbt.put("marbles", list);
            return nbt;
        }

        static Saved load(NbtCompound nbt, RegistryWrapper.WrapperLookup registries) {
            Saved saved = new Saved();
            saved.pending = nbt.getList("marbles", NbtElement.COMPOUND_TYPE);
            return saved;
        }
    }

    private static final PersistentState.Type<Saved> SAVED_TYPE = new PersistentState.Type<>(Saved::new, Saved::load,
            DataFixTypes.SAVED_DATA_COMMAND_STORAGE); // null rompe la lectura en vanilla; con la versión actual no cambia nada
    private static final Map<UUID, Marble> MARBLES = new HashMap<>();
    // Marbles que se están deshaciendo: el mundo vuelve de fuera adentro en RESTORE_TICKS
    private static final List<Marble> CLOSING = new ArrayList<>();
    private static final int RESTORE_TICKS = 20;
    @Nullable
    private static Saved saved;

    private UnlimitedBladeWorks() {
    }

    public static void register() {
        ServerTickEvents.END_SERVER_TICK.register(UnlimitedBladeWorks::tick);
        ServerLifecycleEvents.SERVER_STARTED.register(server -> {
            saved = server.getOverworld().getPersistentStateManager().getOrCreate(SAVED_TYPE, "fate_ubw_marbles");
            for (NbtElement e : saved.pending) {
                NbtCompound nbt = (NbtCompound) e;
                ServerWorld world = server.getWorld(RegistryKey.of(RegistryKeys.WORLD, Identifier.of(nbt.getString("world"))));
                if (world != null) restore(world, Marble.fromNbt(world, nbt), Integer.MAX_VALUE);
            }
            saved.pending = new NbtList();
            saved.markDirty();
        });
        // Al cerrar, todo vuelve a su sitio de golpe antes de guardar el mundo
        ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
            for (Marble m : MARBLES.values()) close(server, m);
            MARBLES.clear();
            for (Marble m : CLOSING) {
                ServerWorld world = server.getWorld(m.world);
                if (world != null) restore(world, m, Integer.MAX_VALUE);
            }
            CLOSING.clear();
            if (saved != null) saved.markDirty();
        });
        ServerLifecycleEvents.SERVER_STOPPED.register(server -> saved = null);
    }

    // Distancia² al centro: esfera por encima del suelo, cilindro por debajo
    private static int dist2(BlockPos center, BlockPos pos) {
        int dx = pos.getX() - center.getX(), dy = pos.getY() - center.getY(), dz = pos.getZ() - center.getZ();
        return dx * dx + dz * dz + (dy > 0 ? dy * dy : 0);
    }

    private static boolean contains(Marble m, BlockPos pos) {
        return pos.getY() >= m.center.getY() - FLOOR_DEPTH && dist2(m.center, pos) < WALL * WALL;
    }

    // Todos los bloques que toca el Marble, de dentro afuera (así se extiende como una onda)
    private static List<BlockPos> positions(ServerWorld world, BlockPos center) {
        List<BlockPos> list = new ArrayList<>();
        for (int dy = -FLOOR_DEPTH; dy < RADIUS; dy++) {
            for (int dx = -RADIUS; dx <= RADIUS; dx++) {
                for (int dz = -RADIUS; dz <= RADIUS; dz++) {
                    BlockPos pos = center.add(dx, dy, dz);
                    if (dist2(center, pos) < RADIUS * RADIUS && !world.isOutOfHeightLimit(pos)) list.add(pos);
                }
            }
        }
        list.sort(Comparator.comparingInt(pos -> dist2(center, pos)));
        return list;
    }

    private static BlockState target(ServerWorld world, BlockPos center, BlockPos pos) {
        int dx = pos.getX() - center.getX(), dy = pos.getY() - center.getY(), dz = pos.getZ() - center.getZ();
        int d2 = dist2(center, pos);
        if (dy == -1) return Blocks.RED_SAND.getDefaultState();
        if (dy < -1) return Blocks.TERRACOTTA.getDefaultState();
        if (d2 >= WALL * WALL) return Blocks.BARRIER.getDefaultState();
        if (dy == 0 && d2 > ARENA_RADIUS * ARENA_RADIUS && world.random.nextFloat() < SWORD_CHANCE) {
            return FateUBW.UBW_SWORD.getDefaultState();
        }
        // Luz invisible para que el páramo no quede a oscuras de noche
        if (dy == 4 && Math.floorMod(dx, 7) == 0 && Math.floorMod(dz, 7) == 0) return Blocks.LIGHT.getDefaultState();
        return Blocks.AIR.getDefaultState();
    }

    public static boolean isInside(Entity entity) {
        for (Marble m : MARBLES.values()) {
            if (m.world == entity.getWorld().getRegistryKey() && contains(m, entity.getBlockPos())) return true;
        }
        return false;
    }

    /** Despliega el Marble. Devuelve false si no se puede (ya tiene uno, está dentro de otro o se solaparían). */
    public static boolean open(ServerPlayerEntity caster) {
        if (MARBLES.containsKey(caster.getUuid()) || isInside(caster)) return false;
        ServerWorld world = caster.getServerWorld();
        BlockPos center = caster.getBlockPos();
        List<Marble> others = new ArrayList<>(MARBLES.values());
        others.addAll(CLOSING);
        for (Marble other : others) {
            if (other.world == world.getRegistryKey() && other.center.getSquaredDistance(center) < 4.0 * RADIUS * RADIUS) return false;
        }
        Marble m = new Marble(caster.getUuid(), world, center);

        // Cuadros y pinturas se guardan aparte antes de que desaparezca su pared
        for (AbstractDecorationEntity e : world.getEntitiesByClass(AbstractDecorationEntity.class,
                new Box(center).expand(RADIUS), e -> dist2(center, e.getBlockPos()) < RADIUS * RADIUS)) {
            NbtCompound nbt = new NbtCompound();
            if (e.saveSelfNbt(nbt)) m.hanging.add(nbt);
            e.discard();
        }

        Vec3d base = Vec3d.ofBottomCenter(center);
        UbwCoreEntity core = new UbwCoreEntity(FateUBW.UBW_CORE, world);
        core.setPosition(base);
        world.spawnEntity(core);
        m.core = core.getUuid();
        MARBLES.put(caster.getUuid(), m);

        world.playSound(null, base.x, base.y, base.z, SoundEvents.BLOCK_END_PORTAL_SPAWN, SoundCategory.PLAYERS, 1.0F, 0.6F);
        world.playSound(null, base.x, base.y, base.z, SoundEvents.ITEM_FIRECHARGE_USE, SoundCategory.PLAYERS, 2.0F, 0.5F);
        caster.sendMessage(Text.translatable("message.fate_ubw.ubw_open").formatted(Formatting.RED), true);
        return true;
    }

    /** Deshace el Marble del lanzador antes de tiempo. */
    public static void end(ServerPlayerEntity caster) {
        Marble m = MARBLES.remove(caster.getUuid());
        if (m != null) close(caster.getServer(), m);
    }

    // Transforma los bloques que ya ha alcanzado la onda, guardando antes cómo eran
    private static void spread(ServerWorld world, Marble m) {
        int age = DURATION - m.ticksLeft + 1;
        double reach = RADIUS * Math.min(1.0, age / (double) SPREAD_TICKS);
        while (m.placed < m.positions.size() && dist2(m.center, m.positions.get(m.placed)) <= reach * reach) {
            int i = m.placed++;
            BlockPos pos = m.positions.get(i);
            m.original[i] = world.getBlockState(pos);
            BlockEntity be = world.getBlockEntity(pos);
            if (be != null) {
                m.blockEntities.put(i, be.createNbtWithIdentifyingData(world.getRegistryManager()));
                Clearable.clear(be); // que el cofre no suelte su contenido al quitarlo
            }
            world.setBlockState(pos, target(world, m.center, pos), FLAGS);
        }
    }

    private static void close(MinecraftServer server, Marble m) {
        ServerWorld world = server.getWorld(m.world);
        if (world == null) return;
        if (m.core != null) {
            Entity core = world.getEntity(m.core);
            if (core != null) core.discard();
        }
        Vec3d base = Vec3d.ofBottomCenter(m.center);
        world.playSound(null, base.x, base.y, base.z, SoundEvents.BLOCK_GLASS_BREAK, SoundCategory.PLAYERS, 1.5F, 0.6F);
        CLOSING.add(m);
    }

    /** Devuelve hasta {@code limit} bloques a como estaban, de fuera adentro. True cuando ha terminado. */
    private static boolean restore(ServerWorld world, Marble m, int limit) {
        for (int n = 0; n < limit && m.placed > 0; n++) {
            int i = --m.placed;
            if (m.original[i] == null) continue;
            BlockPos pos = m.positions.get(i);
            world.setBlockState(pos, m.original[i], FLAGS);
            NbtCompound data = m.blockEntities.get(i);
            BlockEntity be = data != null ? world.getBlockEntity(pos) : null;
            if (be != null) {
                be.read(data, world.getRegistryManager());
                be.markDirty();
            }
        }
        if (m.placed > 0) return false;
        for (NbtCompound nbt : m.hanging) EntityType.getEntityFromNbt(nbt, world).ifPresent(world::spawnEntity);

        // Quien haya quedado dentro de un bloque (donde antes había una colina) sube hasta tener sitio
        for (LivingEntity e : world.getEntitiesByClass(LivingEntity.class, new Box(m.center).expand(RADIUS), Entity::isAlive)) {
            double y = e.getY();
            for (int n = 0; n < RADIUS && !world.isSpaceEmpty(e, e.getBoundingBox().offset(0.0, y - e.getY(), 0.0)); n++) {
                y = Math.floor(y) + 1.0;
            }
            if (y != e.getY()) e.requestTeleport(e.getX(), y, e.getZ());
        }
        return true;
    }

    private static void tick(MinecraftServer server) {
        Iterator<Marble> it = MARBLES.values().iterator();
        List<Marble> ended = new ArrayList<>();
        while (it.hasNext()) {
            Marble m = it.next();
            ServerWorld world = server.getWorld(m.world);
            if (world != null && m.placed < m.positions.size()) spread(world, m);
            m.ticksLeft--;
            ServerPlayerEntity caster = server.getPlayerManager().getPlayer(m.caster);
            if (caster == null || !caster.isAlive() || m.ticksLeft <= 0
                    || caster.getWorld().getRegistryKey() != m.world || !contains(m, caster.getBlockPos())) {
                ended.add(m);
                it.remove();
            } else if (m.ticksLeft == 200) {
                caster.sendMessage(Text.translatable("message.fate_ubw.ubw_ending").formatted(Formatting.GOLD), true);
            }
        }
        for (Marble m : ended) close(server, m);
        boolean restored = CLOSING.removeIf(m -> {
            ServerWorld world = server.getWorld(m.world);
            return world == null || restore(world, m, m.positions.size() / RESTORE_TICKS + 1);
        });
        if (saved != null && (!MARBLES.isEmpty() || !CLOSING.isEmpty() || restored)) saved.markDirty();

        // Quien se quedara en la dimensión de versiones anteriores vuelve a su respawn
        ServerWorld legacy = server.getWorld(LEGACY_WORLD);
        if (legacy == null || legacy.getPlayers().isEmpty()) return;
        for (ServerPlayerEntity player : List.copyOf(legacy.getPlayers())) {
            player.teleportTo(player.getRespawnTarget(true, TeleportTarget.NO_OP));
        }
    }
}
