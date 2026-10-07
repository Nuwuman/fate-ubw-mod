package com.nuwuman.fateubw.client;

import com.nuwuman.fateubw.FateUBW;
import com.nuwuman.fateubw.saber.ExcaliburItem;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;
import net.minecraft.client.item.ModelPredicateProviderRegistry;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Identifier;
import org.jetbrains.annotations.Nullable;

public class FateUBWClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        EntityRendererRegistry.register(FateUBW.BEAM, ExcaliburBeamRenderer::new);
        EntityRendererRegistry.register(FateUBW.THROWN_FALCHION, ThrownFalchionRenderer::new);
        EntityRendererRegistry.register(FateUBW.SWORD_ARROW_ENTITY, SwordArrowRenderer::new);
        EntityRendererRegistry.register(FateUBW.RHO_AIAS_ENTITY, RhoAiasRenderer::new);

        // Excalibur: normal / cargando / cargada
        ModelPredicateProviderRegistry.register(FateUBW.EXCALIBUR, FateUBW.id("charge"),
                (stack, world, entity, seed) -> using(entity, stack) ? ExcaliburItem.chargeProgress(entity) : 0.0F);

        // Arco: mismos predicados que el vanilla + si lleva Caladbolg montado
        ModelPredicateProviderRegistry.register(FateUBW.ARCHER_BOW, Identifier.ofVanilla("pull"),
                (stack, world, entity, seed) -> using(entity, stack) ? entity.getItemUseTime() / 20.0F : 0.0F);
        ModelPredicateProviderRegistry.register(FateUBW.ARCHER_BOW, Identifier.ofVanilla("pulling"),
                (stack, world, entity, seed) -> using(entity, stack) ? 1.0F : 0.0F);
        ModelPredicateProviderRegistry.register(FateUBW.ARCHER_BOW, FateUBW.id("caladbolg"),
                (stack, world, entity, seed) -> using(entity, stack) && entity.isSneaking()
                        && !(entity instanceof PlayerEntity p && p.getItemCooldownManager().isCoolingDown(FateUBW.CALADBOLG)) ? 1.0F : 0.0F);
    }

    private static boolean using(@Nullable LivingEntity entity, ItemStack stack) {
        return entity != null && entity.isUsingItem() && entity.getActiveItem() == stack;
    }
}
