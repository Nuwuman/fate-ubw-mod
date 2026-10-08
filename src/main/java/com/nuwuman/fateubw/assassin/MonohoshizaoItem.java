package com.nuwuman.fateubw.assassin;

import com.nuwuman.fateubw.FateUBW;
import com.nuwuman.fateubw.lancer.GaeBolgItem;
import net.minecraft.component.type.AttributeModifierSlot;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.attribute.EntityAttributeModifier;
import net.minecraft.entity.attribute.EntityAttributes;
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
 * Monohoshizao, la "pértiga de secar ropa" de Sasaki Kojirō: una nodachi larguísima (más alcance).
 * Mantén 1 s y suelta: Tsubame Gaeshi, tres cortes que caen a la vez y no se pueden esquivar.
 */
public class MonohoshizaoItem extends SwordItem {
    public static final int CHARGE = 20;
    public static final int COOLDOWN = 20 * 15;
    private static final float CUT_DAMAGE = 9.0F;
    private static final double RANGE = 7.0;

    public MonohoshizaoItem(Item.Settings settings) {
        super(ToolMaterials.NETHERITE, settings.attributeModifiers(
                SwordItem.createAttributeModifiers(ToolMaterials.NETHERITE, 4, -2.2F).with(
                        EntityAttributes.PLAYER_ENTITY_INTERACTION_RANGE,
                        new EntityAttributeModifier(FateUBW.id("monohoshizao_reach"), 2.0, EntityAttributeModifier.Operation.ADD_VALUE),
                        AttributeModifierSlot.MAINHAND)));
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
        if (!com.nuwuman.fateubw.Rules.ready(user, FateUBW.TSUBAME_GAESHI, COOLDOWN, "tsubame_gaeshi")) return TypedActionResult.fail(stack);
        user.setCurrentHand(hand);
        return TypedActionResult.consume(stack);
    }

    // Se concentra: el viento se arremolina alrededor de la hoja
    @Override
    public void usageTick(World world, LivingEntity user, ItemStack stack, int remainingUseTicks) {
        if (!(world instanceof ServerWorld server)) return;
        int charge = getMaxUseTime(stack, user) - remainingUseTicks;
        double angle = charge * 0.5;
        server.spawnParticles(ParticleTypes.CLOUD, user.getX() + Math.cos(angle) * 1.2, user.getBodyY(0.6), user.getZ() + Math.sin(angle) * 1.2,
                1, 0.0, 0.0, 0.0, 0.0);
        if (charge == CHARGE) {
            world.playSound(null, user.getX(), user.getY(), user.getZ(), SoundEvents.ITEM_TRIDENT_RIPTIDE_1.value(), SoundCategory.PLAYERS, 1.0F, 1.6F);
        }
    }

    @Override
    public void onStoppedUsing(ItemStack stack, World world, LivingEntity user, int remainingUseTicks) {
        if (!(user instanceof PlayerEntity player) || getMaxUseTime(stack, user) - remainingUseTicks < CHARGE) return;
        com.nuwuman.fateubw.Rules.commit(player, FateUBW.TSUBAME_GAESHI, COOLDOWN);
        com.nuwuman.fateubw.PlayerAnims.play(player, "tsubame_gaeshi");
        if (!(world instanceof ServerWorld server)) return;
        player.swingHand(player.getActiveHand(), true);
        tsubameGaeshi(server, player);
    }

    // Tres cortes en el mismo instante: de arriba, de lado y en diagonal. Ninguno espera a que pase la invulnerabilidad
    private static void tsubameGaeshi(ServerWorld world, PlayerEntity player) {
        LivingEntity main = GaeBolgItem.findTarget(world, player, RANGE, 0.85);
        Vec3d eye = player.getEyePos();
        Vec3d dir = player.getRotationVec(1.0F);
        List<LivingEntity> targets = main != null ? List.of(main)
                : world.getEntitiesByClass(LivingEntity.class, player.getBoundingBox().stretch(dir.multiply(5.0)).expand(1.5),
                e -> e != player && e.isAlive() && com.nuwuman.fateubw.Rules.canAffect(player, e) && e.getBoundingBox().getCenter().subtract(eye).normalize().dotProduct(dir) > 0.5);
        for (LivingEntity target : targets) {
            for (int cut = 0; cut < 3; cut++) {
                target.timeUntilRegen = 0;
                target.damage(world.getDamageSources().playerAttack(player), CUT_DAMAGE);
            }
        }
        Vec3d at = main != null ? main.getBoundingBox().getCenter() : eye.add(dir.multiply(3.0));
        for (int cut = 0; cut < 3; cut++) {
            world.spawnParticles(ParticleTypes.SWEEP_ATTACK, at.x, at.y - 0.4 + cut * 0.4, at.z, 1, 0.0, 0.0, 0.0, 0.0);
        }
        world.spawnParticles(ParticleTypes.CRIT, at.x, at.y, at.z, 30, 0.4, 0.6, 0.4, 0.4);
        world.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.ENTITY_PLAYER_ATTACK_SWEEP, SoundCategory.PLAYERS, 1.5F, 1.4F);
        world.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.ENTITY_PLAYER_ATTACK_SWEEP, SoundCategory.PLAYERS, 1.5F, 1.0F);
        world.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.ENTITY_PLAYER_ATTACK_CRIT, SoundCategory.PLAYERS, 1.5F, 0.8F);
        player.sendMessage(Text.literal("Tsubame Gaeshi").formatted(Formatting.AQUA, Formatting.BOLD), true);
        com.nuwuman.fateubw.Voices.say(world, player, "tsubame_gaeshi");
    }

    @Override
    public void appendTooltip(ItemStack stack, Item.TooltipContext context, List<Text> tooltip, TooltipType type) {
        tooltip.add(Text.translatable("item.fate_ubw.monohoshizao.tooltip.reach").formatted(Formatting.GRAY));
        tooltip.add(Text.translatable("item.fate_ubw.monohoshizao.tooltip.tsubame").formatted(Formatting.AQUA));
    }
}
