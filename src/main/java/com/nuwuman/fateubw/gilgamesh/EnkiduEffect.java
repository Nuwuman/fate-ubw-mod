package com.nuwuman.fateubw.gilgamesh;

import com.nuwuman.fateubw.FateUBW;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentSyncPredicate;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.attribute.EntityAttributeModifier;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.effect.StatusEffect;
import net.minecraft.entity.effect.StatusEffectCategory;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.util.math.Vec3d;

/**
 * Cadenas del Cielo (Enkidu): atado de verdad. Sin moverse, sin saltar, sin volar ni planear, y golpeando más despacio.
 * Las cadenas se ven gracias a BOUND_UNTIL, sincronizado con todos (los efectos de otras entidades no llegan al cliente).
 */
public class EnkiduEffect extends StatusEffect {
    /** Tick del mundo en que se sueltan las cadenas. */
    public static final AttachmentType<Long> BOUND_UNTIL = AttachmentRegistry.<Long>builder()
            .syncWith(PacketCodecs.VAR_LONG.cast(), AttachmentSyncPredicate.all())
            .buildAndRegister(FateUBW.id("enkidu_until"));
    /** Cuánto dura atado. */
    public static final int TICKS = 100;

    public EnkiduEffect() {
        super(StatusEffectCategory.HARMFUL, 0xd9b44a);
        addAttributeModifier(EntityAttributes.GENERIC_MOVEMENT_SPEED, FateUBW.id("enkidu_speed"), -1.0, EntityAttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
        addAttributeModifier(EntityAttributes.GENERIC_FLYING_SPEED, FateUBW.id("enkidu_flying"), -1.0, EntityAttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
        addAttributeModifier(EntityAttributes.GENERIC_JUMP_STRENGTH, FateUBW.id("enkidu_jump"), -1.0, EntityAttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
        addAttributeModifier(EntityAttributes.GENERIC_ATTACK_SPEED, FateUBW.id("enkidu_attack"), -0.5, EntityAttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
    }

    /** Dónde se abren los portales de los que salen las cadenas: cuatro alrededor y por encima del atado. */
    public static Vec3d[] anchors(LivingEntity target) {
        double r = 2.2 + target.getWidth(), top = target.getY() + target.getHeight() + 1.5;
        Vec3d[] out = new Vec3d[4];
        for (int i = 0; i < 4; i++) {
            double a = Math.PI / 4 + i * Math.PI / 2;
            out[i] = new Vec3d(target.getX() + Math.cos(a) * r, top - (i % 2) * 1.2, target.getZ() + Math.sin(a) * r);
        }
        return out;
    }

    @Override
    public boolean canApplyUpdateEffect(int duration, int amplifier) {
        return true;
    }

    // Lo que los atributos no paran: el empuje, los élitros y los mobs que vuelan o nadan por su cuenta
    @Override
    public boolean applyUpdateEffect(LivingEntity entity, int amplifier) {
        if (entity.getWorld().isClient()) return true;
        if (entity instanceof PlayerEntity player) {
            if (player.isFallFlying()) player.stopFallFlying();
            return true;
        }
        Vec3d v = entity.getVelocity();
        entity.setVelocity(0.0, Math.min(v.y, 0.0), 0.0);
        return true;
    }
}
