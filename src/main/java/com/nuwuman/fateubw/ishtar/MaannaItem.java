package com.nuwuman.fateubw.ishtar;

import com.nuwuman.fateubw.FateUBW;
import com.nuwuman.fateubw.master.RinJewelEntity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.projectile.ArrowEntity;
import net.minecraft.entity.projectile.PersistentProjectileEntity;
import net.minecraft.item.BowItem;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.item.tooltip.TooltipType;
import net.minecraft.particle.DustParticleEffect;
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
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3f;

import java.util.List;

/**
 * Maanna, la Barca del Cielo, usada como arco: dispara flechas de luz sin gastar flechas; tensado del todo, una joya
 * que estalla. Agachado y cargado 3 s: An Gal Ta Kigal Shè, Venus lanzada como una flecha.
 */
public class MaannaItem extends BowItem {
    public static final int NP_COOLDOWN = 20 * 25;
    public static final int NP_CHARGE = 60;
    private static final DustParticleEffect GOLD = new DustParticleEffect(new Vector3f(1.0F, 0.82F, 0.3F), 1.2F);
    private static final DustParticleEffect BLUE = new DustParticleEffect(new Vector3f(0.3F, 0.55F, 1.0F), 1.2F);

    public MaannaItem(Item.Settings settings) {
        super(settings);
    }

    @Override
    public TypedActionResult<ItemStack> use(World world, PlayerEntity user, Hand hand) {
        user.setCurrentHand(hand);
        return TypedActionResult.consume(user.getStackInHand(hand));
    }

    static boolean npReady(PlayerEntity player) {
        return player.isSneaking() && !player.getItemCooldownManager().isCoolingDown(FateUBW.AN_GAL_TA_KIGAL_SHE_NP);
    }

    @Override
    public void usageTick(World world, LivingEntity user, ItemStack stack, int remainingUseTicks) {
        if (world instanceof ServerWorld server && user instanceof PlayerEntity player) {
            chargeTick(server, player, getMaxUseTime(stack, user) - remainingUseTicks);
        }
    }

    @Override
    public void onStoppedUsing(ItemStack stack, World world, LivingEntity user, int remainingUseTicks) {
        if (!(user instanceof PlayerEntity player)) return;
        int used = getMaxUseTime(stack, user) - remainingUseTicks;
        if (world.isClient()) clientRelease(player, used);
        else if (world instanceof ServerWorld server) release(server, player, used, stack);
    }

    /** Cargando el Noble Phantasm: Venus se forma delante de ella, entre anillos dorados y azules. También la flotante. */
    static void chargeTick(ServerWorld server, PlayerEntity player, int used) {
        if (!npReady(player)) return;
        float progress = Math.min(1.0F, used / (float) NP_CHARGE);
        var tip = player.getEyePos().add(player.getRotationVec(1.0F).multiply(1.6));
        for (int i = 0; i < 2; i++) {
            double angle = used * 0.35 + i * Math.PI;
            double r = 1.4 - progress;
            server.spawnParticles(i == 0 ? GOLD : BLUE, tip.x + Math.cos(angle) * r, tip.y + Math.sin(angle) * r * 0.6, tip.z + Math.sin(angle) * r,
                    1, 0.0, 0.0, 0.0, 0.0);
        }
        if (used % 20 == 0 && used > 0 && used <= NP_CHARGE) {
            server.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.BLOCK_BEACON_POWER_SELECT,
                    SoundCategory.PLAYERS, 1.5F, 0.8F + progress * 0.6F);
        }
        if (used == NP_CHARGE) server.spawnParticles(ParticleTypes.FLASH, tip.x, tip.y, tip.z, 1, 0.0, 0.0, 0.0, 0.0);
    }

    /** En el cliente de quien dispara: la postura y el plano de cámara del Noble Phantasm. */
    public static void clientRelease(PlayerEntity player, int used) {
        if (getPullProgress(used) >= 0.1F && npReady(player) && used >= NP_CHARGE) {
            com.nuwuman.fateubw.PlayerAnims.play(player, "bow_hold");
            com.nuwuman.fateubw.PlayerAnims.cinematic(player);
        }
    }

    /** Suelta la cuerda tras {@code used} ticks tensando. {@code bow} es el ítem, o null si es la Maanna flotante. */
    /** Devuelve si ha salido el Noble Phantasm (que sale un segundo después). */
    static boolean release(ServerWorld world, PlayerEntity player, int used, @Nullable ItemStack bow) {
        float pull = getPullProgress(used);
        if (pull < 0.1F) return false;
        if (bow != null) bow.damage(1, player, LivingEntity.getSlotForHand(player.getActiveHand()));
        // Sin recarga o sin maná sale un disparo normal (ready avisa de lo que falte)
        if (player.isSneaking() && used >= NP_CHARGE
                && com.nuwuman.fateubw.Rules.ready(player, FateUBW.AN_GAL_TA_KIGAL_SHE_NP, NP_COOLDOWN, "an_gal_ta_kigal_she")) {
            com.nuwuman.fateubw.Rules.commit(player, FateUBW.AN_GAL_TA_KIGAL_SHE_NP, NP_COOLDOWN);
            // Maanna en alto un segundo mientras la cámara se coloca; luego sale Venus
            com.nuwuman.fateubw.enchant.FateEnchantments.later(com.nuwuman.fateubw.PlayerAnims.WINDUP, () -> {
                if (!player.isAlive() || !(player instanceof ServerPlayerEntity serverPlayer)) return;
                com.nuwuman.fateubw.PlayerAnims.playAll(serverPlayer, "bow_release");
                AnGalTaKigalSheEntity.fire(world, player);
                com.nuwuman.fateubw.Voices.say(world, player, "an_gal_ta_kigal_she");
                world.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.ENTITY_WITHER_SHOOT, SoundCategory.PLAYERS, 1.5F, 1.6F);
                world.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.BLOCK_BEACON_ACTIVATE, SoundCategory.PLAYERS, 2.0F, 1.2F);
            });
            return true;
        }
        if (pull >= 1.0F) {
            // Tensado del todo: una joya que estalla
            RinJewelEntity jewel = new RinJewelEntity(world, player);
            jewel.setVelocity(player, player.getPitch(), player.getYaw(), 0.0F, 2.6F, 0.5F);
            world.spawnEntity(jewel);
            world.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.BLOCK_AMETHYST_BLOCK_RESONATE, SoundCategory.PLAYERS, 1.2F, 1.6F);
        } else {
            ArrowEntity arrow = new ArrowEntity(world, player, new ItemStack(Items.ARROW), bow);
            arrow.setVelocity(player, player.getPitch(), player.getYaw(), 0.0F, pull * 3.0F, 1.0F);
            arrow.pickupType = PersistentProjectileEntity.PickupPermission.DISALLOWED;
            world.spawnEntity(arrow);
        }
        if (bow == null && player instanceof ServerPlayerEntity serverPlayer) com.nuwuman.fateubw.PlayerAnims.playAll(serverPlayer, "bow_release");
        world.playSound(null, player.getX(), player.getY(), player.getZ(),
                SoundEvents.ENTITY_ARROW_SHOOT, SoundCategory.PLAYERS, 1.0F, 1.0F / (world.getRandom().nextFloat() * 0.4F + 1.2F) + pull * 0.5F);
        return false;
    }

    @Override
    public void appendTooltip(ItemStack stack, Item.TooltipContext context, List<Text> tooltip, TooltipType type) {
        tooltip.add(Text.translatable("item.fate_ubw.maanna.tooltip.shoot").formatted(Formatting.GRAY));
        tooltip.add(Text.translatable("item.fate_ubw.maanna.tooltip.np").formatted(Formatting.GOLD));
    }
}
