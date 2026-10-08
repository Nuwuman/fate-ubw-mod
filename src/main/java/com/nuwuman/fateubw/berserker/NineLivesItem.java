package com.nuwuman.fateubw.berserker;

import com.nuwuman.fateubw.FateUBW;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.SwordItem;
import net.minecraft.item.ToolMaterials;
import net.minecraft.item.tooltip.TooltipType;
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

import java.util.List;

/**
 * La hacha-espada de piedra de Heracles. Mantén 1,5 s y suelta: Nine Lives (Disparar a las Cien Cabezas),
 * nueve golpes en un instante a todo lo que tenga delante.
 */
public class NineLivesItem extends SwordItem {
    public static final int CHARGE = 30;
    public static final int COOLDOWN = 20 * 25;
    private static final int HITS = 9;
    private static final float HIT_DAMAGE = 4.0F;

    public NineLivesItem(Item.Settings settings) {
        super(ToolMaterials.NETHERITE, settings.attributeModifiers(
                SwordItem.createAttributeModifiers(ToolMaterials.NETHERITE, 9, -3.0F)));
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
        if (!com.nuwuman.fateubw.Rules.ready(user, FateUBW.NINE_LIVES, COOLDOWN, "nine_lives")) return TypedActionResult.fail(stack);
        user.setCurrentHand(hand);
        return TypedActionResult.consume(stack);
    }

    @Override
    public void usageTick(World world, LivingEntity user, ItemStack stack, int remainingUseTicks) {
        if (!(world instanceof ServerWorld server)) return;
        int charge = getMaxUseTime(stack, user) - remainingUseTicks;
        if (charge % 3 == 0) server.spawnParticles(ParticleTypes.ANGRY_VILLAGER, user.getX(), user.getEyeY() + 0.3, user.getZ(), 1, 0.4, 0.2, 0.4, 0.0);
        if (charge == CHARGE) {
            world.playSound(null, user.getX(), user.getY(), user.getZ(), SoundEvents.ENTITY_RAVAGER_ROAR, SoundCategory.PLAYERS, 1.2F, 0.8F);
        }
    }

    @Override
    public void onStoppedUsing(ItemStack stack, World world, LivingEntity user, int remainingUseTicks) {
        if (!(user instanceof PlayerEntity player) || getMaxUseTime(stack, user) - remainingUseTicks < CHARGE) return;
        com.nuwuman.fateubw.Rules.commit(player, FateUBW.NINE_LIVES, COOLDOWN);
        if (!(world instanceof ServerWorld server)) return;
        player.swingHand(player.getActiveHand(), true);
        nineLives(server, player);
    }

    private static void nineLives(ServerWorld world, PlayerEntity player) {
        Vec3d look = player.getRotationVec(1.0F);
        Vec3d dir = new Vec3d(look.x, 0.0, look.z).normalize();
        player.setVelocity(dir.x * 1.2, 0.1, dir.z * 1.2);
        player.velocityModified = true;
        Vec3d center = player.getPos().add(dir.multiply(3.0));
        for (LivingEntity target : world.getEntitiesByClass(LivingEntity.class, player.getBoundingBox().stretch(dir.multiply(6.0)).expand(2.5),
                e -> e != player && e.isAlive() && com.nuwuman.fateubw.Rules.canAffect(player, e) && e.getPos().subtract(player.getPos()).normalize().dotProduct(dir) > 0.3)) {
            for (int i = 0; i < HITS; i++) {
                target.timeUntilRegen = 0;
                target.damage(world.getDamageSources().playerAttack(player), HIT_DAMAGE);
            }
            target.takeKnockback(2.5, -dir.x, -dir.z);
        }
        for (int i = 0; i < HITS; i++) {
            double angle = i * Math.PI * 2 / HITS;
            world.spawnParticles(ParticleTypes.SWEEP_ATTACK, center.x + Math.cos(angle) * 1.5, center.y + 0.5 + (i % 3) * 0.6,
                    center.z + Math.sin(angle) * 1.5, 1, 0.0, 0.0, 0.0, 0.0);
        }
        world.spawnParticles(ParticleTypes.EXPLOSION, center.x, center.y + 1.0, center.z, 3, 1.0, 0.5, 1.0, 0.0);
        world.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.ENTITY_IRON_GOLEM_ATTACK, SoundCategory.PLAYERS, 2.0F, 0.5F);
        world.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.ENTITY_RAVAGER_ATTACK, SoundCategory.PLAYERS, 2.0F, 0.7F);
        world.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.ENTITY_GENERIC_EXPLODE.value(), SoundCategory.PLAYERS, 1.0F, 1.2F);
        player.sendMessage(Text.literal("Nine Lives").formatted(Formatting.DARK_RED, Formatting.BOLD), true);
        com.nuwuman.fateubw.Voices.say(world, player, "nine_lives");
    }

    @Override
    public void appendTooltip(ItemStack stack, Item.TooltipContext context, List<Text> tooltip, TooltipType type) {
        tooltip.add(Text.translatable("item.fate_ubw.berserker_axe_sword.tooltip").formatted(Formatting.DARK_RED));
    }
}
