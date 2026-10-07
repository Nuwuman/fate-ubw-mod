package com.nuwuman.fateubw.mixin;

import com.nuwuman.fateubw.FateUBW;
import com.nuwuman.fateubw.saber.ExcaliburItem;
import net.minecraft.client.model.ModelPart;
import net.minecraft.client.render.entity.model.BipedEntityModel;
import net.minecraft.entity.LivingEntity;
import net.minecraft.util.math.MathHelper;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Mientras se carga Excalibur, el portador la alza con las dos manos: de delante hasta encima de la cabeza. */
@Mixin(BipedEntityModel.class)
public abstract class BipedEntityModelMixin<T extends LivingEntity> {
    @Shadow @Final public ModelPart rightArm;
    @Shadow @Final public ModelPart leftArm;

    @Inject(method = "setAngles(Lnet/minecraft/entity/LivingEntity;FFFFF)V", at = @At("TAIL"))
    private void fate_ubw$raiseSword(T entity, float limbAngle, float limbDistance, float animationProgress,
                                     float headYaw, float headPitch, CallbackInfo ci) {
        if (!entity.isUsingItem() || !entity.getActiveItem().isOf(FateUBW.EXCALIBUR)) return;

        float pitch = MathHelper.lerp(ExcaliburItem.chargeProgress(entity), -1.5F, -2.9F);
        rightArm.pitch = pitch;
        leftArm.pitch = pitch;
        rightArm.yaw = -0.4F;
        leftArm.yaw = 0.4F;
        rightArm.roll = 0.0F;
        leftArm.roll = 0.0F;
    }
}
