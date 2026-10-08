package com.nuwuman.fateubw.gilgamesh;

import com.nuwuman.fateubw.FateUBW;
import com.nuwuman.fateubw.ServantArmorItem;
import com.nuwuman.fateubw.lancer.GaeBolgItem;
import net.minecraft.block.Blocks;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ArmorItem;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.tooltip.TooltipType;
import net.minecraft.particle.BlockStateParticleEffect;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

import java.util.List;

/**
 * Armadura dorada del Rey de los Héroes con escarcelas animadas. Conjunto completo: Regla de Oro (Suerte II, Resistencia I).
 * Habilidades: Gate of Babylon y las Cadenas del Cielo (Enkidu).
 */
public class GilgameshArmorItem extends ServantArmorItem {
    public static final int GATE_COOLDOWN = 20 * 12;
    public static final int ENKIDU_COOLDOWN = 20 * 25;
    private static final int ENKIDU_TICKS = 100;
    private static final BlockStateParticleEffect CHAIN = new BlockStateParticleEffect(ParticleTypes.BLOCK, Blocks.CHAIN.getDefaultState());

    public GilgameshArmorItem(ArmorItem.Type type, Item.Settings settings) {
        super(FateUBW.GILGAMESH_MATERIAL, type, settings, "gilgamesh_armor");
    }

    @Override
    public void inventoryTick(ItemStack stack, World world, Entity entity, int slot, boolean selected) {
        if (getType() != Type.CHESTPLATE || world.isClient || !(entity instanceof PlayerEntity player)) return;
        if (player.age % 40 != 0 || player.getEquippedStack(EquipmentSlot.CHEST) != stack) return;
        if (!wearsSet(player, FateUBW.GILGAMESH_CHESTPLATE, FateUBW.GILGAMESH_LEGGINGS, FateUBW.GILGAMESH_BOOTS)) return;
        player.addStatusEffect(new StatusEffectInstance(StatusEffects.LUCK, 60, 1, true, false));
        player.addStatusEffect(new StatusEffectInstance(StatusEffects.RESISTANCE, 60, 0, true, false));
    }

    public static boolean gateOfBabylon(ServerPlayerEntity player) {
        BabylonPortalEntity.openGate(player.getServerWorld(), player);
        com.nuwuman.fateubw.Voices.say(player.getWorld(), player, "gate_of_babylon");
        return true;
    }

    /** Enkidu, las Cadenas del Cielo: atan a quien miras (32 bloques) y no le dejan moverse durante 5 s. */
    public static boolean enkidu(ServerPlayerEntity player) {
        ServerWorld world = player.getServerWorld();
        LivingEntity target = GaeBolgItem.findTarget(world, player, 32.0, 0.95);
        if (target == null) {
            player.sendMessage(Text.translatable("message.fate_ubw.no_target").formatted(Formatting.GRAY), true);
            return false;
        }
        target.stopRiding();
        target.addStatusEffect(new StatusEffectInstance(StatusEffects.SLOWNESS, ENKIDU_TICKS, 9));
        target.addStatusEffect(new StatusEffectInstance(StatusEffects.MINING_FATIGUE, ENKIDU_TICKS, 3));
        target.addStatusEffect(new StatusEffectInstance(StatusEffects.WEAKNESS, ENKIDU_TICKS, 2));
        target.setVelocity(Vec3d.ZERO);
        target.velocityModified = true;
        target.damage(world.getDamageSources().magic(), 4.0F);

        // Cadenas desde los portales hasta el objetivo, y anillos de cadena a su alrededor
        Vec3d from = player.getPos().add(0.0, 2.5, 0.0), to = target.getBoundingBox().getCenter();
        for (int i = 0; i <= 24; i++) {
            Vec3d p = from.lerp(to, i / 24.0);
            world.spawnParticles(CHAIN, p.x, p.y, p.z, 2, 0.05, 0.05, 0.05, 0.0);
        }
        for (int ring = 0; ring < 3; ring++) {
            double y = target.getY() + target.getHeight() * (0.25 + ring * 0.3);
            for (int i = 0; i < 16; i++) {
                double angle = i * Math.PI / 8;
                world.spawnParticles(CHAIN, target.getX() + Math.cos(angle) * 0.8, y, target.getZ() + Math.sin(angle) * 0.8, 1, 0.0, 0.0, 0.0, 0.0);
            }
        }
        world.spawnParticles(ParticleTypes.END_ROD, from.x, from.y, from.z, 10, 0.3, 0.3, 0.3, 0.05);
        world.playSound(null, target.getX(), target.getY(), target.getZ(), SoundEvents.BLOCK_CHAIN_PLACE, SoundCategory.PLAYERS, 2.0F, 0.6F);
        world.playSound(null, target.getX(), target.getY(), target.getZ(), SoundEvents.BLOCK_CHAIN_HIT, SoundCategory.PLAYERS, 2.0F, 0.8F);
        world.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.BLOCK_END_PORTAL_FRAME_FILL, SoundCategory.PLAYERS, 1.0F, 1.2F);
        return true;
    }

    @Override
    public void appendTooltip(ItemStack stack, Item.TooltipContext context, List<Text> tooltip, TooltipType type) {
        tooltip.add(Text.translatable("item.fate_ubw.gilgamesh_armor.tooltip.set").formatted(Formatting.GOLD));
        tooltip.add(Text.translatable("item.fate_ubw.gilgamesh_armor.tooltip.abilities").formatted(Formatting.LIGHT_PURPLE));
    }
}
