package com.nuwuman.fateubw.client;

import com.nuwuman.fateubw.archer.ThrownFalchionEntity;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.EntityRenderer;
import net.minecraft.client.render.entity.EntityRendererFactory;
import net.minecraft.client.render.item.ItemRenderer;
import net.minecraft.client.render.model.json.ModelTransformationMode;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.screen.PlayerScreenHandler;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.RotationAxis;

/** El sable lanzado gira en plano horizontal como un disco. Usa el mismo modelo 3D del ítem. */
public class ThrownFalchionRenderer extends EntityRenderer<ThrownFalchionEntity> {
    private final ItemRenderer itemRenderer;

    public ThrownFalchionRenderer(EntityRendererFactory.Context ctx) {
        super(ctx);
        this.itemRenderer = ctx.getItemRenderer();
    }

    @Override
    public void render(ThrownFalchionEntity entity, float yaw, float tickDelta, MatrixStack matrices,
                       VertexConsumerProvider vertexConsumers, int light) {
        matrices.push();
        matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees((entity.age + tickDelta) * 50.0F));
        matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(90.0F));
        itemRenderer.renderItem(entity.getStack(), ModelTransformationMode.NONE, light, OverlayTexture.DEFAULT_UV,
                matrices, vertexConsumers, entity.getWorld(), entity.getId());
        matrices.pop();
        super.render(entity, yaw, tickDelta, matrices, vertexConsumers, light);
    }

    @Override
    public Identifier getTexture(ThrownFalchionEntity entity) {
        return PlayerScreenHandler.BLOCK_ATLAS_TEXTURE;
    }
}
