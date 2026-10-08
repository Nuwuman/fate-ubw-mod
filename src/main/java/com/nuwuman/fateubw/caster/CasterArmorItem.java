package com.nuwuman.fateubw.caster;

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
import net.minecraft.particle.DustParticleEffect;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import org.joml.Vector3f;

import java.util.Comparator;
import java.util.List;

/**
 * Túnica de Medea con capa animada. Conjunto completo: Creación de Territorio (Regeneración I) y magia de vuelo
 * (sin daño por caída). Habilidades: Palabras Divinas Rápidas y Transferencia Espacial.
 */
public class CasterArmorItem extends ServantArmorItem {
    public static final int DIVINE_WORDS_COOLDOWN = 20 * 5;
    public static final int SPATIAL_TRANSFER_COOLDOWN = 20 * 8;
    private static final double SPELL_RANGE = 24.0;
    private static final int MAX_TARGETS = 6;
    private static final DustParticleEffect VIOLET = new DustParticleEffect(new Vector3f(0.65F, 0.3F, 1.0F), 1.2F);

    public CasterArmorItem(ArmorItem.Type type, Item.Settings settings) {
        super(FateUBW.CASTER_MATERIAL, type, settings, "caster_armor");
    }

    private static boolean fullSet(LivingEntity entity) {
        return entity instanceof PlayerEntity player
                && wearsSet(player, FateUBW.CASTER_CHESTPLATE, FateUBW.CASTER_LEGGINGS, FateUBW.CASTER_BOOTS);
    }

    @Override
    public void inventoryTick(ItemStack stack, World world, Entity entity, int slot, boolean selected) {
        if (getType() != Type.CHESTPLATE || world.isClient || !(entity instanceof PlayerEntity player)) return;
        if (player.age % 40 != 0 || player.getEquippedStack(EquipmentSlot.CHEST) != stack || !fullSet(player)) return;
        player.addStatusEffect(new StatusEffectInstance(StatusEffects.REGENERATION, 60, 0, true, false));
    }

    /** Magia de vuelo: Medea no sufre daño por caída. */
    public static boolean allowDamage(LivingEntity entity, DamageSource source, float amount) {
        return !(fullSet(entity) && source.isOf(DamageTypes.FALL));
    }

    /** Palabras Divinas Rápidas: rayos de maná a los seis enemigos más cercanos frente a ella. */
    public static boolean divineWords(ServerPlayerEntity player) {
        ServerWorld world = player.getServerWorld();
        Vec3d eye = player.getEyePos();
        Vec3d dir = player.getRotationVec(1.0F);
        List<LivingEntity> targets = world.getEntitiesByClass(LivingEntity.class, player.getBoundingBox().expand(SPELL_RANGE),
                        e -> e != player && e.isAlive() && !e.isSpectator() && player.canSee(e)).stream()
                .filter(e -> {
                    Vec3d to = e.getBoundingBox().getCenter().subtract(eye);
                    return to.length() <= SPELL_RANGE && to.normalize().dotProduct(dir) > 0.75;
                })
                .sorted(Comparator.comparingDouble(e -> e.squaredDistanceTo(player)))
                .limit(MAX_TARGETS).toList();
        if (targets.isEmpty()) {
            beam(world, eye, eye.add(dir.multiply(SPELL_RANGE)));
        }
        for (LivingEntity target : targets) {
            Vec3d to = target.getBoundingBox().getCenter();
            beam(world, eye.add(0.0, 0.6, 0.0), to);
            target.damage(world.getDamageSources().indirectMagic(player, player), 7.0F);
            world.spawnParticles(ParticleTypes.END_ROD, to.x, to.y, to.z, 6, 0.2, 0.2, 0.2, 0.05);
        }
        // El círculo mágico detrás de ella
        for (int i = 0; i < 24; i++) {
            double angle = i * Math.PI / 12;
            Vec3d back = player.getPos().add(dir.multiply(-0.8)).add(Math.cos(angle) * 1.3, 1.6 + Math.sin(angle) * 1.3, 0.0);
            world.spawnParticles(VIOLET, back.x, back.y, back.z, 1, 0.0, 0.0, 0.0, 0.0);
        }
        world.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.ENTITY_EVOKER_CAST_SPELL, SoundCategory.PLAYERS, 1.5F, 1.3F);
        world.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.ENTITY_ILLUSIONER_CAST_SPELL, SoundCategory.PLAYERS, 1.0F, 1.0F);
        return true;
    }

    private static void beam(ServerWorld world, Vec3d from, Vec3d to) {
        int steps = (int) Math.ceil(from.distanceTo(to) * 2);
        for (int i = 0; i <= steps; i++) {
            Vec3d p = from.lerp(to, i / (double) steps);
            world.spawnParticles(VIOLET, p.x, p.y, p.z, 1, 0.0, 0.0, 0.0, 0.0);
        }
    }

    /** Transferencia Espacial: aparece en el punto al que mira (32 bloques). */
    public static boolean spatialTransfer(ServerPlayerEntity player) {
        ServerWorld world = player.getServerWorld();
        HitResult hit = player.raycast(32.0, 1.0F, false);
        Vec3d dest;
        if (hit instanceof BlockHitResult block && hit.getType() == HitResult.Type.BLOCK) {
            BlockPos stand = block.getBlockPos().offset(block.getSide());
            dest = Vec3d.ofBottomCenter(stand);
        } else {
            dest = player.getEyePos().add(player.getRotationVec(1.0F).multiply(32.0)).subtract(0.0, player.getStandingEyeHeight(), 0.0);
        }
        // Sube un poco si no cabe
        for (int i = 0; i < 3 && !world.isSpaceEmpty(player, player.getBoundingBox().offset(dest.subtract(player.getPos()))); i++) {
            dest = dest.add(0.0, 1.0, 0.0);
        }
        if (!world.isSpaceEmpty(player, player.getBoundingBox().offset(dest.subtract(player.getPos())))) {
            player.sendMessage(Text.translatable("message.fate_ubw.spatial_transfer.blocked").formatted(Formatting.GRAY), true);
            return false;
        }
        world.spawnParticles(ParticleTypes.PORTAL, player.getX(), player.getBodyY(0.5), player.getZ(), 60, 0.4, 0.8, 0.4, 0.5);
        world.spawnParticles(VIOLET, player.getX(), player.getBodyY(0.5), player.getZ(), 20, 0.4, 0.8, 0.4, 0.0);
        world.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.ENTITY_ENDERMAN_TELEPORT, SoundCategory.PLAYERS, 1.0F, 1.2F);
        player.requestTeleport(dest.x, dest.y, dest.z);
        player.fallDistance = 0.0F;
        world.spawnParticles(ParticleTypes.PORTAL, dest.x, dest.y + 1.0, dest.z, 60, 0.4, 0.8, 0.4, 0.5);
        world.playSound(null, dest.x, dest.y, dest.z, SoundEvents.ENTITY_ENDERMAN_TELEPORT, SoundCategory.PLAYERS, 1.0F, 1.4F);
        return true;
    }

    @Override
    public void appendTooltip(ItemStack stack, Item.TooltipContext context, List<Text> tooltip, TooltipType type) {
        tooltip.add(Text.translatable("item.fate_ubw.caster_armor.tooltip.set").formatted(Formatting.LIGHT_PURPLE));
        tooltip.add(Text.translatable("item.fate_ubw.caster_armor.tooltip.abilities").formatted(Formatting.LIGHT_PURPLE));
    }
}
