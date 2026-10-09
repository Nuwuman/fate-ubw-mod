package com.nuwuman.fateubw.archer;

import com.nuwuman.fateubw.FateUBW;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.SwordItem;
import net.minecraft.item.ToolMaterials;
import net.minecraft.item.tooltip.TooltipType;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Hand;
import net.minecraft.util.TypedActionResult;
import net.minecraft.world.World;

import java.util.List;

/** Kanshō (negro) y Bakuya (blanco): sables gemelos que se lanzan y vuelven a la mano. */
public class FalchionItem extends SwordItem {
    private static final int THROW_COOLDOWN = 25;
    public static final int RHO_AIAS_COOLDOWN = 20 * 25;
    private final boolean kanshou;

    public FalchionItem(boolean kanshou, Item.Settings settings) {
        super(ToolMaterials.NETHERITE, settings.attributeModifiers(
                SwordItem.createAttributeModifiers(ToolMaterials.NETHERITE, 3, -2.0F)));
        this.kanshou = kanshou;
    }

    private Item twin() {
        return kanshou ? FateUBW.BAKUYA : FateUBW.KANSHOU;
    }

    @Override
    public TypedActionResult<ItemStack> use(World world, PlayerEntity user, Hand hand) {
        ItemStack stack = user.getStackInHand(hand);

        // Agachado: Rho Aias
        if (user.isSneaking()) {
            if (!com.nuwuman.fateubw.Rules.ready(user, FateUBW.RHO_AIAS, RHO_AIAS_COOLDOWN, "rho_aias")) return TypedActionResult.fail(stack);
            com.nuwuman.fateubw.PlayerAnims.play(user, "rho_aias");
            if (world instanceof ServerWorld server) RhoAiasEntity.deploy(server, user);
            com.nuwuman.fateubw.Voices.say(world, user, "rho_aias");
            com.nuwuman.fateubw.Rules.commit(user, FateUBW.RHO_AIAS, RHO_AIAS_COOLDOWN);
            return TypedActionResult.success(stack, world.isClient());
        }

        // Lanzar. Con la pareja en la otra mano salen las dos y se cruzan en el aire
        ItemStack other = user.getStackInHand(hand == Hand.MAIN_HAND ? Hand.OFF_HAND : Hand.MAIN_HAND);
        boolean pair = other.isOf(twin());
        boolean right = (hand == Hand.MAIN_HAND) == (user.getMainArm() == net.minecraft.util.Arm.RIGHT);
        com.nuwuman.fateubw.PlayerAnims.play(user, pair ? "falchion_throw_both" : right ? "falchion_throw_right" : "falchion_throw_left");
        if (world instanceof ServerWorld server) {
            ThrownFalchionEntity.toss(server, user, stack, kanshou ? -1.0F : 1.0F);
            if (pair) ThrownFalchionEntity.toss(server, user, other, kanshou ? 1.0F : -1.0F);
            world.playSound(null, user.getX(), user.getY(), user.getZ(),
                    SoundEvents.ENTITY_PLAYER_ATTACK_SWEEP, SoundCategory.PLAYERS, 1.0F, 1.4F);
        }
        com.nuwuman.fateubw.Rules.cooldown(user, this, THROW_COOLDOWN);
        if (pair) com.nuwuman.fateubw.Rules.cooldown(user, twin(), THROW_COOLDOWN);
        return TypedActionResult.success(stack, world.isClient());
    }

    // Yin-Yang Resonance: al golpear con uno, a veces la pareja de la otra mano golpea justo después
    @Override
    public boolean postHit(ItemStack stack, LivingEntity target, LivingEntity attacker) {
        int level = com.nuwuman.fateubw.enchant.FateEnchantments.level(attacker.getWorld(), stack,
                com.nuwuman.fateubw.enchant.FateEnchantments.YIN_YANG_RESONANCE);
        Hand other = attacker.getMainHandStack() == stack ? Hand.OFF_HAND : Hand.MAIN_HAND;
        if (level > 0 && attacker.getWorld() instanceof ServerWorld world && attacker.getStackInHand(other).isOf(twin())
                && world.random.nextFloat() < 0.15F * level) {
            com.nuwuman.fateubw.enchant.FateEnchantments.later(4, () -> {
                if (!target.isAlive() || !attacker.isAlive()) return;
                attacker.swingHand(other, true);
                target.timeUntilRegen = 0;
                target.damage(attacker instanceof PlayerEntity player ? world.getDamageSources().playerAttack(player)
                        : world.getDamageSources().mobAttack(attacker), 5.0F + level);
                world.spawnParticles(net.minecraft.particle.ParticleTypes.SWEEP_ATTACK, target.getX(), target.getBodyY(0.5), target.getZ(), 1, 0.0, 0.0, 0.0, 0.0);
                world.playSound(null, target.getX(), target.getY(), target.getZ(), SoundEvents.ENTITY_PLAYER_ATTACK_CRIT, SoundCategory.PLAYERS, 1.0F, 1.3F);
            });
        }
        return super.postHit(stack, target, attacker);
    }

    @Override
    public void appendTooltip(ItemStack stack, Item.TooltipContext context, List<Text> tooltip, TooltipType type) {
        tooltip.add(Text.translatable("item.fate_ubw.falchion.tooltip.throw").formatted(Formatting.GRAY));
        tooltip.add(Text.translatable("item.fate_ubw.falchion.tooltip.pair").formatted(Formatting.GOLD));
        tooltip.add(Text.translatable("item.fate_ubw.falchion.tooltip.rho_aias").formatted(Formatting.LIGHT_PURPLE));
    }
}
