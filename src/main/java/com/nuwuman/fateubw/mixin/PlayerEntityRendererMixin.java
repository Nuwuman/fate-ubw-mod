package com.nuwuman.fateubw.mixin;

import com.nuwuman.fateubw.ServantArmorItem;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.render.entity.PlayerEntityRenderer;
import net.minecraft.client.render.entity.model.PlayerEntityModel;
import net.minecraft.entity.EquipmentSlot;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Las capas exteriores de la skin atraviesan la armadura de servant: se ocultan las de las partes que cubre. */
@Mixin(PlayerEntityRenderer.class)
public abstract class PlayerEntityRendererMixin {
    @Inject(method = "setModelPose", at = @At("TAIL"))
    private void fate_ubw$hideSkinLayers(AbstractClientPlayerEntity player, CallbackInfo ci) {
        PlayerEntityModel<AbstractClientPlayerEntity> model = ((PlayerEntityRenderer) (Object) this).getModel();
        if (covered(player, EquipmentSlot.HEAD)) model.hat.visible = false;
        if (covered(player, EquipmentSlot.CHEST)) {
            model.jacket.visible = false;
            model.leftSleeve.visible = false;
            model.rightSleeve.visible = false;
        }
        if (covered(player, EquipmentSlot.LEGS) || covered(player, EquipmentSlot.FEET)) {
            model.leftPants.visible = false;
            model.rightPants.visible = false;
        }
    }

    private static boolean covered(AbstractClientPlayerEntity player, EquipmentSlot slot) {
        return player.getEquippedStack(slot).getItem() instanceof ServantArmorItem;
    }
}
