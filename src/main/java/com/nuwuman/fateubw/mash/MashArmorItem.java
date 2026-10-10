package com.nuwuman.fateubw.mash;

import com.nuwuman.fateubw.FateUBW;
import com.nuwuman.fateubw.ServantArmorItem;
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
import net.minecraft.particle.DustParticleEffect;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import org.joml.Vector3f;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.WeakHashMap;

/**
 * Mash Kyrielight, la Shielder. Conjunto completo: Resistencia I y "Protección de las Hadas" (un 30 % menos de daño por
 * la espalda: es la que se pone delante). Habilidades: Bunker Bolt, Muro Transitorio de Copos de Nieve y Muro Oscuro
 * de Tiza. Su arma es el escudo (MashShieldItem), con Lord Camelot como Noble Phantasm.
 */
public class MashArmorItem extends ServantArmorItem {
    public static final int BUNKER_BOLT_COOLDOWN = 20 * 6;
    public static final int SNOWFLAKES_COOLDOWN = 20 * 20;
    public static final int CHALK_COOLDOWN = 20 * 15;
    private static final int SNOWFLAKES_TICKS = 20 * 4;
    private static final int CHALK_TICKS = 20 * 10;
    private static final double SNOWFLAKES_RANGE = 5.0;
    private static final DustParticleEffect PURPLE = new DustParticleEffect(new Vector3f(0.6F, 0.4F, 0.95F), 1.2F);
    // Hasta qué tick del servidor dura cada protección (solo las mira el servidor, al calcular el daño)
    private static final Map<UUID, Integer> SNOWFLAKES = new java.util.HashMap<>();
    private static final Map<UUID, Integer> CHALK = new java.util.HashMap<>();

    public MashArmorItem(ArmorItem.Type type, Item.Settings settings) {
        super(FateUBW.MASH_MATERIAL, type, settings, "mash_armor");
    }

    public static boolean fullSet(LivingEntity entity) {
        return entity instanceof PlayerEntity player
                && wearsSet(player, FateUBW.MASH_CHESTPLATE, FateUBW.MASH_LEGGINGS, FateUBW.MASH_BOOTS);
    }

    @Override
    public void inventoryTick(ItemStack stack, World world, Entity entity, int slot, boolean selected) {
        if (getType() != Type.CHESTPLATE || world.isClient || !(entity instanceof PlayerEntity player)) return;
        if (player.age % 40 != 0 || player.getEquippedStack(EquipmentSlot.CHEST) != stack || !fullSet(player)) return;
        player.addStatusEffect(new StatusEffectInstance(StatusEffects.RESISTANCE, 60, 0, true, false));
    }

    /**
     * Ajusta el daño que recibe {@code victim} (lo llama LivingEntity.damage, después de fatePlayerDamagePercent):
     * Muro de Tiza (anula un golpe fuerte), Muro de Copos de Nieve (la mitad) y Protección de las Hadas (por la espalda).
     */
    public static float adjustDamage(LivingEntity victim, DamageSource source, float amount) {
        if (victim.getWorld().isClient() || !(victim.getWorld() instanceof ServerWorld world) || amount <= 0) return amount;
        int now = world.getServer().getTicks();
        Integer chalk = CHALK.get(victim.getUuid());
        if (chalk != null && chalk > now && amount >= 4.0F) {
            CHALK.remove(victim.getUuid());
            world.spawnParticles(ParticleTypes.WHITE_ASH, victim.getX(), victim.getBodyY(0.5), victim.getZ(), 40, 0.6, 0.8, 0.6, 0.05);
            world.playSound(null, victim.getX(), victim.getY(), victim.getZ(), SoundEvents.BLOCK_AMETHYST_BLOCK_BREAK, SoundCategory.PLAYERS, 1.5F, 1.4F);
            return 0.0F;
        }
        Integer flakes = SNOWFLAKES.get(victim.getUuid());
        if (flakes != null && flakes > now) amount *= 0.5F;
        if (fullSet(victim) && source.getPosition() != null) {
            Vec3d look = victim.getRotationVector().multiply(1, 0, 1);
            Vec3d from = source.getPosition().subtract(victim.getPos()).multiply(1, 0, 1);
            if (look.lengthSquared() > 1e-4 && from.lengthSquared() > 1e-4 && look.normalize().dotProduct(from.normalize()) < -0.3) amount *= 0.7F;
        }
        return amount;
    }

    /** Bunker Bolt: carga con el escudo por delante; arrolla y aturde (1 s) a lo primero que encuentra. */
    public static boolean bunkerBolt(ServerPlayerEntity player) {
        ServerWorld world = player.getServerWorld();
        Vec3d look = player.getRotationVec(1.0F);
        Vec3d dir = new Vec3d(look.x, 0.0, look.z).normalize();
        player.setVelocity(dir.x * 2.0, 0.25, dir.z * 2.0);
        player.velocityModified = true;
        player.fallDistance = 0.0F;
        for (LivingEntity target : world.getEntitiesByClass(LivingEntity.class, player.getBoundingBox().stretch(dir.multiply(6.0)).expand(1.2),
                e -> e != player && e.isAlive() && com.nuwuman.fateubw.Rules.canAffect(player, e))) {
            target.damage(world.getDamageSources().playerAttack(player), 6.0F);
            target.takeKnockback(2.0, -dir.x, -dir.z);
            target.addStatusEffect(new StatusEffectInstance(FateUBW.ENKIDU_CHAINS, 20, 0, false, false, false), player);
            world.spawnParticles(ParticleTypes.CRIT, target.getX(), target.getBodyY(0.6), target.getZ(), 12, 0.3, 0.3, 0.3, 0.2);
        }
        world.spawnParticles(PURPLE, player.getX(), player.getBodyY(0.5), player.getZ(), 30, 0.6, 0.6, 0.6, 0.0);
        world.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.ITEM_SHIELD_BLOCK, SoundCategory.PLAYERS, 1.5F, 0.7F);
        world.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.ENTITY_IRON_GOLEM_ATTACK, SoundCategory.PLAYERS, 1.2F, 0.8F);
        return true;
    }

    /** Muro Transitorio de Copos de Nieve: ella y los jugadores a 5 bloques reciben la mitad de daño durante 4 s. */
    public static boolean wallOfSnowflakes(ServerPlayerEntity player) {
        ServerWorld world = player.getServerWorld();
        int until = world.getServer().getTicks() + SNOWFLAKES_TICKS;
        for (PlayerEntity ally : world.getEntitiesByClass(PlayerEntity.class, player.getBoundingBox().expand(SNOWFLAKES_RANGE),
                p -> p.isAlive() && !p.isSpectator() && p.squaredDistanceTo(player) <= SNOWFLAKES_RANGE * SNOWFLAKES_RANGE)) {
            SNOWFLAKES.put(ally.getUuid(), until);
            world.spawnParticles(ParticleTypes.SNOWFLAKE, ally.getX(), ally.getBodyY(0.5), ally.getZ(), 30, 0.5, 0.8, 0.5, 0.02);
        }
        for (int i = 0; i < 48; i++) {
            double angle = i * Math.PI / 24;
            world.spawnParticles(ParticleTypes.SNOWFLAKE, player.getX() + Math.cos(angle) * SNOWFLAKES_RANGE, player.getY() + 0.3 + (i % 4) * 0.6,
                    player.getZ() + Math.sin(angle) * SNOWFLAKES_RANGE, 1, 0.0, 0.0, 0.0, 0.0);
        }
        world.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.BLOCK_POWDER_SNOW_PLACE, SoundCategory.PLAYERS, 2.0F, 0.8F);
        world.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.BLOCK_BEACON_POWER_SELECT, SoundCategory.PLAYERS, 1.0F, 1.6F);
        return true;
    }

    /** Muro Oscuro de Tiza: un escudo de luz sobre el jugador al que miras (o sobre ella) que anula el siguiente golpe fuerte. */
    public static boolean wallOfChalk(ServerPlayerEntity player) {
        ServerWorld world = player.getServerWorld();
        LivingEntity target = com.nuwuman.fateubw.lancer.GaeBolgItem.findTarget(world, player, 24.0, 0.95);
        PlayerEntity ally = target instanceof PlayerEntity p ? p : player;
        CHALK.put(ally.getUuid(), world.getServer().getTicks() + CHALK_TICKS);
        world.spawnParticles(ParticleTypes.END_ROD, ally.getX(), ally.getBodyY(0.5), ally.getZ(), 30, 0.5, 0.8, 0.5, 0.03);
        world.spawnParticles(PURPLE, ally.getX(), ally.getBodyY(0.5), ally.getZ(), 20, 0.5, 0.8, 0.5, 0.0);
        world.playSound(null, ally.getX(), ally.getY(), ally.getZ(), SoundEvents.BLOCK_AMETHYST_BLOCK_CHIME, SoundCategory.PLAYERS, 1.5F, 0.8F);
        if (ally != player) ally.sendMessage(Text.translatable("message.fate_ubw.wall_of_chalk", player.getName()).formatted(Formatting.LIGHT_PURPLE), true);
        return true;
    }

    @Override
    public void appendTooltip(ItemStack stack, Item.TooltipContext context, List<Text> tooltip, TooltipType type) {
        tooltip.add(Text.translatable("item.fate_ubw.mash_armor.tooltip.set").formatted(Formatting.GOLD));
        tooltip.add(Text.translatable("item.fate_ubw.mash_armor.tooltip.abilities").formatted(Formatting.LIGHT_PURPLE));
    }
}
