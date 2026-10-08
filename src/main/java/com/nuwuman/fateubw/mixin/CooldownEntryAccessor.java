package com.nuwuman.fateubw.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(targets = "net.minecraft.entity.player.ItemCooldownManager$Entry")
public interface CooldownEntryAccessor {
    @Accessor("endTick")
    int fateubw$endTick();
}
