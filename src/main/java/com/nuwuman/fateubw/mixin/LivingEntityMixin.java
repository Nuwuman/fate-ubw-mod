package com.nuwuman.fateubw.mixin;

import com.llamalad7.mixinextras.sugar.Local;
import com.nuwuman.fateubw.Rules;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.damage.DamageSource;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/** /gamerule fatePlayerDamagePercent: escala el daño de armas y habilidades del mod entre jugadores. */
@Mixin(LivingEntity.class)
public abstract class LivingEntityMixin {
    @ModifyVariable(method = "damage", at = @At("HEAD"), argsOnly = true)
    private float fateubw$playerDamage(float amount, @Local(argsOnly = true) DamageSource source) {
        return Rules.playerDamage((LivingEntity) (Object) this, source, amount);
    }
}
