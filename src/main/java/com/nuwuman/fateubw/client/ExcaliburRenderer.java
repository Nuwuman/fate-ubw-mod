package com.nuwuman.fateubw.client;

import com.nuwuman.fateubw.FateUBW;
import com.nuwuman.fateubw.saber.ExcaliburItem;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.model.json.ModelTransformationMode;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Identifier;
import org.jetbrains.annotations.Nullable;
import software.bernie.geckolib.cache.object.BakedGeoModel;
import software.bernie.geckolib.cache.object.GeoBone;
import software.bernie.geckolib.model.DefaultedItemGeoModel;
import software.bernie.geckolib.renderer.GeoItemRenderer;
import software.bernie.geckolib.renderer.layer.AutoGlowingGeoLayer;
import software.bernie.geckolib.renderer.layer.GeoRenderLayer;

/**
 * Excalibur con GeckoLib: textura dorada al cargar, la hoja brilla en la oscuridad solo mientras carga,
 * y en el inventario se ve siempre la espada (sin el viento de Invisible Air).
 */
public class ExcaliburRenderer extends GeoItemRenderer<ExcaliburItem> {
    private static final Identifier NORMAL = FateUBW.id("textures/item/excalibur.png");
    private static final Identifier CHARGING = FateUBW.id("textures/item/excalibur_charging.png");
    private static final Identifier CHARGED = FateUBW.id("textures/item/excalibur_charged.png");
    // El halo suma su color a lo que hay detrás: a media intensidad para que siga pareciendo translúcido
    private static final int AURA_COLOUR = 0xFF8C8C8C;
    private boolean auraPass;

    public ExcaliburRenderer() {
        super(new DefaultedItemGeoModel<>(FateUBW.id("excalibur")));
        addRenderLayer(new AutoGlowingGeoLayer<>(this) {
            @Override
            public void render(MatrixStack poseStack, ExcaliburItem animatable, BakedGeoModel bakedModel, @Nullable RenderLayer renderType,
                               VertexConsumerProvider bufferSource, @Nullable VertexConsumer buffer, float partialTick, int packedLight, int packedOverlay) {
                if (state() >= ExcaliburItem.CHARGING) {
                    super.render(poseStack, animatable, bakedModel, renderType, bufferSource, buffer, partialTick, packedLight, packedOverlay);
                }
            }
        });
        // El halo va aparte, sumando luz y sin escribir profundidad: si se dibuja antes que la hoja ya no la tapa
        addRenderLayer(new GeoRenderLayer<>(this) {
            @Override
            public void render(MatrixStack poseStack, ExcaliburItem animatable, BakedGeoModel bakedModel, @Nullable RenderLayer renderType,
                               VertexConsumerProvider bufferSource, @Nullable VertexConsumer buffer, float partialTick, int packedLight, int packedOverlay) {
                if (state() < ExcaliburItem.CHARGING) return;
                RenderLayer aura = RenderLayer.getEyes(getTextureLocation(animatable));
                auraPass = true;
                getRenderer().reRender(bakedModel, poseStack, bufferSource, animatable, aura, bufferSource.getBuffer(aura),
                        partialTick, packedLight, packedOverlay, AURA_COLOUR);
                auraPass = false;
            }
        });
    }

    // En la pasada del halo solo se pintan sus cubos; en las demás, todo menos el halo
    @Override
    public void renderCubesOfBone(MatrixStack poseStack, GeoBone bone, VertexConsumer buffer, int packedLight, int packedOverlay, int colour) {
        if (bone.getName().equals("aura") == auraPass) super.renderCubesOfBone(poseStack, bone, buffer, packedLight, packedOverlay, colour);
    }

    private int state() {
        ItemStack stack = getCurrentItemStack();
        return stack == null ? ExcaliburItem.VEILED : ExcaliburItem.airState.applyAsInt(stack);
    }

    @Override
    public Identifier getTextureLocation(ExcaliburItem animatable) {
        return switch (state()) {
            case ExcaliburItem.CHARGING -> CHARGING;
            case ExcaliburItem.CHARGED -> CHARGED;
            default -> NORMAL;
        };
    }

    // El viento y el halo son translúcidos
    @Override
    public RenderLayer getRenderType(ExcaliburItem animatable, Identifier texture, @Nullable VertexConsumerProvider bufferSource, float partialTick) {
        return RenderLayer.getEntityTranslucent(texture);
    }

    @Override
    public void renderRecursively(MatrixStack poseStack, ExcaliburItem animatable, GeoBone bone, RenderLayer renderType,
                                  VertexConsumerProvider bufferSource, VertexConsumer buffer, boolean isReRender, float partialTick,
                                  int packedLight, int packedOverlay, int colour) {
        if (renderPerspective == ModelTransformationMode.GUI) {
            if (bone.getName().equals("wind") || bone.getName().equals("aura")) return;
            if (bone.getName().equals("blade")) bone.updateScale(1, 1, 1);
        }
        super.renderRecursively(poseStack, animatable, bone, renderType, bufferSource, buffer, isReRender, partialTick, packedLight, packedOverlay, colour);
    }
}
