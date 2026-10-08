package com.nuwuman.fateubw.assassin;

import com.nuwuman.fateubw.FateUBW;
import com.nuwuman.fateubw.ServantArmorItem;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ArmorItem;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.tooltip.TooltipType;
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
 * Haori y hakama de Sasaki Kojirō con faldones animados. Conjunto completo: Velocidad I y Ojo de la Mente (falso):
 * esquiva uno de cada cuatro golpes cuerpo a cuerpo. Habilidad: Ocultación de Presencia.
 */
public class AssassinArmorItem extends ServantArmorItem {
    public static final int PRESENCE_CONCEALMENT_COOLDOWN = 20 * 40;
    private static final float DODGE_CHANCE = 0.25F;

    public AssassinArmorItem(ArmorItem.Type type, Item.Settings settings) {
        super(FateUBW.ASSASSIN_MATERIAL, type, settings, "assassin_armor");
    }

    private static boolean fullSet(LivingEntity entity) {
        return entity instanceof PlayerEntity player
                && wearsSet(player, FateUBW.ASSASSIN_CHESTPLATE, FateUBW.ASSASSIN_LEGGINGS, FateUBW.ASSASSIN_BOOTS);
    }

    @Override
    public void inventoryTick(ItemStack stack, World world, Entity entity, int slot, boolean selected) {
        if (getType() != Type.CHESTPLATE || world.isClient || !(entity instanceof PlayerEntity player)) return;
        if (player.age % 40 != 0 || player.getEquippedStack(EquipmentSlot.CHEST) != stack || !fullSet(player)) return;
        player.addStatusEffect(new StatusEffectInstance(StatusEffects.SPEED, 60, 0, true, false));
    }

    /** Ojo de la Mente (falso): lee el golpe y lo esquiva. */
    public static boolean allowDamage(LivingEntity entity, DamageSource source, float amount) {
        if (!fullSet(entity) || source.getAttacker() == null || source.isIn(DamageTypeTags.IS_PROJECTILE)
                || source.getSource() != source.getAttacker() || entity.getRandom().nextFloat() >= DODGE_CHANCE) return true;
        if (entity.getWorld() instanceof ServerWorld world) {
            world.spawnParticles(ParticleTypes.CLOUD, entity.getX(), entity.getBodyY(0.5), entity.getZ(), 8, 0.3, 0.4, 0.3, 0.05);
            world.playSound(null, entity.getX(), entity.getY(), entity.getZ(), SoundEvents.ENTITY_PLAYER_ATTACK_NODAMAGE, SoundCategory.PLAYERS, 1.0F, 1.4F);
        }
        return false;
    }

    /** Ocultación de Presencia: invisible 10 s y los monstruos que le perseguían le pierden. */
    public static boolean presenceConcealment(ServerPlayerEntity player) {
        player.addStatusEffect(new StatusEffectInstance(StatusEffects.INVISIBILITY, 200, 0, false, false));
        ServerWorld world = player.getServerWorld();
        for (MobEntity mob : world.getEntitiesByClass(MobEntity.class, player.getBoundingBox().expand(32.0), m -> m.getTarget() == player)) {
            mob.setTarget(null);
        }
        world.spawnParticles(ParticleTypes.LARGE_SMOKE, player.getX(), player.getBodyY(0.5), player.getZ(), 20, 0.4, 0.6, 0.4, 0.02);
        world.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.ENTITY_ILLUSIONER_MIRROR_MOVE, SoundCategory.PLAYERS, 1.0F, 1.0F);
        return true;
    }

    @Override
    public void appendTooltip(ItemStack stack, Item.TooltipContext context, List<Text> tooltip, TooltipType type) {
        tooltip.add(Text.translatable("item.fate_ubw.assassin_armor.tooltip.set").formatted(Formatting.AQUA));
        tooltip.add(Text.translatable("item.fate_ubw.assassin_armor.tooltip.abilities").formatted(Formatting.LIGHT_PURPLE));
    }
}
