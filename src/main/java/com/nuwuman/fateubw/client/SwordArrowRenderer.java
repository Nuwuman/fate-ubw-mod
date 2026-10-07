package com.nuwuman.fateubw.client;

import com.nuwuman.fateubw.FateUBW;
import com.nuwuman.fateubw.archer.SwordArrowEntity;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.EntityRenderer;
import net.minecraft.client.render.entity.EntityRendererFactory;
import net.minecraft.client.render.item.ItemRenderer;
import net.minecraft.client.render.model.json.ModelTransformationMode;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.PlayerScreenHandler;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.RotationAxis;

/** Dibuja el modelo 3D de la espada-flecha (o de Caladbolg, girando como un taladro) apuntando hacia donde vuela. */
public class SwordArrowRenderer extends EntityRenderer<SwordArrowEntity> {
    private final ItemRenderer itemRenderer;
    private final ItemStack arrow = new ItemStack(FateUBW.SWORD_ARROW);
    private final ItemStack caladbolg = new ItemStack(FateUBW.CALADBOLG);

    public SwordArrowRenderer(EntityRendererFactory.Context ctx) {
        super(ctx);
        this.itemRenderer = ctx.getItemRenderer();
    }

    @Override
    public void render(SwordArrowEntity entity, float yaw, float tickDelta, MatrixStack matrices,
                       VertexConsumerProvider vertexConsumers, int light) {
        matrices.push();
        // Igual que las flechas vanilla: deja +X en la dirección del vuelo...
        matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(MathHelper.lerp(tickDelta, entity.prevYaw, entity.getYaw()) - 90.0F));
        matrices.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(MathHelper.lerp(tickDelta, entity.prevPitch, entity.getPitch())));
        // ...y el modelo, que mira hacia +Y, se tumba sobre +X
        matrices.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(-90.0F));
        boolean isCaladbolg = entity.isCaladbolg();
        if (isCaladbolg) matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees((entity.age + tickDelta) * 40.0F));
        itemRenderer.renderItem(isCaladbolg ? caladbolg : arrow, ModelTransformationMode.NONE, light, OverlayTexture.DEFAULT_UV,
                matrices, vertexConsumers, entity.getWorld(), entity.getId());
        matrices.pop();
        super.render(entity, yaw, tickDelta, matrices, vertexConsumers, light);
    }

    @Override
    public Identifier getTexture(SwordArrowEntity entity) {
        return PlayerScreenHandler.BLOCK_ATLAS_TEXTURE;
    }
}
