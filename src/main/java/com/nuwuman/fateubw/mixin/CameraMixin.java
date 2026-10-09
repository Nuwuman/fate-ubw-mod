package com.nuwuman.fateubw.mixin;

import com.nuwuman.fateubw.client.NpCamera;
import net.minecraft.client.render.Camera;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Plano de un Noble Phantasm: la cámara de tercera persona se aleja y gira hasta quedar detrás a la derecha. */
@Mixin(Camera.class)
public abstract class CameraMixin {
    @Shadow
    private boolean thirdPerson;

    @Shadow
    public abstract float getYaw();

    @Shadow
    public abstract float getPitch();

    @Shadow
    protected abstract void setRotation(float yaw, float pitch);

    // Girar la vista a la izquierda y bajarla antes de retroceder deja la cámara detrás, a la derecha y en alto
    @Inject(method = "update", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/render/Camera;setRotation(FF)V", ordinal = 0, shift = At.Shift.AFTER))
    private void fateubw$npOrbit(CallbackInfo ci) {
        float orbit = NpCamera.orbit();
        if (thirdPerson && orbit != 0.0F) setRotation(getYaw() - orbit, Math.min(90.0F, getPitch() + NpCamera.tilt()));
    }

    @ModifyArg(method = "update", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/render/Camera;clipToSpace(F)F"))
    private float fateubw$npDistance(float distance) {
        return distance * NpCamera.distanceFactor();
    }
}
