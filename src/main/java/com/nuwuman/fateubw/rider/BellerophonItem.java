package com.nuwuman.fateubw.rider;

import com.nuwuman.fateubw.FateUBW;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.tooltip.TooltipType;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Hand;
import net.minecraft.util.TypedActionResult;
import net.minecraft.world.World;

import java.util.List;

/** Bridas de Bellerophon. Click derecho: invoca a Pegaso y lo monta. Montado: la embestida de Bellerophon. */
public class BellerophonItem extends Item {
    // Corto: también bloquea la embestida justo después de montar
    public static final int SUMMON_COOLDOWN = 20;
    public static final int CHARGE_COOLDOWN = 20 * 30;

    public BellerophonItem(Item.Settings settings) {
        super(settings);
    }

    @Override
    // El ítem (ya no se fabrica: ahora es habilidad del conjunto de Rider) funciona igual que la habilidad
    public TypedActionResult<ItemStack> use(World world, PlayerEntity user, Hand hand) {
        ItemStack stack = user.getStackInHand(hand);
        Item key = user.getVehicle() instanceof PegasusEntity ? FateUBW.BELLEROPHON_CHARGE : FateUBW.BELLEROPHON;
        if (user.getItemCooldownManager().isCoolingDown(key)) {
            if (!world.isClient) FateUBW.cooldownMessage(user, key, key == FateUBW.BELLEROPHON ? SUMMON_COOLDOWN : CHARGE_COOLDOWN, "bellerophon");
            return TypedActionResult.fail(stack);
        }
        if (user instanceof ServerPlayerEntity player && ability(player)) {
            com.nuwuman.fateubw.Rules.cooldown(player, key, key == FateUBW.BELLEROPHON ? SUMMON_COOLDOWN : CHARGE_COOLDOWN);
            com.nuwuman.fateubw.PlayerAnims.playAll(player, "bellerophon");
        }
        return TypedActionResult.success(stack, world.isClient());
    }

    /** Montado en Pegaso: la embestida. Si no: invoca a Pegaso y lo monta. */
    public static boolean ability(ServerPlayerEntity user) {
        if (user.getVehicle() instanceof PegasusEntity pegasus) {
            pegasus.startCharge();
            com.nuwuman.fateubw.Voices.say(user.getWorld(), user, "bellerophon");
            return true;
        }
        ServerWorld server = user.getServerWorld();
        PegasusEntity pegasus = FateUBW.PEGASUS.create(server);
        if (pegasus == null) return false;
        pegasus.refreshPositionAndAngles(user.getX(), user.getY(), user.getZ(), user.getYaw(), 0.0F);
        server.spawnEntity(pegasus);
        user.startRiding(pegasus, true);
        server.spawnParticles(ParticleTypes.END_ROD, user.getX(), user.getBodyY(0.5), user.getZ(), 40, 1.0, 1.0, 1.0, 0.1);
        server.spawnParticles(ParticleTypes.CLOUD, user.getX(), user.getY() + 0.5, user.getZ(), 20, 1.0, 0.3, 1.0, 0.05);
        server.playSound(null, user.getX(), user.getY(), user.getZ(), SoundEvents.ENTITY_HORSE_AMBIENT, SoundCategory.PLAYERS, 1.2F, 1.2F);
        server.playSound(null, user.getX(), user.getY(), user.getZ(), SoundEvents.ENTITY_ENDER_DRAGON_FLAP, SoundCategory.PLAYERS, 1.0F, 1.4F);
        return true;
    }

    @Override
    public void appendTooltip(ItemStack stack, Item.TooltipContext context, List<Text> tooltip, TooltipType type) {
        tooltip.add(Text.translatable("item.fate_ubw.bellerophon.tooltip.summon").formatted(Formatting.GRAY));
        tooltip.add(Text.translatable("item.fate_ubw.bellerophon.tooltip.fly").formatted(Formatting.GRAY));
        tooltip.add(Text.translatable("item.fate_ubw.bellerophon.tooltip.charge").formatted(Formatting.GOLD));
    }
}
