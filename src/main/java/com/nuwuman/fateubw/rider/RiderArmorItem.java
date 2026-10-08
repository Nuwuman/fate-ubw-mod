package com.nuwuman.fateubw.rider;

import com.nuwuman.fateubw.FateUBW;
import com.nuwuman.fateubw.Rules;
import com.nuwuman.fateubw.ServantArmorItem;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.entity.LivingEntity;
import net.minecraft.particle.DustParticleEffect;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import org.joml.Vector3f;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ArmorItem;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.tooltip.TooltipType;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.world.World;

import java.util.List;

/** Ropa de Medusa: Breaker Gorgon (venda), vestido con melena animada, medias y botas. Conjunto completo: Velocidad I y Salto II. */
public class RiderArmorItem extends ServantArmorItem {
    public static final int ANDROMEDA_COOLDOWN = 20 * 60;
    private static final int ANDROMEDA_TICKS = 20 * 10;
    private static final double ANDROMEDA_RADIUS = 10.0;
    private static final DustParticleEffect BLOOD = new DustParticleEffect(new Vector3f(0.7F, 0.0F, 0.08F), 1.6F);
    // Campos de sangre activos: dueño → ticks que le quedan
    private static final Map<UUID, Integer> FORTS = new HashMap<>();

    public RiderArmorItem(ArmorItem.Type type, Item.Settings settings) {
        super(FateUBW.RIDER_MATERIAL, type, settings, "rider_armor");
    }

    public static void register() {
        ServerTickEvents.END_SERVER_TICK.register(server -> FORTS.entrySet().removeIf(entry -> {
            ServerPlayerEntity rider = server.getPlayerManager().getPlayer(entry.getKey());
            int left = entry.getValue() - 1;
            if (rider == null || !rider.isAlive() || left <= 0) return true;
            entry.setValue(left);
            fortTick(rider.getServerWorld(), rider, left);
            return false;
        }));
    }

    /** Blood Fort Andromeda: un templo de sangre de 10 bloques que durante 10 s absorbe la vida de los demás. */
    public static boolean bloodFortAndromeda(ServerPlayerEntity player) {
        FORTS.put(player.getUuid(), ANDROMEDA_TICKS);
        ServerWorld world = player.getServerWorld();
        world.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.ENTITY_WARDEN_HEARTBEAT, SoundCategory.PLAYERS, 2.0F, 0.6F);
        world.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.BLOCK_BEACON_ACTIVATE, SoundCategory.PLAYERS, 1.5F, 0.5F);
        player.sendMessage(Text.literal("Blood Fort Andromeda").formatted(Formatting.DARK_RED, Formatting.BOLD), true);
        return true;
    }

    private static void fortTick(ServerWorld world, ServerPlayerEntity rider, int left) {
        // La cúpula de sangre que la rodea
        if (left % 4 == 0) {
            for (int i = 0; i < 40; i++) {
                double angle = i * Math.PI / 20 + left * 0.02, h = (i % 5) / 5.0;
                double r = ANDROMEDA_RADIUS * Math.sqrt(1 - h * h);
                world.spawnParticles(BLOOD, rider.getX() + Math.cos(angle) * r, rider.getY() + h * ANDROMEDA_RADIUS * 0.6,
                        rider.getZ() + Math.sin(angle) * r, 1, 0.0, 0.0, 0.0, 0.0);
            }
        }
        if (left % 20 != 0) return;
        if (left % 40 == 0) world.playSound(null, rider.getX(), rider.getY(), rider.getZ(), SoundEvents.ENTITY_WARDEN_HEARTBEAT, SoundCategory.PLAYERS, 1.5F, 0.6F);
        for (LivingEntity victim : world.getEntitiesByClass(LivingEntity.class, rider.getBoundingBox().expand(ANDROMEDA_RADIUS),
                e -> e != rider && e.isAlive() && !e.isSpectator() && e.squaredDistanceTo(rider) < ANDROMEDA_RADIUS * ANDROMEDA_RADIUS
                        && Rules.canAffect(rider, e))) {
            if (victim.damage(world.getDamageSources().indirectMagic(rider, rider), 2.0F)) rider.heal(1.0F);
            world.spawnParticles(BLOOD, victim.getX(), victim.getBodyY(0.5), victim.getZ(), 8, 0.3, 0.4, 0.3, 0.0);
        }
    }

    @Override
    public void inventoryTick(ItemStack stack, World world, Entity entity, int slot, boolean selected) {
        if (getType() != Type.CHESTPLATE || world.isClient || !(entity instanceof PlayerEntity player)) return;
        if (player.age % 40 != 0 || player.getEquippedStack(EquipmentSlot.CHEST) != stack) return;
        if (!player.getEquippedStack(EquipmentSlot.HEAD).isOf(FateUBW.RIDER_HELMET)
                || !wearsSet(player, FateUBW.RIDER_CHESTPLATE, FateUBW.RIDER_LEGGINGS, FateUBW.RIDER_BOOTS)) return;
        player.addStatusEffect(new StatusEffectInstance(StatusEffects.SPEED, 60, 0, true, false));
        player.addStatusEffect(new StatusEffectInstance(StatusEffects.JUMP_BOOST, 60, 1, true, false));
    }

    @Override
    public void appendTooltip(ItemStack stack, Item.TooltipContext context, List<Text> tooltip, TooltipType type) {
        tooltip.add(Text.translatable("item.fate_ubw.rider_armor.tooltip.set").formatted(Formatting.LIGHT_PURPLE));
        tooltip.add(Text.translatable("item.fate_ubw.rider_armor.tooltip.abilities").formatted(Formatting.LIGHT_PURPLE));
    }
}
