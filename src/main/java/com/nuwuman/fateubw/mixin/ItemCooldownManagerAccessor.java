package com.nuwuman.fateubw.mixin;

import net.minecraft.entity.player.ItemCooldownManager;
import net.minecraft.item.Item;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.Map;

/** Para que el HUD sepa cuántos ticks quedan de verdad (con fateCooldownPercent la duración cambia). */
@Mixin(ItemCooldownManager.class)
public interface ItemCooldownManagerAccessor {
    @Accessor("entries")
    Map<Item, ?> fateubw$entries();

    @Accessor("tick")
    int fateubw$tick();
}
