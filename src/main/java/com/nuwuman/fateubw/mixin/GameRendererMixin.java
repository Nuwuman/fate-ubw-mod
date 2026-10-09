package com.nuwuman.fateubw.mixin;

import com.nuwuman.fateubw.client.NpCamera;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.GameRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Golpe de campo de visión al lanzar un Noble Phantasm (solo el del mundo, no el de la mano). */
@Mixin(GameRenderer.class)
public abstract class GameRendererMixin {
    @Inject(method = "getFov", at = @At("RETURN"), cancellable = true)
    private void fateubw$npFovPunch(Camera camera, float tickDelta, boolean changingFov, CallbackInfoReturnable<Double> cir) {
        float punch = NpCamera.fovPunch();
        if (changingFov && punch > 0.0F) cir.setReturnValue(cir.getReturnValue() + punch);
    }
}
