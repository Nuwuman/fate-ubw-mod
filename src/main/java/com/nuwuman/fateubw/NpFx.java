package com.nuwuman.fateubw;

import net.minecraft.block.BlockState;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.particle.BlockStateParticleEffect;
import net.minecraft.particle.DustParticleEffect;
import net.minecraft.particle.ParticleEffect;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import org.joml.Vector3f;

/**
 * Efectos de partículas y sonido de los Noble Phantasm grandes: la preparación (el segundo entre soltar la carga y
 * que salga el ataque) y el estallido al lanzarlo. Lo que se dibuja del propio haz está en los renderers.
 */
public final class NpFx {
    private static final DustParticleEffect GOLD = new DustParticleEffect(new Vector3f(1.0F, 0.85F, 0.35F), 1.6F);
    private static final DustParticleEffect RED = new DustParticleEffect(new Vector3f(0.9F, 0.08F, 0.06F), 1.8F);
    private static final DustParticleEffect DARK_RED = new DustParticleEffect(new Vector3f(0.35F, 0.02F, 0.02F), 2.0F);

    private NpFx() {
    }

    /**
     * Excalibur, preparación: una columna de luz sube de la espada al cielo y motas doradas giran cerrándose sobre ella.
     * progress va de 0 a 1 a lo largo del segundo.
     */
    public static void lightPillar(ServerWorld world, PlayerEntity player, float progress) {
        Vec3d top = player.getEyePos().add(0.0, 0.9, 0.0);
        for (int i = 0; i < 10; i++) {
            double h = world.random.nextDouble() * (6.0 + 30.0 * progress);
            world.spawnParticles(ParticleTypes.END_ROD, top.x, top.y + h, top.z, 1, 0.15, 0.2, 0.15, 0.01);
        }
        world.spawnParticles(GOLD, top.x, top.y + 2.0, top.z, 6, 0.3, 2.0, 0.3, 0.0);
        double radius = 3.5 * (1.0 - progress) + 0.6;
        for (int i = 0; i < 3; i++) {
            double angle = progress * 12.0 + i * MathHelper.TAU / 3;
            world.spawnParticles(GOLD, player.getX() + Math.cos(angle) * radius, player.getY() + 0.3 + progress * 2.5,
                    player.getZ() + Math.sin(angle) * radius, 1, 0.0, 0.0, 0.0, 0.0);
        }
        if (progress == 0.0F) {
            world.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.BLOCK_BEACON_POWER_SELECT, SoundCategory.PLAYERS, 2.5F, 0.7F);
        }
    }

    /**
     * Ea, preparación: un torbellino rojo y negro sube alrededor de Gilgamesh y arranca trozos del suelo hacia él.
     */
    public static void redWhirlwind(ServerWorld world, PlayerEntity player, float progress) {
        for (int arm = 0; arm < 4; arm++) {
            for (int i = 0; i < 4; i++) {
                double h = i * 0.9 + progress * 2.0;
                double angle = progress * 18.0 + arm * MathHelper.TAU / 4 + h * 0.8;
                double radius = 3.8 - progress * 1.6 + h * 0.15;
                world.spawnParticles(arm % 2 == 0 ? RED : DARK_RED, player.getX() + Math.cos(angle) * radius, player.getY() + h,
                        player.getZ() + Math.sin(angle) * radius, 1, 0.0, 0.0, 0.0, 0.0);
            }
        }
        BlockState ground = world.getBlockState(player.getBlockPos().down());
        if (!ground.isAir()) {
            for (int i = 0; i < 4; i++) {
                double angle = world.random.nextDouble() * MathHelper.TAU;
                double dx = Math.cos(angle), dz = Math.sin(angle);
                // Velocidad hacia dentro y hacia arriba (con count 0, los tres números son la dirección)
                world.spawnParticles(new BlockStateParticleEffect(ParticleTypes.BLOCK, ground),
                        player.getX() + dx * 4.5, player.getY() + 0.1, player.getZ() + dz * 4.5, 0, -dx, 0.6, -dz, 0.4);
            }
        }
        if (progress == 0.0F) {
            world.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.ENTITY_WITHER_AMBIENT, SoundCategory.PLAYERS, 1.5F, 0.5F);
            world.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.ITEM_ELYTRA_FLYING, SoundCategory.PLAYERS, 1.0F, 0.6F);
        }
    }

    /** Al lanzarlo: estampido sónico, explosión de luz y una onda que barre el suelo hacia fuera. */
    public static void launchBlast(ServerWorld world, PlayerEntity player, boolean gold) {
        Vec3d at = player.getPos();
        world.playSound(null, at.x, at.y, at.z, SoundEvents.ENTITY_WARDEN_SONIC_BOOM, SoundCategory.PLAYERS, 3.0F, gold ? 0.9F : 0.6F);
        if (!gold) world.playSound(null, at.x, at.y, at.z, SoundEvents.ENTITY_ENDER_DRAGON_GROWL, SoundCategory.PLAYERS, 1.5F, 0.6F);
        Vec3d front = at.add(player.getRotationVec(1.0F).multiply(2.0)).add(0.0, 1.4, 0.0);
        // Excalibur estalla en luz; Ea, en una explosión de verdad
        if (gold) {
            world.spawnParticles(ParticleTypes.FLASH, front.x, front.y, front.z, 3, 0.4, 0.4, 0.4, 0.0);
            world.spawnParticles(ParticleTypes.END_ROD, front.x, front.y, front.z, 60, 0.6, 0.6, 0.6, 0.5);
            world.spawnParticles(GOLD, front.x, front.y, front.z, 40, 1.2, 1.2, 1.2, 0.0);
        } else {
            world.spawnParticles(ParticleTypes.EXPLOSION_EMITTER, front.x, front.y, front.z, 1, 0.0, 0.0, 0.0, 0.0);
            world.spawnParticles(ParticleTypes.FLASH, front.x, front.y, front.z, 2, 0.3, 0.3, 0.3, 0.0);
            world.spawnParticles(RED, front.x, front.y, front.z, 40, 1.2, 1.2, 1.2, 0.0);
        }
        ParticleEffect wave = gold ? ParticleTypes.END_ROD : ParticleTypes.LARGE_SMOKE;
        for (int i = 0; i < 48; i++) {
            double angle = i * MathHelper.TAU / 48;
            double dx = Math.cos(angle), dz = Math.sin(angle);
            world.spawnParticles(wave, at.x + dx, at.y + 0.2, at.z + dz, 0, dx, 0.03, dz, gold ? 0.9 : 0.7);
            if (!gold) world.spawnParticles(ParticleTypes.CLOUD, at.x + dx, at.y + 0.1, at.z + dz, 0, dx, 0.0, dz, 0.6);
        }
    }
}
