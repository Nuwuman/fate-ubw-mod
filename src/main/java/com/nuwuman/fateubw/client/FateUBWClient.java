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
        AbilityHud.register();
        com.nuwuman.fateubw.grail.HolyGrailItem.openWishScreen =
                () -> net.minecraft.client.MinecraftClient.getInstance().setScreen(new GrailWishScreen());
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
        ItemStack hrunting = new ItemStack(FateUBW.HRUNTING);
        EntityRendererRegistry.register(FateUBW.HRUNTING_ENTITY, ctx -> new OrientedItemRenderer<com.nuwuman.fateubw.archer.HruntingEntity>(ctx, e -> hrunting, e -> false));
        EntityRendererRegistry.register(FateUBW.RIN_JEWEL_ENTITY, net.minecraft.client.render.entity.FlyingItemEntityRenderer::new);
        EntityRendererRegistry.register(FateUBW.ZELZERIZ_ENTITY, ZelzerizRenderer::new);
        EntityRendererRegistry.register(com.nuwuman.fateubw.npc.ServantNpcs.BERSERKER, HostileServantRenderer::new);
        EntityRendererRegistry.register(com.nuwuman.fateubw.npc.ServantNpcs.LANCER, HostileServantRenderer::new);
        EntityRendererRegistry.register(com.nuwuman.fateubw.npc.ServantNpcs.ASSASSIN, HostileServantRenderer::new);
        EntityRendererRegistry.register(FateUBW.BABYLON_WEAPON, ctx -> new OrientedItemRenderer<BabylonWeaponEntity>(ctx, BabylonWeaponEntity::getStack, e -> false));

        // Excalibur y Ea: normal / cargando / cargada
        ModelPredicateProviderRegistry.register(FateUBW.EXCALIBUR, FateUBW.id("charge"),
                (stack, world, entity, seed) -> using(entity, stack) ? ExcaliburItem.chargeProgress(entity) : 0.0F);
        // Invisible Air: el viento la oculta salvo al cargar el Noble Phantasm o justo después de liberar Strike Air
        ModelPredicateProviderRegistry.register(FateUBW.EXCALIBUR, FateUBW.id("air"),
                (stack, world, entity, seed) -> entity instanceof LivingEntity user && !using(user, stack)
                        && !(user instanceof PlayerEntity p && p.getItemCooldownManager().isCoolingDown(FateUBW.STRIKE_AIR)) ? 1.0F : 0.0F);
        // Ea (GeckoLib): sus cilindros giran deprisa mientras alguien la carga
        com.nuwuman.fateubw.gilgamesh.EaItem.charging = stack -> {
            net.minecraft.client.world.ClientWorld world = net.minecraft.client.MinecraftClient.getInstance().world;
            return world != null && world.getPlayers().stream().anyMatch(p -> using(p, stack));
        };

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
