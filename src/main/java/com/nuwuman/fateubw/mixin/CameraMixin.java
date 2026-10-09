package com.nuwuman.fateubw.mixin;

import com.nuwuman.fateubw.client.NpCamera;
import net.minecraft.client.render.Camera;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

/** Aleja la cámara de tercera persona durante el plano de un Noble Phantasm. */
@Mixin(Camera.class)
public abstract class CameraMixin {
    @ModifyArg(method = "update", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/render/Camera;clipToSpace(F)F"))
    private float fateubw$npDistance(float distance) {
        return distance * NpCamera.distanceFactor();
    }
}
