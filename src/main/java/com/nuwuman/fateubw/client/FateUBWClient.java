package com.nuwuman.fateubw.client;

import com.nuwuman.fateubw.FateUBW;
import com.nuwuman.fateubw.archer.SwordArrowEntity;
import com.nuwuman.fateubw.gilgamesh.BabylonWeaponEntity;
import com.nuwuman.fateubw.lancer.GaeBolgSpearEntity;
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
        EntityRendererRegistry.register(FateUBW.SWORD_ARROW_ENTITY, ctx -> {
            ItemStack arrow = new ItemStack(FateUBW.SWORD_ARROW);
            ItemStack caladbolg = new ItemStack(FateUBW.CALADBOLG);
            return new OrientedItemRenderer<SwordArrowEntity>(ctx, e -> e.isCaladbolg() ? caladbolg : arrow, SwordArrowEntity::isCaladbolg);
        });
        EntityRendererRegistry.register(FateUBW.RHO_AIAS_ENTITY, RhoAiasRenderer::new);
        EntityRendererRegistry.register(FateUBW.GAE_BOLG_SPEAR, ctx -> new OrientedItemRenderer<GaeBolgSpearEntity>(ctx, GaeBolgSpearEntity::getStack, e -> false));
        EntityRendererRegistry.register(FateUBW.CHAIN_DAGGER, ChainDaggerRenderer::new);
        EntityRendererRegistry.register(FateUBW.PEGASUS, PegasusRenderer::new);
        EntityRendererRegistry.register(FateUBW.ENUMA_ELISH, EnumaElishRenderer::new);
        EntityRendererRegistry.register(FateUBW.UBW_CORE, UbwCoreRenderer::new);
        EntityRendererRegistry.register(FateUBW.BABYLON_PORTAL, BabylonPortalRenderer::new);
        EntityRendererRegistry.register(FateUBW.BABYLON_WEAPON, ctx -> new OrientedItemRenderer<BabylonWeaponEntity>(ctx, BabylonWeaponEntity::getStack, e -> false));

        // Excalibur y Ea: normal / cargando / cargada
        ModelPredicateProviderRegistry.register(FateUBW.EXCALIBUR, FateUBW.id("charge"),
                (stack, world, entity, seed) -> using(entity, stack) ? ExcaliburItem.chargeProgress(entity) : 0.0F);
        ModelPredicateProviderRegistry.register(FateUBW.EA, FateUBW.id("charge"),
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
