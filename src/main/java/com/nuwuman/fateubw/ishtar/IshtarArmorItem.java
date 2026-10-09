package com.nuwuman.fateubw.ishtar;

import com.nuwuman.fateubw.FateUBW;
import com.nuwuman.fateubw.ServantArmorItem;
import com.nuwuman.fateubw.master.RinJewelEntity;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.damage.DamageTypes;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.mob.MobEntity;
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

/**
 * Ishtar, la diosa de Venus (en el cuerpo de Rin Tohsaka). Conjunto completo: Velocidad I y, como vuela con Maanna,
 * sin daño por caída. La tiara es opcional (pone las coletas). Habilidades: Ráfaga de Joyas, Manifestación de la
 * Belleza y Barca del Cielo.
 */
public class IshtarArmorItem extends ServantArmorItem {
    public static final int JEWEL_BURST_COOLDOWN = 20 * 8;
    public static final int BEAUTY_COOLDOWN = 20 * 15;
    public static final int SKY_BOAT_COOLDOWN = 20 * 6;
    private static final int JEWELS = 5;
    private static final double BEAUTY_RANGE = 12.0;
    private static final DustParticleEffect GOLD = new DustParticleEffect(new Vector3f(1.0F, 0.8F, 0.25F), 1.3F);

    public IshtarArmorItem(ArmorItem.Type type, Item.Settings settings) {
        super(FateUBW.ISHTAR_MATERIAL, type, settings, "ishtar_armor");
    }

    private static boolean fullSet(LivingEntity entity) {
        return entity instanceof PlayerEntity player
                && wearsSet(player, FateUBW.ISHTAR_CHESTPLATE, FateUBW.ISHTAR_LEGGINGS, FateUBW.ISHTAR_BOOTS);
    }

    @Override
    public void inventoryTick(ItemStack stack, World world, Entity entity, int slot, boolean selected) {
        if (getType() != Type.CHESTPLATE || world.isClient || !(entity instanceof PlayerEntity player)) return;
        if (player.age % 40 != 0 || player.getEquippedStack(EquipmentSlot.CHEST) != stack || !fullSet(player)) return;
        player.addStatusEffect(new StatusEffectInstance(StatusEffects.SPEED, 60, 0, true, false));
    }

    /** Una diosa que vuela en su barca no se hace daño al caer. */
    public static boolean allowDamage(LivingEntity entity, DamageSource source, float amount) {
        return !(fullSet(entity) && source.isOf(DamageTypes.FALL));
    }

    /** Ráfaga de Joyas (Mana Burst): cinco joyas cargadas de maná en abanico que estallan al chocar. */
    public static boolean jewelBurst(ServerPlayerEntity player) {
        ServerWorld world = player.getServerWorld();
        for (int i = 0; i < JEWELS; i++) {
            RinJewelEntity jewel = new RinJewelEntity(world, player);
            float spread = (i - (JEWELS - 1) / 2.0F) * 8.0F;
            jewel.setVelocity(player, player.getPitch() - 4.0F, player.getYaw() + spread, 0.0F, 1.6F, 0.5F);
            world.spawnEntity(jewel);
        }
        Vec3d eye = player.getEyePos();
        world.spawnParticles(GOLD, eye.x, eye.y, eye.z, 20, 0.5, 0.3, 0.5, 0.0);
        world.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.BLOCK_AMETHYST_BLOCK_RESONATE, SoundCategory.PLAYERS, 1.5F, 1.4F);
        world.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.ENTITY_SNOWBALL_THROW, SoundCategory.PLAYERS, 1.0F, 0.6F);
        return true;
    }

    /** Manifestación de la Belleza: quien la mira cae rendido; los que tiene cerca quedan débiles y lentos y dejan de atacarla. */
    public static boolean manifestationOfBeauty(ServerPlayerEntity player) {
        ServerWorld world = player.getServerWorld();
        List<LivingEntity> charmed = world.getEntitiesByClass(LivingEntity.class, player.getBoundingBox().expand(BEAUTY_RANGE),
                e -> e != player && e.isAlive() && !e.isSpectator() && e.squaredDistanceTo(player) <= BEAUTY_RANGE * BEAUTY_RANGE
                        && com.nuwuman.fateubw.Rules.canAffect(player, e));
        for (LivingEntity e : charmed) {
            e.addStatusEffect(new StatusEffectInstance(StatusEffects.WEAKNESS, 20 * 8, 1), player);
            e.addStatusEffect(new StatusEffectInstance(StatusEffects.SLOWNESS, 20 * 8, 1), player);
            if (e instanceof MobEntity mob && mob.getTarget() == player) mob.setTarget(null);
            world.spawnParticles(ParticleTypes.HEART, e.getX(), e.getEyeY() + 0.5, e.getZ(), 3, 0.3, 0.2, 0.3, 0.0);
        }
        for (int i = 0; i < 32; i++) {
            double angle = i * Math.PI / 16;
            world.spawnParticles(GOLD, player.getX() + Math.cos(angle) * 2.5, player.getY() + 1.0, player.getZ() + Math.sin(angle) * 2.5,
                    1, 0.0, 0.0, 0.0, 0.0);
        }
        world.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.BLOCK_AMETHYST_BLOCK_CHIME, SoundCategory.PLAYERS, 2.0F, 1.2F);
        world.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.ENTITY_ALLAY_AMBIENT_WITH_ITEM, SoundCategory.PLAYERS, 1.0F, 0.9F);
        return true;
    }

    /** Barca del Cielo: Maanna la lanza hacia donde mira y la deja planear. */
    public static boolean skyBoat(ServerPlayerEntity player) {
        ServerWorld world = player.getServerWorld();
        Vec3d dir = player.getRotationVec(1.0F);
        player.setVelocity(dir.x * 1.8, Math.max(0.6, dir.y * 1.8 + 0.4), dir.z * 1.8);
        player.velocityModified = true;
        player.fallDistance = 0.0F;
        player.addStatusEffect(new StatusEffectInstance(StatusEffects.SLOW_FALLING, 20 * 5, 0, true, false));
        world.spawnParticles(GOLD, player.getX(), player.getY() + 0.2, player.getZ(), 30, 0.6, 0.2, 0.6, 0.0);
        world.spawnParticles(ParticleTypes.END_ROD, player.getX(), player.getY() + 0.2, player.getZ(), 12, 0.5, 0.1, 0.5, 0.05);
        world.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.ENTITY_BREEZE_JUMP, SoundCategory.PLAYERS, 1.2F, 1.1F);
        return true;
    }

    @Override
    public void appendTooltip(ItemStack stack, Item.TooltipContext context, List<Text> tooltip, TooltipType type) {
        tooltip.add(Text.translatable("item.fate_ubw.ishtar_armor.tooltip.set").formatted(Formatting.GOLD));
        tooltip.add(Text.translatable("item.fate_ubw.ishtar_armor.tooltip.abilities").formatted(Formatting.GOLD));
    }
}
