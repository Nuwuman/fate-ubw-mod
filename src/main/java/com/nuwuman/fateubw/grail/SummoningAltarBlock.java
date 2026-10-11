package com.nuwuman.fateubw.grail;

import com.nuwuman.fateubw.FateUBW;
import com.nuwuman.fateubw.ability.Mana;
import net.minecraft.block.Block;
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
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.world.BlockView;
import net.minecraft.world.World;

/**
 * Altar de ritual: un círculo grabado en el suelo, reutilizable. Se invoca soltando un catalizador encima (o
 * dándoselo con click derecho): gasta maná, dibuja el círculo con su rayo y deja el equipo del servant en el centro.
 */
public class SummoningAltarBlock extends Block {
    private static final VoxelShape SHAPE = Block.createCuboidShape(0.0, 0.0, 0.0, 16.0, 1.0, 16.0);
    private static final double SUMMONER_RANGE = 16.0;

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
        if (master instanceof ServerPlayerEntity player && summon(server, pos, player, stack)) {
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
        summon(server, pos, serverPlayer, held);
        return ActionResult.CONSUME;
    }

    /** Empieza el ritual con un catalizador de 'stack' (se gasta uno). false si no pudo empezar. */
    private static boolean summon(ServerWorld world, BlockPos pos, ServerPlayerEntity master, ItemStack stack) {
        Vec3d center = Vec3d.ofBottomCenter(pos);
        if (SummoningCircleItem.ritualAt(world, center)) return false;
        if (!Mana.has(master, FateUBW.SUMMONING_ALTAR_ITEM)) return false;
        Servants.Servant servant = Servants.byCatalyst(stack, world.random);
        Mana.spend(master, FateUBW.SUMMONING_ALTAR_ITEM);
        if (!master.isCreative()) stack.decrement(1);
        SummoningCircleItem.startRitual(world, center, servant, master);
        return true;
    }

    // En reposo el círculo respira: unas chispas rojas que suben
    @Override
    public void randomDisplayTick(BlockState state, World world, BlockPos pos, Random random) {
        if (random.nextInt(3) != 0) return;
        double angle = random.nextDouble() * Math.PI * 2;
        world.addParticle(ParticleTypes.ENCHANT, pos.getX() + 0.5 + Math.cos(angle) * 0.45, pos.getY() + 0.1,
                pos.getZ() + 0.5 + Math.sin(angle) * 0.45, 0.0, 0.4, 0.0);
    }
}
