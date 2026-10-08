package com.nuwuman.fateubw.berserker;

import com.nuwuman.fateubw.FateUBW;
import com.nuwuman.fateubw.ServantArmorItem;
import com.mojang.serialization.Codec;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentSyncPredicate;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ArmorItem;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.tooltip.TooltipType;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.registry.tag.DamageTypeTags;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.world.World;

import java.util.List;

/**
 * Taparrabos, brazales y grebas de Heracles con faldones animados. Conjunto completo: God Hand.
 * Once vidas de más (resucita con la vida llena; recupera una cada 2 minutos) y los golpes de menos de 4 de daño
 * no le hacen nada. Habilidad: Locura Mejorada.
 */
public class BerserkerArmorItem extends ServantArmorItem {
    public static final int MAX_LIVES = 11;
    public static final int MAD_ENHANCEMENT_COOLDOWN = 20 * 60;
    private static final int LIFE_REGEN_TICKS = 20 * 120;
    private static final float WEAK_HIT = 4.0F;
    /** Vidas de God Hand que le quedan; se sincroniza con su cliente para el HUD. */
    public static final AttachmentType<Integer> LIVES = AttachmentRegistry.<Integer>builder()
            .persistent(Codec.INT).copyOnDeath().initializer(() -> MAX_LIVES)
            .syncWith(PacketCodecs.VAR_INT.cast(), AttachmentSyncPredicate.targetOnly())
            .buildAndRegister(FateUBW.id("god_hand_lives"));

    public BerserkerArmorItem(ArmorItem.Type type, Item.Settings settings) {
        super(FateUBW.BERSERKER_MATERIAL, type, settings, "berserker_armor");
    }

    public static boolean fullSet(LivingEntity entity) {
        return entity instanceof PlayerEntity player
                && wearsSet(player, FateUBW.BERSERKER_CHESTPLATE, FateUBW.BERSERKER_LEGGINGS, FateUBW.BERSERKER_BOOTS);
    }

    public static int lives(PlayerEntity player) {
        Integer lives = player.getAttached(LIVES);
        return lives == null ? MAX_LIVES : lives;
    }

    // Recupera una vida cada 2 minutos con el conjunto puesto
    @Override
    public void inventoryTick(ItemStack stack, World world, Entity entity, int slot, boolean selected) {
        if (getType() != Type.CHESTPLATE || world.isClient || !(entity instanceof PlayerEntity player)) return;
        if (player.age % LIFE_REGEN_TICKS != 0 || player.getEquippedStack(EquipmentSlot.CHEST) != stack || !fullSet(player)) return;
        if (lives(player) < MAX_LIVES) player.setAttached(LIVES, lives(player) + 1);
    }

    /** God Hand: los ataques débiles no atraviesan su cuerpo. */
    public static boolean allowDamage(LivingEntity entity, DamageSource source, float amount) {
        return !(fullSet(entity) && amount < WEAK_HIT && !source.isIn(DamageTypeTags.BYPASSES_INVULNERABILITY));
    }

    /** God Hand: al morir, resucita mientras le queden vidas. */
    public static boolean allowDeath(LivingEntity entity, DamageSource source, float amount) {
        if (!fullSet(entity) || !(entity instanceof PlayerEntity player) || lives(player) <= 0) return true;
        int left = lives(player) - 1;
        player.setAttached(LIVES, left);
        player.setHealth(player.getMaxHealth());
        player.addStatusEffect(new StatusEffectInstance(StatusEffects.RESISTANCE, 60, 2));
        player.sendMessage(Text.translatable("message.fate_ubw.god_hand", left).formatted(Formatting.DARK_RED), true);
        if (player.getWorld() instanceof ServerWorld world) {
            world.spawnParticles(ParticleTypes.TOTEM_OF_UNDYING, player.getX(), player.getBodyY(0.5), player.getZ(), 50, 0.6, 0.9, 0.6, 0.3);
            world.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.ENTITY_RAVAGER_ROAR, SoundCategory.PLAYERS, 1.5F, 0.6F);
        }
        return false;
    }

    /** Locura Mejorada: Fuerza III, Velocidad II y Resistencia I durante 15 s. */
    public static boolean madEnhancement(ServerPlayerEntity player) {
        player.addStatusEffect(new StatusEffectInstance(StatusEffects.STRENGTH, 300, 2));
        player.addStatusEffect(new StatusEffectInstance(StatusEffects.SPEED, 300, 1));
        player.addStatusEffect(new StatusEffectInstance(StatusEffects.RESISTANCE, 300, 0));
        ServerWorld world = player.getServerWorld();
        world.spawnParticles(ParticleTypes.ANGRY_VILLAGER, player.getX(), player.getEyeY(), player.getZ(), 12, 0.6, 0.4, 0.6, 0.0);
        world.spawnParticles(ParticleTypes.LARGE_SMOKE, player.getX(), player.getBodyY(0.5), player.getZ(), 20, 0.5, 0.8, 0.5, 0.02);
        world.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.ENTITY_RAVAGER_ROAR, SoundCategory.PLAYERS, 2.0F, 0.6F);
        return true;
    }

    @Override
    public void appendTooltip(ItemStack stack, Item.TooltipContext context, List<Text> tooltip, TooltipType type) {
        tooltip.add(Text.translatable("item.fate_ubw.berserker_armor.tooltip.set").formatted(Formatting.DARK_RED));
        tooltip.add(Text.translatable("item.fate_ubw.berserker_armor.tooltip.weak").formatted(Formatting.GRAY));
        tooltip.add(Text.translatable("item.fate_ubw.berserker_armor.tooltip.abilities").formatted(Formatting.LIGHT_PURPLE));
    }
}
