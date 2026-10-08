package com.nuwuman.fateubw;

import net.minecraft.item.Item;
import software.bernie.geckolib.animatable.GeoItem;
import software.bernie.geckolib.animatable.client.GeoRenderProvider;
import software.bernie.geckolib.model.DefaultedItemGeoModel;
import software.bernie.geckolib.renderer.GeoItemRenderer;
import software.bernie.geckolib.renderer.layer.AutoGlowingGeoLayer;

import java.util.function.Consumer;

/** Armas sin animación que GeckoLib dibuja solo para que sus partes brillen en la oscuridad (textura _glowmask). */
public final class GlowingGeo {
    public static <T extends Item & GeoItem> void renderer(Consumer<GeoRenderProvider> consumer, String id) {
        consumer.accept(new GeoRenderProvider() {
            private GeoItemRenderer<T> renderer;

            @Override
            public net.minecraft.client.render.item.BuiltinModelItemRenderer getGeoItemRenderer() {
                if (renderer == null) {
                    renderer = new GeoItemRenderer<>(new DefaultedItemGeoModel<>(FateUBW.id(id)));
                    renderer.addRenderLayer(new AutoGlowingGeoLayer<>(renderer));
                }
                return renderer;
            }
        });
    }
}
