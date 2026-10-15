package com.nuwuman.fateubw.structure;

import com.nuwuman.fateubw.FateUBW;
import com.nuwuman.fateubw.npc.HostileServantEntity;
import com.nuwuman.fateubw.npc.ServantNpcs;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.LanternBlock;
import net.minecraft.entity.SpawnReason;
import net.minecraft.loot.LootTable;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.structure.ShiftableStructurePiece;
import net.minecraft.structure.StructureContext;
import net.minecraft.util.math.BlockBox;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.random.Random;
import net.minecraft.world.StructureWorldAccess;
import net.minecraft.world.gen.StructureAccessor;
import net.minecraft.world.gen.chunk.ChunkGenerator;

/**
 * Un santuario de 13x13 construido en código, con un estilo por servant. El servant de guardia aparece en el centro
 * (6, 1, 6) y el cofre (loot_table/chests/{servant}_shrine.json) al fondo. Las ruinas y los adornos al azar salen de
 * un hash de la posición, para que el santuario salga igual aunque cruce varios chunks.
 */
public class ServantShrinePiece extends ShiftableStructurePiece {
    private static final int SIZE = 13, HEIGHT = 10;
    private final String servant;
    private boolean guardSpawned;

    public ServantShrinePiece(String servant, Random random, int x, int z) {
        super(ServantShrines.PIECE, x, 64, z, SIZE, HEIGHT, SIZE, getRandomHorizontalDirection(random));
        this.servant = servant;
    }

    public ServantShrinePiece(NbtCompound nbt) {
        super(ServantShrines.PIECE, nbt);
        this.servant = nbt.getString("Servant");
        this.guardSpawned = nbt.getBoolean("Guard");
    }

    @Override
    protected void writeNbt(StructureContext context, NbtCompound nbt) {
        super.writeNbt(context, nbt);
        nbt.putString("Servant", servant);
        nbt.putBoolean("Guard", guardSpawned);
    }

    @Override
    public void generate(StructureWorldAccess world, StructureAccessor structures, ChunkGenerator generator, Random random,
                         BlockBox box, ChunkPos chunk, BlockPos pivot) {
        if (!adjustToAverageHeight(world, box, 0)) return;
        // Cimientos hasta el suelo, el suelo y aire encima (el estilo pone luego su suelo)
        for (int x = 0; x < SIZE; x++) {
            for (int z = 0; z < SIZE; z++) fillDownwards(world, Blocks.STONE.getDefaultState(), x, -1, z, box);
        }
        fill(world, box, 0, 1, 0, SIZE - 1, HEIGHT - 1, SIZE - 1, Blocks.AIR.getDefaultState());
        int chestY = switch (servant) {
            case "saber" -> saber(world, box);
            case "archer" -> archer(world, box);
            case "lancer" -> lancer(world, box);
            case "rider" -> rider(world, box);
            case "gilgamesh" -> gilgamesh(world, box);
            case "caster" -> caster(world, box);
            case "assassin" -> assassin(world, box);
            default -> berserker(world, box);
        };
        addChest(world, box, random, 6, chestY, 11, RegistryKey.of(RegistryKeys.LOOT_TABLE, FateUBW.id("chests/" + servant + "_shrine")));
        spawnGuard(world, box);
    }

    private void spawnGuard(StructureWorldAccess world, BlockBox box) {
        BlockPos pos = offsetPos(6, 1, 6);
        if (guardSpawned || !box.contains(pos)) return;
        guardSpawned = true;
        HostileServantEntity guard = ServantNpcs.byId(servant).create(world.toServerWorld());
        if (guard == null) return;
        guard.refreshPositionAndAngles(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, 0.0F, 0.0F);
        guard.initialize(world, world.getLocalDifficulty(pos), SpawnReason.STRUCTURE, null);
        guard.guard(pos);
        world.spawnEntityAndPassengers(guard);
    }

    // ---------- estilos: cada uno devuelve a qué altura va el cofre ----------

    // Saber: ruinas de un patio de Camelot, con Caliburn clavada en la piedra
    private int saber(StructureWorldAccess w, BlockBox box) {
        floor(w, box, Blocks.STONE_BRICKS.getDefaultState(), Blocks.MOSSY_STONE_BRICKS.getDefaultState(), Blocks.CRACKED_STONE_BRICKS.getDefaultState());
        ring(w, box, 1, Blocks.STONE_BRICK_WALL.getDefaultState(), 15);
        for (int[] p : corners(2)) {
            fill(w, box, p[0], 1, p[1], p[0], 3, p[1], Blocks.STONE_BRICKS.getDefaultState());
            set(w, box, p[0], 4, p[1], Blocks.CHISELED_STONE_BRICKS.getDefaultState());
            set(w, box, p[0], 5, p[1], Blocks.LANTERN.getDefaultState());
        }
        fill(w, box, 5, 1, 1, 7, 1, 4, Blocks.BLUE_CARPET.getDefaultState());
        set(w, box, 6, 1, 9, Blocks.STONE.getDefaultState());
        set(w, box, 6, 2, 9, Blocks.LIGHTNING_ROD.getDefaultState());
        return 1;
    }

    // Archer: una colina cubierta de espadas clavadas, como su Reality Marble
    private int archer(StructureWorldAccess w, BlockBox box) {
        floor(w, box, Blocks.COARSE_DIRT.getDefaultState(), Blocks.ROOTED_DIRT.getDefaultState(), Blocks.GRAVEL.getDefaultState());
        for (int x = 0; x < SIZE; x++) {
            for (int z = 0; z < SIZE; z++) {
                if (Math.abs(x - 6) <= 1 && Math.abs(z - 6) <= 1 || x == 6 && z >= 10) continue;
                if (chance(x, 1, z, 22)) set(w, box, x, 1, z, Blocks.LIGHTNING_ROD.getDefaultState());
            }
        }
        for (int[] p : corners(1)) {
            fill(w, box, p[0], 1, p[1], p[0], 2, p[1], Blocks.DARK_OAK_FENCE.getDefaultState());
            set(w, box, p[0], 3, p[1], Blocks.LANTERN.getDefaultState());
        }
        return 1;
    }

    // Lancer: un círculo de piedras celta con una hoguera
    private int lancer(StructureWorldAccess w, BlockBox box) {
        floor(w, box, Blocks.PODZOL.getDefaultState(), Blocks.MOSS_BLOCK.getDefaultState(), Blocks.COARSE_DIRT.getDefaultState());
        int[][] stones = {{6, 1}, {10, 2}, {11, 6}, {10, 10}, {2, 10}, {1, 6}, {2, 2}};
        for (int[] s : stones) {
            int top = 2 + hash(s[0], 0, s[1]) % 3;
            for (int y = 1; y <= top; y++) {
                set(w, box, s[0], y, s[1], chance(s[0], y, s[1], 50) ? Blocks.MOSSY_COBBLESTONE.getDefaultState() : Blocks.COBBLESTONE.getDefaultState());
            }
        }
        set(w, box, 6, 1, 3, Blocks.CAMPFIRE.getDefaultState());
        return 1;
    }

    // Rider: el templo de sangre de Blood Fort Andromeda
    private int rider(StructureWorldAccess w, BlockBox box) {
        floor(w, box, Blocks.POLISHED_BLACKSTONE_BRICKS.getDefaultState(), Blocks.CRACKED_POLISHED_BLACKSTONE_BRICKS.getDefaultState(),
                Blocks.POLISHED_BLACKSTONE_BRICKS.getDefaultState());
        for (int y = 1; y <= 4; y++) ring(w, box, y, Blocks.RED_NETHER_BRICKS.getDefaultState(), 0);
        for (int i = 3; i <= 9; i += 3) {                       // ventanas rojas
            for (int y = 2; y <= 3; y++) {
                set(w, box, i, y, 12, Blocks.RED_STAINED_GLASS.getDefaultState());
                set(w, box, 0, y, i, Blocks.RED_STAINED_GLASS.getDefaultState());
                set(w, box, 12, y, i, Blocks.RED_STAINED_GLASS.getDefaultState());
            }
        }
        fill(w, box, 5, 1, 0, 7, 3, 0, Blocks.AIR.getDefaultState());   // puerta
        fill(w, box, 0, 5, 0, 12, 5, 12, Blocks.POLISHED_BLACKSTONE.getDefaultState());
        set(w, box, 6, 5, 6, Blocks.CRYING_OBSIDIAN.getDefaultState());
        for (int[] p : corners(2)) {
            fill(w, box, p[0], 1, p[1], p[0], 3, p[1], Blocks.CRYING_OBSIDIAN.getDefaultState());
            set(w, box, p[0], 4, p[1], Blocks.SHROOMLIGHT.getDefaultState());
        }
        fill(w, box, 5, 1, 5, 7, 1, 7, Blocks.RED_CARPET.getDefaultState());
        return 1;
    }

    // Gilgamesh: la cámara del tesoro, con su trono de oro
    private int gilgamesh(StructureWorldAccess w, BlockBox box) {
        for (int x = 0; x < SIZE; x++) {
            for (int z = 0; z < SIZE; z++) {
                set(w, box, x, 0, z, (x + z) % 2 == 0 ? Blocks.SMOOTH_QUARTZ.getDefaultState() : Blocks.YELLOW_TERRACOTTA.getDefaultState());
            }
        }
        for (int[] p : corners(1)) {
            fill(w, box, p[0], 1, p[1], p[0], 6, p[1], Blocks.QUARTZ_PILLAR.getDefaultState());
            set(w, box, p[0], 7, p[1], Blocks.GOLD_BLOCK.getDefaultState());
            set(w, box, p[0], 8, p[1], Blocks.LANTERN.getDefaultState());
        }
        fill(w, box, 3, 1, 8, 9, 1, 12, Blocks.QUARTZ_BRICKS.getDefaultState());   // estrado
        set(w, box, 6, 2, 9, Blocks.GOLD_BLOCK.getDefaultState());                // trono
        fill(w, box, 5, 2, 10, 7, 4, 10, Blocks.GOLD_BLOCK.getDefaultState());
        set(w, box, 5, 2, 9, Blocks.QUARTZ_SLAB.getDefaultState());
        set(w, box, 7, 2, 9, Blocks.QUARTZ_SLAB.getDefaultState());
        fill(w, box, 5, 1, 1, 7, 1, 7, Blocks.RED_CARPET.getDefaultState());
        return 2;
    }

    // Caster: un pabellón del templo Ryuudou, con libros
    private int caster(StructureWorldAccess w, BlockBox box) {
        floor(w, box, Blocks.SPRUCE_PLANKS.getDefaultState(), Blocks.SPRUCE_PLANKS.getDefaultState(), Blocks.STRIPPED_SPRUCE_LOG.getDefaultState());
        int[][] posts = {{1, 1}, {1, 11}, {11, 1}, {11, 11}, {1, 6}, {11, 6}};
        for (int[] p : posts) fill(w, box, p[0], 1, p[1], p[0], 4, p[1], Blocks.DARK_OAK_LOG.getDefaultState());
        fill(w, box, 0, 5, 0, 12, 5, 12, Blocks.DARK_OAK_PLANKS.getDefaultState());
        fill(w, box, 2, 6, 2, 10, 6, 10, Blocks.DARK_OAK_SLAB.getDefaultState());
        fill(w, box, 2, 1, 12, 10, 2, 12, Blocks.BOOKSHELF.getDefaultState());
        fill(w, box, 4, 1, 4, 8, 1, 8, Blocks.PURPLE_CARPET.getDefaultState());
        BlockState hanging = Blocks.LANTERN.getDefaultState().with(LanternBlock.HANGING, true);
        for (int[] p : corners(3)) set(w, box, p[0], 4, p[1], hanging);
        return 1;
    }

    // Assassin: la puerta del templo (un torii) y un altar pequeño detrás
    private int assassin(StructureWorldAccess w, BlockBox box) {
        floor(w, box, Blocks.GRAVEL.getDefaultState(), Blocks.MOSS_BLOCK.getDefaultState(), Blocks.COARSE_DIRT.getDefaultState());
        fill(w, box, 4, 0, 0, 8, 0, 12, Blocks.STONE_BRICKS.getDefaultState());   // camino
        for (int x : new int[]{2, 10}) fill(w, box, x, 1, 3, x, 5, 3, Blocks.RED_TERRACOTTA.getDefaultState());
        fill(w, box, 1, 5, 3, 11, 5, 3, Blocks.RED_TERRACOTTA.getDefaultState());
        fill(w, box, 0, 6, 3, 12, 6, 3, Blocks.DARK_OAK_PLANKS.getDefaultState());
        for (int x : new int[]{2, 10}) {                                          // faroles de piedra
            set(w, box, x, 1, 7, Blocks.STONE_BRICK_WALL.getDefaultState());
            set(w, box, x, 2, 7, Blocks.LANTERN.getDefaultState());
        }
        for (int[] p : new int[][]{{4, 10}, {8, 10}, {4, 12}, {8, 12}}) fill(w, box, p[0], 1, p[1], p[0], 3, p[1], Blocks.SPRUCE_FENCE.getDefaultState());
        fill(w, box, 3, 4, 9, 9, 4, 12, Blocks.SPRUCE_SLAB.getDefaultState());
        return 1;
    }

    // Berserker: una arena en ruinas, con huesos
    private int berserker(StructureWorldAccess w, BlockBox box) {
        floor(w, box, Blocks.CRACKED_STONE_BRICKS.getDefaultState(), Blocks.STONE_BRICKS.getDefaultState(), Blocks.GRAVEL.getDefaultState());
        for (int i = 0; i < SIZE; i++) {
            for (int[] p : new int[][]{{i, 0}, {i, 12}, {0, i}, {12, i}}) {
                if (p[0] >= 5 && p[0] <= 7 && p[1] == 0) continue;               // entrada
                int top = hash(p[0], 0, p[1]) % 4;
                for (int y = 1; y <= top; y++) {
                    set(w, box, p[0], y, p[1], chance(p[0], y, p[1], 40) ? Blocks.MOSSY_COBBLESTONE.getDefaultState() : Blocks.COBBLESTONE.getDefaultState());
                }
            }
        }
        for (int[] p : corners(3)) {
            int top = 1 + hash(p[0], 1, p[1]) % 4;
            fill(w, box, p[0], 1, p[1], p[0], top, p[1], Blocks.CHISELED_STONE_BRICKS.getDefaultState());
        }
        for (int x = 1; x < SIZE - 1; x++) {
            for (int z = 1; z < SIZE - 1; z++) {
                if (Math.abs(x - 6) <= 1 && Math.abs(z - 6) <= 1) continue;
                if (chance(x, 1, z, 4)) set(w, box, x, 1, z, Blocks.BONE_BLOCK.getDefaultState());
            }
        }
        for (int[] p : corners(1)) set(w, box, p[0], 1, p[1], Blocks.LANTERN.getDefaultState());
        return 1;
    }

    // ---------- utilidades ----------

    private void fill(StructureWorldAccess w, BlockBox box, int x0, int y0, int z0, int x1, int y1, int z1, BlockState state) {
        fillWithOutline(w, box, x0, y0, z0, x1, y1, z1, state, state, false);
    }

    private void set(StructureWorldAccess w, BlockBox box, int x, int y, int z, BlockState state) {
        addBlock(w, state, x, y, z, box);
    }

    // Suelo: casi todo 'main', con algo de 'a' (15 %) y 'b' (10 %) repartidos
    private void floor(StructureWorldAccess w, BlockBox box, BlockState main, BlockState a, BlockState b) {
        for (int x = 0; x < SIZE; x++) {
            for (int z = 0; z < SIZE; z++) {
                int h = hash(x, 0, z) % 100;
                set(w, box, x, 0, z, h < 15 ? a : h < 25 ? b : main);
            }
        }
    }

    // El borde del santuario a una altura, con huecos al azar ('gaps' %) y la entrada (x 5-7 delante) abierta
    private void ring(StructureWorldAccess w, BlockBox box, int y, BlockState state, int gaps) {
        for (int i = 0; i < SIZE; i++) {
            for (int[] p : new int[][]{{i, 0}, {i, SIZE - 1}, {0, i}, {SIZE - 1, i}}) {
                if (p[1] == 0 && p[0] >= 5 && p[0] <= 7 && gaps > 0) continue;
                if (gaps > 0 && chance(p[0], y, p[1], gaps)) continue;
                set(w, box, p[0], y, p[1], state);
            }
        }
    }

    private static int[][] corners(int inset) {
        int far = SIZE - 1 - inset;
        return new int[][]{{inset, inset}, {inset, far}, {far, inset}, {far, far}};
    }

    // Número fijo para cada bloque del santuario (en coordenadas del mundo)
    private int hash(int x, int y, int z) {
        BlockPos p = offsetPos(x, y, z);
        return (int) (MathHelper.hashCode(p.getX(), p.getY(), p.getZ()) >>> 16 & 0x7FFFFFFF);
    }

    private boolean chance(int x, int y, int z, int percent) {
        return hash(x, y, z) % 100 < percent;
    }
}
