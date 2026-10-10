package com.nuwuman.fateubw.mash;

import com.nuwuman.fateubw.FateUBW;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.attribute.EntityAttributeModifier;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.component.type.AttributeModifierSlot;
import net.minecraft.component.type.AttributeModifiersComponent;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.ShieldItem;
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
 * El escudo de Mash (Lord Chaldeas). Clic derecho: cubrirse como con un escudo (sin gastarse). Golpe: un porrazo con
 * mucho empuje. Agachada, cubriéndose 2 s y soltando: Lord Camelot, el muro de las murallas de Camelot.
 */
public class MashShieldItem extends ShieldItem {
    public static final int NP_CHARGE = 40;
    public static final int NP_COOLDOWN = 20 * 30;
    private static final DustParticleEffect LIGHT = new DustParticleEffect(new Vector3f(0.75F, 0.85F, 1.0F), 1.2F);

    public MashShieldItem(Item.Settings settings) {
        super(settings.attributeModifiers(AttributeModifiersComponent.builder()
                .add(EntityAttributes.GENERIC_ATTACK_DAMAGE, new EntityAttributeModifier(BASE_ATTACK_DAMAGE_MODIFIER_ID, 5.0,
                        EntityAttributeModifier.Operation.ADD_VALUE), AttributeModifierSlot.MAINHAND)
                .add(EntityAttributes.GENERIC_ATTACK_SPEED, new EntityAttributeModifier(BASE_ATTACK_SPEED_MODIFIER_ID, -3.0,
                        EntityAttributeModifier.Operation.ADD_VALUE), AttributeModifierSlot.MAINHAND)
                .build()));
    }

    // El porrazo con el escudo: poco daño, mucho empuje
    @Override
    public boolean postHit(ItemStack stack, LivingEntity target, LivingEntity attacker) {
        Vec3d dir = target.getPos().subtract(attacker.getPos()).multiply(1, 0, 1);
        if (dir.lengthSquared() > 1e-4) {
            dir = dir.normalize();
            target.takeKnockback(1.6, -dir.x, -dir.z);
        }
        attacker.getWorld().playSound(null, target.getX(), target.getY(), target.getZ(), SoundEvents.ITEM_SHIELD_BLOCK, SoundCategory.PLAYERS, 1.0F, 0.6F);
        return true;
    }

    @Override
    public void usageTick(World world, LivingEntity user, ItemStack stack, int remainingUseTicks) {
        if (!(world instanceof ServerWorld server) || !(user instanceof PlayerEntity player) || !player.isSneaking()) return;
        int used = getMaxUseTime(stack, user) - remainingUseTicks;
        if (player.getItemCooldownManager().isCoolingDown(FateUBW.LORD_CAMELOT_NP)) return;
        // Cargando Lord Camelot: luz que sube del escudo plantado
        Vec3d front = player.getPos().add(player.getRotationVector().multiply(1, 0, 1).normalize().multiply(1.0));
        server.spawnParticles(LIGHT, front.x, player.getY() + 0.2 + Math.min(used, NP_CHARGE) / (double) NP_CHARGE * 2.0, front.z, 3, 0.5, 0.2, 0.5, 0.0);
        if (used == NP_CHARGE) {
            server.spawnParticles(ParticleTypes.END_ROD, front.x, player.getY() + 1.0, front.z, 30, 0.6, 1.0, 0.6, 0.05);
            server.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.BLOCK_BEACON_ACTIVATE, SoundCategory.PLAYERS, 1.2F, 1.4F);
        }
    }

    @Override
    public void onStoppedUsing(ItemStack stack, World world, LivingEntity user, int remainingUseTicks) {
        if (!(user instanceof ServerPlayerEntity player) || !(world instanceof ServerWorld server)) return;
        int used = getMaxUseTime(stack, user) - remainingUseTicks;
        if (!player.isSneaking() || used < NP_CHARGE) return;
        if (!com.nuwuman.fateubw.Rules.ready(player, FateUBW.LORD_CAMELOT_NP, NP_COOLDOWN, "lord_camelot")) return;
        com.nuwuman.fateubw.Rules.commit(player, FateUBW.LORD_CAMELOT_NP, NP_COOLDOWN);
        com.nuwuman.fateubw.PlayerAnims.playAll(player, "lord_camelot");
        com.nuwuman.fateubw.Voices.say(server, player, "lord_camelot");
        LordCamelotEntity.raise(server, player);
    }

    @Override
    public void appendTooltip(ItemStack stack, Item.TooltipContext context, List<Text> tooltip, TooltipType type) {
        tooltip.add(Text.translatable("item.fate_ubw.mash_shield.tooltip.block").formatted(Formatting.GRAY));
        tooltip.add(Text.translatable("item.fate_ubw.mash_shield.tooltip.np").formatted(Formatting.GOLD));
    }
}
