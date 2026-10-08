package com.nuwuman.fateubw.archer;

import com.nuwuman.fateubw.FateUBW;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import com.nuwuman.fateubw.gilgamesh.BabylonPortalEntity;
import com.nuwuman.fateubw.gilgamesh.BabylonWeaponEntity;
import net.minecraft.entity.decoration.ArmorStandEntity;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.BlockItem;
import net.minecraft.item.BucketItem;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.particle.DustParticleEffect;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.util.ActionResult;
import org.joml.Vector3f;
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
    public static final int MARBLE_COOLDOWN = 20 * 120;
    public static final int BARRAGE_COOLDOWN = 20;
    public static final int CHANT_TICKS = 60;
    private static final int BARRAGE_SWORDS = 16;
    private static final int RAIN_INTERVAL = 30;
    private static final DustParticleEffect EMBER = new DustParticleEffect(new Vector3f(1.0F, 0.45F, 0.1F), 1.5F);
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
    // Quien está recitando el aria (habilidad del conjunto) y cuántos ticks lleva
    private static final Map<UUID, Integer> CHANTS = new HashMap<>();
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
        ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
            saved = null;
            CHANTS.clear();
        });

        // Dentro del Marble no se rompen ni se ponen bloques (el mundo de fuera está guardado debajo)
        PlayerBlockBreakEvents.BEFORE.register((world, player, pos, state, blockEntity) -> !protects(world, pos));
        UseBlockCallback.EVENT.register((player, world, hand, hit) -> {
            ItemStack stack = player.getStackInHand(hand);
            boolean places = stack.getItem() instanceof BlockItem || stack.getItem() instanceof BucketItem
                    || stack.isOf(Items.FLINT_AND_STEEL) || stack.isOf(Items.FIRE_CHARGE);
            if (!world.isClient && places && (protects(world, hit.getBlockPos()) || protects(world, hit.getBlockPos().offset(hit.getSide())))) {
                return ActionResult.FAIL;
            }
            return ActionResult.PASS;
        });
    }

    public static boolean protects(World world, BlockPos pos) {
        for (Marble m : MARBLES.values()) {
            if (m.world == world.getRegistryKey() && pos.getY() >= m.center.getY() - FLOOR_DEPTH
                    && dist2(m.center, pos) < RADIUS * RADIUS) return true;
        }
        return false;
    }

    /** Dentro de un Marble; en el cliente, donde no se conocen los Marbles, por la cercanía de su núcleo. */
    public static boolean isInsideAny(PlayerEntity player) {
        if (!player.getWorld().isClient) return isInside(player);
        return !player.getWorld().getEntitiesByClass(UbwCoreEntity.class, player.getBoundingBox().expand(WALL), e -> true).isEmpty();
    }

    /**
     * La habilidad del conjunto de Archer. Fuera: empieza el aria (3 s) y al acabar despliega el Marble.
     * Dentro: ráfaga de espadas; agachado, deshace el Marble propio.
     */
    public static boolean useAbility(ServerPlayerEntity player) {
        if (isInside(player)) {
            if (player.isSneaking()) {
                end(player);
                return false;
            }
            barrage(player.getServerWorld(), player);
            return true;
        }
        if (!CHANTS.containsKey(player.getUuid())) {
            CHANTS.put(player.getUuid(), 0);
            player.sendMessage(Text.translatable("message.fate_ubw.ubw_chant").formatted(Formatting.RED, Formatting.ITALIC), true);
            com.nuwuman.fateubw.Voices.say(player.getWorld(), player, "ubw_chant");
        }
        return false;
    }

    // Un anillo de fuego que se extiende por el suelo mientras se recita el aria
    public static void chantEffects(ServerWorld world, LivingEntity user, int charge) {
        double radius = (RADIUS - 2) * Math.min(1.0, charge / (double) CHANT_TICKS);
        for (int i = 0; i < 64; i++) {
            double angle = i * Math.PI * 2 / 64 + charge * 0.05;
            world.spawnParticles(ParticleTypes.FLAME, user.getX() + Math.cos(angle) * radius, user.getY() + 0.1,
                    user.getZ() + Math.sin(angle) * radius, 1, 0.0, 0.05, 0.0, 0.01);
        }
        world.spawnParticles(EMBER, user.getX(), user.getBodyY(0.5), user.getZ(), 2, 0.6, 0.8, 0.6, 0.0);
        if (charge % 20 == 0 && charge > 0 && charge <= CHANT_TICKS) {
            world.playSound(null, user.getX(), user.getY(), user.getZ(), SoundEvents.ITEM_FIRECHARGE_USE, SoundCategory.PLAYERS, 1.0F, 0.6F + charge / 100.0F);
        }
    }

    private static final ItemStack[] SWORDS = {new ItemStack(FateUBW.KANSHOU), new ItemStack(FateUBW.BAKUYA),
            new ItemStack(FateUBW.SWORD_ARROW), new ItemStack(FateUBW.CALADBOLG), new ItemStack(Items.IRON_SWORD),
            new ItemStack(Items.DIAMOND_SWORD)};

    private static ItemStack randomSword(ServerWorld world) {
        return SWORDS[world.random.nextInt(SWORDS.length)].copy();
    }

    /** Espadas que surgen del suelo detrás del lanzador y salen disparadas hacia lo que mira. */
    public static void barrage(ServerWorld world, PlayerEntity player) {
        Vec3d target = BabylonPortalEntity.aimPoint(world, player, 48.0);
        Vec3d forward = Vec3d.fromPolar(0.0F, player.getYaw());
        Vec3d right = forward.crossProduct(new Vec3d(0.0, 1.0, 0.0));
        for (int i = 0; i < BARRAGE_SWORDS; i++) {
            Vec3d from = player.getPos()
                    .add(right.multiply((i - (BARRAGE_SWORDS - 1) / 2.0) * 0.7))
                    .subtract(forward.multiply(1.5 + world.random.nextDouble()))
                    .add(0.0, 0.4 + world.random.nextDouble() * 1.4, 0.0);
            BabylonWeaponEntity sword = new BabylonWeaponEntity(world, player, randomSword(world));
            sword.setPosition(from);
            Vec3d dir = target.subtract(from).normalize();
            sword.setVelocity(dir.x, dir.y, dir.z, 2.8F, 1.5F);
            world.spawnEntity(sword);
            world.spawnParticles(ParticleTypes.CRIT, from.x, from.y, from.z, 4, 0.1, 0.1, 0.1, 0.1);
        }
        world.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.ENTITY_PLAYER_ATTACK_SWEEP, SoundCategory.PLAYERS, 1.5F, 0.6F);
        world.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.ITEM_TRIDENT_THROW.value(), SoundCategory.PLAYERS, 1.0F, 0.8F);
    }

    // El Marble es el mundo de EMIYA: él se fortalece y sobre todos los demás llueven espadas
    private static void empower(ServerWorld world, Marble m, ServerPlayerEntity caster, int age) {
        if (age % 40 == 0) {
            caster.addStatusEffect(new StatusEffectInstance(StatusEffects.STRENGTH, 60, 1, true, false));
            caster.addStatusEffect(new StatusEffectInstance(StatusEffects.RESISTANCE, 60, 0, true, false));
            caster.addStatusEffect(new StatusEffectInstance(StatusEffects.SPEED, 60, 0, true, false));
            caster.addStatusEffect(new StatusEffectInstance(StatusEffects.REGENERATION, 60, 0, true, false));
        }
        if (age < SPREAD_TICKS || age % RAIN_INTERVAL != 0) return;
        for (LivingEntity target : world.getEntitiesByClass(LivingEntity.class, new Box(m.center).expand(RADIUS),
                e -> e != caster && e.isAlive() && !e.isSpectator() && !(e instanceof ArmorStandEntity) && com.nuwuman.fateubw.Rules.canAffect(caster, e)
                        && !(e instanceof net.minecraft.entity.passive.TameableEntity pet && pet.isOwner(caster))
                        && !(e instanceof PlayerEntity p && p.isCreative()) && contains(m, e.getBlockPos()))) {
            for (int i = 0; i < 3; i++) {
                Vec3d from = target.getPos().add(world.random.nextDouble() * 4 - 2, 7 + world.random.nextDouble() * 3,
                        world.random.nextDouble() * 4 - 2);
                Vec3d dir = target.getBoundingBox().getCenter().subtract(from).normalize();
                BabylonWeaponEntity sword = new BabylonWeaponEntity(world, caster, randomSword(world));
                sword.setPosition(from);
                sword.setVelocity(dir.x, dir.y, dir.z, 2.2F, 2.0F);
                world.spawnEntity(sword);
            }
        }
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
        com.nuwuman.fateubw.Rules.cooldown(caster, FateUBW.UBW_COOLDOWN, MARBLE_COOLDOWN);
        com.nuwuman.fateubw.ability.Mana.spend(caster, FateUBW.UBW_COOLDOWN);
        com.nuwuman.fateubw.Voices.say(world, caster, "unlimited_blade_works");
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
            } else {
                if (m.ticksLeft == 200) caster.sendMessage(Text.translatable("message.fate_ubw.ubw_ending").formatted(Formatting.GOLD), true);
                if (world != null) empower(world, m, caster, DURATION - m.ticksLeft);
            }
        }

        // El aria: se cancela si se quita la ropa o muere; al terminar, se despliega el Marble
        CHANTS.entrySet().removeIf(entry -> {
            ServerPlayerEntity player = server.getPlayerManager().getPlayer(entry.getKey());
            if (player == null || !player.isAlive() || !com.nuwuman.fateubw.ServantArmorItem.wearsSet(player,
                    FateUBW.ARCHER_CHESTPLATE, FateUBW.ARCHER_LEGGINGS, FateUBW.ARCHER_BOOTS)) return true;
            int charge = entry.getValue() + 1;
            entry.setValue(charge);
            chantEffects(player.getServerWorld(), player, charge);
            if (charge < CHANT_TICKS) return false;
            // Puede fallar si se solaparía con otro Marble: que no se quede el aria en silencio
            if (!open(player)) player.sendMessage(Text.translatable("message.fate_ubw.ubw_failed").formatted(Formatting.GRAY), true);
            return true;
        });
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
