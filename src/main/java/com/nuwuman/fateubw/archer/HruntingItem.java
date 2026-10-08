package com.nuwuman.fateubw.archer;

import net.minecraft.item.Item;
import software.bernie.geckolib.animatable.GeoItem;
import software.bernie.geckolib.animatable.client.GeoRenderProvider;
import software.bernie.geckolib.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.animation.AnimatableManager;
import software.bernie.geckolib.util.GeckoLibUtil;

/** Hrunting: el ítem solo sirve de llave de la habilidad (la lanza la armadura de Archer); brillan sus partes rojas. */
public class HruntingItem extends Item implements GeoItem {
    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);

    // GeckoLib la dibuja para que brillen sus partes (textura _glowmask); no tiene animaciones
    @Override
    public void createGeoRenderer(java.util.function.Consumer<GeoRenderProvider> consumer) {
        com.nuwuman.fateubw.GlowingGeo.renderer(consumer, "hrunting");
    }

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return cache;
    }

    public HruntingItem(Item.Settings settings) {
        super(settings);
    }
}
