package com.nuwuman.fateubw.grail;

import com.nuwuman.fateubw.FateUBW;
import com.nuwuman.fateubw.ability.Mana;
import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.block.BlockState;
import net.minecraft.block.ShapeContext;
import net.minecraft.entity.Entity;
import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Formatting;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.math.random.Random;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.world.BlockView;
import net.minecraft.world.World;

/**
 * Altar de ritual: un círculo grabado en el suelo, reutilizable. Hay que rodearlo con cuatro bloques de oro (norte,
 * sur, este y oeste, a dos bloques) y cuatro de lapislázuli (en las diagonales). Se invoca soltando un catalizador
 * encima (o dándoselo con click derecho): se consume, gasta maná, dibuja el círculo con su rayo y deja el equipo del
 * servant en el centro.
 */
public class SummoningAltarBlock extends Block {
    private static final VoxelShape SHAPE = Block.createCuboidShape(0.0, 0.0, 0.0, 16.0, 1.0, 16.0);
    private static final double SUMMONER_RANGE = 16.0;
    private static final int RING = 2;                       // a cuántos bloques del altar van los de alrededor

    public SummoningAltarBlock(Settings settings) {
        super(settings);
    }

    @Override
    protected VoxelShape getOutlineShape(BlockState state, BlockView world, BlockPos pos, ShapeContext context) {
        return SHAPE;
    }

    // El catalizador que cae sobre el altar lo invoca; quien lo soltó (el más cercano) es el amo
    @Override
    protected void onEntityCollision(BlockState state, World world, BlockPos pos, Entity entity) {
        if (!(world instanceof ServerWorld server) || !(entity instanceof ItemEntity item) || item.age % 10 != 0) return;
        ItemStack stack = item.getStack();
        if (!Servants.isCatalyst(stack)) return;
        PlayerEntity master = world.getClosestPlayer(item.getX(), item.getY(), item.getZ(), SUMMONER_RANGE, false);
        if (master instanceof ServerPlayerEntity player && summon(server, pos, player, stack, item.age % 40 == 0)) {
            if (stack.isEmpty()) item.discard();
        }
    }

    @Override
    protected ActionResult onUse(BlockState state, World world, BlockPos pos, PlayerEntity player, BlockHitResult hit) {
        if (!(world instanceof ServerWorld server) || !(player instanceof ServerPlayerEntity serverPlayer)) return ActionResult.SUCCESS;
        ItemStack held = player.getMainHandStack();
        if (!Servants.isCatalyst(held)) {
            player.sendMessage(Text.translatable("message.fate_ubw.altar_needs_catalyst").formatted(Formatting.GRAY), true);
            return ActionResult.CONSUME;
        }
        summon(server, pos, serverPlayer, held, true);
        return ActionResult.CONSUME;
    }

    /** Los sitios de alrededor que aún no tienen su bloque (oro en los cuatro lados, lapislázuli en las diagonales). */
    public static List<BlockPos> missing(World world, BlockPos altar) {
        List<BlockPos> out = new ArrayList<>();
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                if (dx == 0 && dz == 0) continue;
                BlockPos at = altar.add(dx * RING, 0, dz * RING);
                Block wanted = dx == 0 || dz == 0 ? Blocks.GOLD_BLOCK : Blocks.LAPIS_BLOCK;
                if (!world.getBlockState(at).isOf(wanted)) out.add(at);
            }
        }
        return out;
    }

    /**
     * Empieza el ritual con un catalizador del montón dado (se gasta uno, también en creativo). false si no pudo
     * empezar: falta algún bloque de alrededor, maná o ya hay un ritual. 'tell' dice si avisar al amo.
     */
    private static boolean summon(ServerWorld world, BlockPos pos, ServerPlayerEntity master, ItemStack stack, boolean tell) {
        Vec3d center = Vec3d.ofBottomCenter(pos);
        if (SummoningCircleItem.ritualAt(world, center)) return false;
        List<BlockPos> missing = missing(world, pos);
        if (!missing.isEmpty()) {
            if (tell) {
                master.sendMessage(Text.translatable("message.fate_ubw.altar_incomplete", missing.size()).formatted(Formatting.RED), true);
                for (BlockPos at : missing) {
                    world.spawnParticles(ParticleTypes.SMOKE, at.getX() + 0.5, at.getY() + 0.6, at.getZ() + 0.5, 6, 0.2, 0.2, 0.2, 0.01);
                }
            }
            return false;
        }
        if (!Mana.has(master, FateUBW.SUMMONING_ALTAR_ITEM)) return false;
        Servants.Servant servant = Servants.byCatalyst(stack, world.random);
        Mana.spend(master, FateUBW.SUMMONING_ALTAR_ITEM);
        ItemStack used = stack.copyWithCount(1);
        stack.decrement(1);                                  // el catalizador siempre desaparece
        SummoningCircleItem.startRitual(world, center, servant, master, used);
        return true;
    }

    // En reposo el círculo respira: unas chispas rojas que suben
    @Override
    public void randomDisplayTick(BlockState state, World world, BlockPos pos, Random random) {
        if (random.nextInt(3) != 0) return;
        // Con todos los bloques puestos brilla, y si no, solo respira
        if (missing(world, pos).isEmpty() && random.nextInt(2) == 0) {
            world.addParticle(ParticleTypes.END_ROD, pos.getX() + 0.5 + (random.nextDouble() - 0.5) * 0.8, pos.getY() + 0.2,
                    pos.getZ() + 0.5 + (random.nextDouble() - 0.5) * 0.8, 0.0, 0.05, 0.0);
        }
        double angle = random.nextDouble() * Math.PI * 2;
        world.addParticle(ParticleTypes.ENCHANT, pos.getX() + 0.5 + Math.cos(angle) * 0.45, pos.getY() + 0.1,
                pos.getZ() + 0.5 + Math.sin(angle) * 0.45, 0.0, 0.4, 0.0);
    }
}
