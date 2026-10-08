package com.nuwuman.fateubw.mixin;

import com.nuwuman.fateubw.archer.TraceOnItem;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.inventory.Inventory;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.slot.Slot;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Las proyecciones de Trace On solo existen en manos de quien las proyecta: no entran en cofres, mesas ni yunques. */
@Mixin(Slot.class)
public abstract class SlotMixin {
    @Shadow
    @Final
    public Inventory inventory;

    @Inject(method = "canInsert", at = @At("HEAD"), cancellable = true)
    private void fateubw$keepProjections(ItemStack stack, CallbackInfoReturnable<Boolean> cir) {
        if (!(inventory instanceof PlayerInventory) && TraceOnItem.isProjection(stack)) cir.setReturnValue(false);
    }
}
