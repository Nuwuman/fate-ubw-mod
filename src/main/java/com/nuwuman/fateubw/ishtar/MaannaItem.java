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
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Hand;
import net.minecraft.util.TypedActionResult;
import net.minecraft.world.World;
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

    private static boolean npReady(PlayerEntity player) {
        return player.isSneaking() && !player.getItemCooldownManager().isCoolingDown(FateUBW.AN_GAL_TA_KIGAL_SHE_NP);
    }

    // Cargando el Noble Phantasm: Venus se forma delante de ella, entre anillos dorados y azules
    @Override
    public void usageTick(World world, LivingEntity user, ItemStack stack, int remainingUseTicks) {
        if (!(world instanceof ServerWorld server) || !(user instanceof PlayerEntity player) || !npReady(player)) return;
        int used = getMaxUseTime(stack, user) - remainingUseTicks;
        float progress = Math.min(1.0F, used / (float) NP_CHARGE);
        var tip = player.getEyePos().add(player.getRotationVec(1.0F).multiply(1.6));
        for (int i = 0; i < 2; i++) {
            double angle = used * 0.35 + i * Math.PI;
            double r = 1.4 - progress;
            server.spawnParticles(i == 0 ? GOLD : BLUE, tip.x + Math.cos(angle) * r, tip.y + Math.sin(angle) * r * 0.6, tip.z + Math.sin(angle) * r,
                    1, 0.0, 0.0, 0.0, 0.0);
        }
        if (used % 20 == 0 && used > 0 && used <= NP_CHARGE) {
            world.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.BLOCK_BEACON_POWER_SELECT,
                    SoundCategory.PLAYERS, 1.5F, 0.8F + progress * 0.6F);
        }
        if (used == NP_CHARGE) server.spawnParticles(ParticleTypes.FLASH, tip.x, tip.y, tip.z, 1, 0.0, 0.0, 0.0, 0.0);
    }

    @Override
    public void onStoppedUsing(ItemStack stack, World world, LivingEntity user, int remainingUseTicks) {
        if (!(user instanceof PlayerEntity player)) return;
        int used = getMaxUseTime(stack, user) - remainingUseTicks;
        float pull = getPullProgress(used);
        if (pull < 0.1F) return;
        if (npReady(player) && used >= NP_CHARGE) {
            com.nuwuman.fateubw.PlayerAnims.play(player, "bow_hold");
            com.nuwuman.fateubw.PlayerAnims.cinematic(player);
        }
        if (!(world instanceof ServerWorld server)) return;

        // Sin recarga o sin maná sale un disparo normal (ready avisa de lo que falte)
        if (player.isSneaking() && used >= NP_CHARGE
                && com.nuwuman.fateubw.Rules.ready(player, FateUBW.AN_GAL_TA_KIGAL_SHE_NP, NP_COOLDOWN, "an_gal_ta_kigal_she")) {
            com.nuwuman.fateubw.Rules.commit(player, FateUBW.AN_GAL_TA_KIGAL_SHE_NP, NP_COOLDOWN);
            stack.damage(1, player, LivingEntity.getSlotForHand(player.getActiveHand()));
            // Maanna en alto un segundo mientras la cámara se coloca; luego sale Venus
            com.nuwuman.fateubw.enchant.FateEnchantments.later(com.nuwuman.fateubw.PlayerAnims.WINDUP, () -> {
                if (!player.isAlive() || !(player instanceof net.minecraft.server.network.ServerPlayerEntity serverPlayer)) return;
                com.nuwuman.fateubw.PlayerAnims.playAll(serverPlayer, "bow_release");
                AnGalTaKigalSheEntity.fire(server, player);
                com.nuwuman.fateubw.Voices.say(world, player, "an_gal_ta_kigal_she");
                world.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.ENTITY_WITHER_SHOOT, SoundCategory.PLAYERS, 1.5F, 1.6F);
                world.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.BLOCK_BEACON_ACTIVATE, SoundCategory.PLAYERS, 2.0F, 1.2F);
            });
            return;
        } else if (pull >= 1.0F) {
            // Tensado del todo: una joya que estalla
            RinJewelEntity jewel = new RinJewelEntity(server, player);
            jewel.setVelocity(player, player.getPitch(), player.getYaw(), 0.0F, 2.6F, 0.5F);
            server.spawnEntity(jewel);
            world.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.BLOCK_AMETHYST_BLOCK_RESONATE, SoundCategory.PLAYERS, 1.2F, 1.6F);
        } else {
            ArrowEntity arrow = new ArrowEntity(server, player, new ItemStack(Items.ARROW), stack);
            arrow.setVelocity(player, player.getPitch(), player.getYaw(), 0.0F, pull * 3.0F, 1.0F);
            arrow.pickupType = PersistentProjectileEntity.PickupPermission.DISALLOWED;
            server.spawnEntity(arrow);
        }
        stack.damage(1, player, LivingEntity.getSlotForHand(player.getActiveHand()));
        world.playSound(null, player.getX(), player.getY(), player.getZ(),
                SoundEvents.ENTITY_ARROW_SHOOT, SoundCategory.PLAYERS, 1.0F, 1.0F / (world.getRandom().nextFloat() * 0.4F + 1.2F) + pull * 0.5F);
    }

    @Override
    public void appendTooltip(ItemStack stack, Item.TooltipContext context, List<Text> tooltip, TooltipType type) {
        tooltip.add(Text.translatable("item.fate_ubw.maanna.tooltip.shoot").formatted(Formatting.GRAY));
        tooltip.add(Text.translatable("item.fate_ubw.maanna.tooltip.np").formatted(Formatting.GOLD));
    }
}
