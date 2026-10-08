package com.nuwuman.fateubw.saber;

import com.nuwuman.fateubw.FateUBW;
import com.nuwuman.fateubw.ServantArmorItem;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.damage.DamageTypes;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ArmorItem;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.tooltip.TooltipType;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.math.Vec3d;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.world.World;

import java.util.List;

/**
 * Armadura de Artoria con falda animada. Conjunto completo: Avalon (Regeneración I)
 * y Resistencia Mágica (inmune al daño mágico y al Wither).
 */
public class SaberArmorItem extends ServantArmorItem {
    public static final int AVALON_COOLDOWN = 20 * 90;
    public static final int MANA_BURST_COOLDOWN = 20 * 6;

    public SaberArmorItem(ArmorItem.Type type, Item.Settings settings) {
        super(FateUBW.SABER_MATERIAL, type, settings, "saber_armor");
    }

    private static boolean fullSet(LivingEntity entity) {
        return entity instanceof PlayerEntity player
                && wearsSet(player, FateUBW.SABER_CHESTPLATE, FateUBW.SABER_LEGGINGS, FateUBW.SABER_BOOTS);
    }

    // Avalon: la vaina sagrada cura las heridas
    @Override
    public void inventoryTick(ItemStack stack, World world, Entity entity, int slot, boolean selected) {
        if (getType() != Type.CHESTPLATE || world.isClient || !(entity instanceof PlayerEntity player)) return;
        if (player.age % 40 != 0 || player.getEquippedStack(EquipmentSlot.CHEST) != stack || !fullSet(player)) return;
        player.addStatusEffect(new StatusEffectInstance(StatusEffects.REGENERATION, 60, 0, true, false));
        player.removeStatusEffect(StatusEffects.WITHER);
    }

    /** Resistencia Mágica: anula el daño mágico (pociones, evocadores, aliento de dragón) y el del Wither. */
    public static boolean allowDamage(LivingEntity entity, DamageSource source, float amount) {
        if (!fullSet(entity)) return true;
        boolean magic = source.isOf(DamageTypes.MAGIC) || source.isOf(DamageTypes.INDIRECT_MAGIC)
                || source.isOf(DamageTypes.DRAGON_BREATH) || source.isOf(DamageTypes.WITHER);
        if (!magic) return true;
        if (entity.getWorld() instanceof ServerWorld world) {
            world.spawnParticles(ParticleTypes.ENCHANT, entity.getX(), entity.getBodyY(0.6), entity.getZ(), 15, 0.4, 0.6, 0.4, 0.5);
        }
        return false;
    }

    /** Avalon, la Utopía Lejana: la vaina se despliega y nada puede tocarla durante 5 s. */
    public static boolean avalon(ServerPlayerEntity player) {
        player.addStatusEffect(new StatusEffectInstance(StatusEffects.RESISTANCE, 100, 4)); // nivel V: inmune
        player.addStatusEffect(new StatusEffectInstance(StatusEffects.REGENERATION, 100, 2));
        player.addStatusEffect(new StatusEffectInstance(StatusEffects.GLOWING, 100, 0, false, false));
        ServerWorld world = player.getServerWorld();
        for (int i = 0; i < 48; i++) {
            double angle = i * Math.PI * 2 / 48;
            world.spawnParticles(ParticleTypes.END_ROD, player.getX() + Math.cos(angle) * 1.6, player.getY() + 0.2 + (i % 6) * 0.35,
                    player.getZ() + Math.sin(angle) * 1.6, 1, 0.0, 0.0, 0.0, 0.0);
        }
        world.spawnParticles(ParticleTypes.FLASH, player.getX(), player.getBodyY(0.5), player.getZ(), 1, 0.0, 0.0, 0.0, 0.0);
        world.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.BLOCK_BEACON_ACTIVATE, SoundCategory.PLAYERS, 1.5F, 1.4F);
        world.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.BLOCK_AMETHYST_BLOCK_RESONATE, SoundCategory.PLAYERS, 2.0F, 0.8F);
        player.sendMessage(Text.literal("Avalon").formatted(Formatting.GOLD, Formatting.BOLD), true);
        com.nuwuman.fateubw.Voices.say(world, player, "avalon");
        return true;
    }

    /** Mana Burst: una embestida impulsada por maná que arrolla a quien esté en el camino. */
    public static boolean manaBurst(ServerPlayerEntity player) {
        ServerWorld world = player.getServerWorld();
        Vec3d look = player.getRotationVec(1.0F);
        Vec3d dir = new Vec3d(look.x, 0.0, look.z).normalize();
        player.setVelocity(dir.x * 2.2, 0.3, dir.z * 2.2);
        player.velocityModified = true;
        player.fallDistance = 0.0F;
        for (LivingEntity target : world.getEntitiesByClass(LivingEntity.class, player.getBoundingBox().stretch(dir.multiply(7.0)).expand(1.5),
                e -> e != player && e.isAlive())) {
            target.damage(world.getDamageSources().playerAttack(player), 8.0F);
            target.takeKnockback(1.5, -dir.x, -dir.z);
        }
        for (int i = 0; i < 7; i++) {
            Vec3d p = player.getPos().add(dir.multiply(i)).add(0.0, 1.0, 0.0);
            world.spawnParticles(ParticleTypes.ELECTRIC_SPARK, p.x, p.y, p.z, 6, 0.3, 0.5, 0.3, 0.1);
            world.spawnParticles(ParticleTypes.CLOUD, p.x, p.y - 0.8, p.z, 2, 0.2, 0.1, 0.2, 0.02);
        }
        world.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.ENTITY_BREEZE_SHOOT, SoundCategory.PLAYERS, 1.5F, 0.6F);
        world.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.ITEM_FIRECHARGE_USE, SoundCategory.PLAYERS, 1.0F, 1.4F);
        return true;
    }

    @Override
    public void appendTooltip(ItemStack stack, Item.TooltipContext context, List<Text> tooltip, TooltipType type) {
        tooltip.add(Text.translatable("item.fate_ubw.saber_armor.tooltip.avalon").formatted(Formatting.GOLD));
        tooltip.add(Text.translatable("item.fate_ubw.saber_armor.tooltip.magic").formatted(Formatting.AQUA));
        tooltip.add(Text.translatable("item.fate_ubw.saber_armor.tooltip.abilities").formatted(Formatting.LIGHT_PURPLE));
    }
}
