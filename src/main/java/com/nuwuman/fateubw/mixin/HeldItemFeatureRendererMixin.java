package com.nuwuman.fateubw.mixin;

import com.nuwuman.fateubw.assassin.AssassinArmorItem;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.feature.HeldItemFeatureRenderer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Con Ocultación de Presencia, Assassin esconde también el arma que lleva en la mano. */
@Mixin(HeldItemFeatureRenderer.class)
public abstract class HeldItemFeatureRendererMixin {
    @Inject(method = "render(Lnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/VertexConsumerProvider;ILnet/minecraft/entity/LivingEntity;FFFFFF)V",
            at = @At("HEAD"), cancellable = true)
    private void fateubw$concealed(MatrixStack matrices, VertexConsumerProvider vertexConsumers, int light, LivingEntity entity,
                                   float limbAngle, float limbDistance, float tickDelta, float animationProgress,
                                   float headYaw, float headPitch, CallbackInfo ci) {
        if (entity.isInvisible() && AssassinArmorItem.fullSet(entity)) ci.cancel();
    }
}
