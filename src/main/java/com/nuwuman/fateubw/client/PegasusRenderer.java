package com.nuwuman.fateubw.client;

import com.nuwuman.fateubw.FateUBW;
import com.nuwuman.fateubw.rider.PegasusEntity;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.EntityRendererFactory;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.RotationAxis;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;
import software.bernie.geckolib.model.DefaultedEntityGeoModel;
import software.bernie.geckolib.renderer.GeoEntityRenderer;

/** Pegaso con GeckoLib; durante la embestida de Bellerophon arrastra una estela de luz como un cometa. */
public class PegasusRenderer extends GeoEntityRenderer<PegasusEntity> {
    public PegasusRenderer(EntityRendererFactory.Context ctx) {
        super(ctx, new DefaultedEntityGeoModel<>(FateUBW.id("pegasus")));
    }

    @Override
    public void render(PegasusEntity entity, float entityYaw, float partialTick, MatrixStack matrices,
                       VertexConsumerProvider vertexConsumers, int light) {
        super.render(entity, entityYaw, partialTick, matrices, vertexConsumers, light);
        int charge = entity.getCharge();
        if (charge <= 0) return;

        Vec3d v = entity.getVelocity();
        if (v.lengthSquared() < 1.0E-4) return;
        float fade = Math.min(1.0F, charge / 6.0F);
        float yaw = (float) Math.toDegrees(Math.atan2(v.x, v.z));
        float pitch = (float) Math.toDegrees(Math.atan2(v.y, v.horizontalLength()));

        matrices.push();
        matrices.translate(0.0, entity.getHeight() * 0.6, 0.0);
        // +Y local en la dirección del vuelo; la estela va hacia -Y (detrás)
        matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(yaw));
        matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(90.0F - pitch));
        matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees((entity.age + partialTick) * 20.0F));
        Matrix4f m = matrices.peek().getPositionMatrix();
        VertexConsumer vc = vertexConsumers.getBuffer(RenderLayer.getLightning());
        float pulse = 1.0F + 0.1F * MathHelper.sin((entity.age + partialTick) * 1.5F);
        ExcaliburBeamRenderer.prism(vc, m, -8.0F, 1.2F * pulse, 0.2F, 255, 255, 240, (int) (200 * fade));
        ExcaliburBeamRenderer.prism(vc, m, -6.0F, 1.8F * pulse, 0.4F, 255, 220, 120, (int) (110 * fade));
        matrices.pop();
    }
}
