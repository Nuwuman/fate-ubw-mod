package com.nuwuman.fateubw.gilgamesh;

import com.nuwuman.fateubw.FateUBW;
import com.nuwuman.fateubw.saber.ExcaliburItem;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.SwordItem;
import net.minecraft.item.ToolMaterials;
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
import net.minecraft.util.UseAction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import org.joml.Vector3f;

import java.util.List;

/** Ea, la espada de la ruptura. Mantén para que sus cilindros giren y suelta para Enuma Elish. */
public class EaItem extends SwordItem {
    private static final int COOLDOWN = 20 * 25;
    private static final DustParticleEffect RED = new DustParticleEffect(new Vector3f(0.9F, 0.1F, 0.1F), 1.4F);

    public EaItem(Item.Settings settings) {
        super(ToolMaterials.NETHERITE, settings.attributeModifiers(
                SwordItem.createAttributeModifiers(ToolMaterials.NETHERITE, 7, -2.6F)));
    }

    @Override
    public UseAction getUseAction(ItemStack stack) {
        return UseAction.NONE;
    }

    @Override
    public int getMaxUseTime(ItemStack stack, LivingEntity user) {
        return 72000;
    }

    @Override
    public TypedActionResult<ItemStack> use(World world, PlayerEntity user, Hand hand) {
        ItemStack stack = user.getStackInHand(hand);
        if (user.getItemCooldownManager().isCoolingDown(FateUBW.ENUMA_ELISH_NP)) {
            if (!world.isClient) FateUBW.cooldownMessage(user, FateUBW.ENUMA_ELISH_NP, COOLDOWN, "enuma_elish");
            return TypedActionResult.fail(stack);
        }
        user.setCurrentHand(hand);
        return TypedActionResult.consume(stack);
    }

    // Viento rojo que gira alrededor del portador mientras carga
    @Override
    public void usageTick(World world, LivingEntity user, ItemStack stack, int remainingUseTicks) {
        if (!(world instanceof ServerWorld server)) return;
        int charge = getMaxUseTime(stack, user) - remainingUseTicks;
        float progress = Math.min(1.0F, charge / (float) ExcaliburItem.FULL_CHARGE);
        for (int i = 0; i < 3; i++) {
            double angle = charge * 0.4 + i * Math.PI * 2 / 3;
            double r = 2.2 - progress;
            server.spawnParticles(RED, user.getX() + Math.cos(angle) * r, user.getY() + 0.3 + progress * 1.5,
                    user.getZ() + Math.sin(angle) * r, 1, 0.0, 0.0, 0.0, 0.0);
        }
        if (charge % 20 == 0 && charge > 0 && charge <= ExcaliburItem.FULL_CHARGE) {
            world.playSound(null, user.getX(), user.getY(), user.getZ(), SoundEvents.BLOCK_BEACON_POWER_SELECT,
                    SoundCategory.PLAYERS, 1.5F, 0.5F + progress * 0.5F);
        }
        if (charge == ExcaliburItem.FULL_CHARGE) {
            server.spawnParticles(ParticleTypes.FLASH, user.getX(), user.getEyeY() + 0.5, user.getZ(), 1, 0.0, 0.0, 0.0, 0.0);
        }
    }

    @Override
    public void onStoppedUsing(ItemStack stack, World world, LivingEntity user, int remainingUseTicks) {
        int charge = getMaxUseTime(stack, user) - remainingUseTicks;
        if (charge < ExcaliburItem.FULL_CHARGE || !(user instanceof PlayerEntity player)) return;
        player.getItemCooldownManager().set(FateUBW.ENUMA_ELISH_NP, COOLDOWN);
        if (!(world instanceof ServerWorld server)) return;

        EnumaElishEntity.fire(server, player);
        com.nuwuman.fateubw.Voices.say(world, player, "enuma_elish");
        player.swingHand(player.getActiveHand(), true);
        world.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.ENTITY_WITHER_SPAWN, SoundCategory.PLAYERS, 1.2F, 1.4F);
        world.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.ENTITY_LIGHTNING_BOLT_THUNDER, SoundCategory.PLAYERS, 3.0F, 0.6F);
        Vec3d dir = player.getRotationVec(1.0F);
        player.addVelocity(-dir.x * 0.6, 0.1, -dir.z * 0.6);
        player.velocityModified = true;
    }

    @Override
    public void appendTooltip(ItemStack stack, Item.TooltipContext context, List<Text> tooltip, TooltipType type) {
        tooltip.add(Text.translatable("item.fate_ubw.ea.tooltip").formatted(Formatting.RED));
    }
}
