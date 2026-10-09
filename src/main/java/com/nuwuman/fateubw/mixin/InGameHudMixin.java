package com.nuwuman.fateubw.mixin;

import com.nuwuman.fateubw.client.AbilityHud;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.hud.InGameHud;
import net.minecraft.client.render.RenderTickCounter;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** El HUD de habilidades se dibuja al final, por encima del chat; las bandas de cine, al principio. */
@Mixin(InGameHud.class)
public abstract class InGameHudMixin {
    // Las bandas de cine del plano de Noble Phantasm, debajo del resto del HUD
    @Inject(method = "render", at = @At("HEAD"))
    private void fateubw$cinemaBars(DrawContext context, RenderTickCounter tickCounter, CallbackInfo ci) {
        com.nuwuman.fateubw.client.NpCamera.drawBars(context);
    }

    @Inject(method = "render", at = @At("TAIL"))
    private void fateubw$abilities(DrawContext context, RenderTickCounter tickCounter, CallbackInfo ci) {
        AbilityHud.render(context, tickCounter);
    }
}
