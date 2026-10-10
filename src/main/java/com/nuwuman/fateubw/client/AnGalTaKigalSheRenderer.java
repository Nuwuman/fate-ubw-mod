package com.nuwuman.fateubw.client;

import com.nuwuman.fateubw.ishtar.AnGalTaKigalSheEntity;
import net.minecraft.client.render.Frustum;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.EntityRenderer;
import net.minecraft.client.render.entity.EntityRendererFactory;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.RotationAxis;
import org.joml.Matrix4f;

/** An Gal Ta Kigal Shè: haz dorado y blanco con halo azul, y Venus como una estrella de anillos en la punta. */
public class AnGalTaKigalSheRenderer extends EntityRenderer<AnGalTaKigalSheEntity> {
    private static final Identifier TEXTURE = Identifier.ofVanilla("textures/entity/beacon_beam.png");

    public AnGalTaKigalSheRenderer(EntityRendererFactory.Context ctx) {
        super(ctx);
    }

    @Override
    public boolean shouldRender(AnGalTaKigalSheEntity entity, Frustum frustum, double x, double y, double z) {
        return true;
    }

    @Override
    public void render(AnGalTaKigalSheEntity entity, float yaw, float tickDelta, MatrixStack matrices,
                       VertexConsumerProvider vertexConsumers, int light) {
        float age = entity.age + tickDelta;
        float len = entity.length(age);
        float fade = entity.fade(age);
        if (len <= 0.0F || fade <= 0.0F) return;
        float grow = Math.min(1.0F, age / AnGalTaKigalSheEntity.WIDEN_TICKS);
        float w = entity.radius() * grow * (1.0F + 0.06F * MathHelper.sin(age * 2.1F));

        matrices.push();
        matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(-entity.getYaw()));
        matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(90.0F + entity.getPitch()));
        matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(age * 20.0F));
        Matrix4f m = matrices.peek().getPositionMatrix();
        VertexConsumer vc = vertexConsumers.getBuffer(RenderLayer.getLightning());

        ExcaliburBeamRenderer.tube(vc, m, len, w * 0.12F, w * 0.25F, 0.05F, age, 255, 255, 250, (int) (255 * fade));
        ExcaliburBeamRenderer.tube(vc, m, len, w * 0.3F, w * 0.55F, 0.1F, age, 255, 210, 90, (int) (170 * fade));
        ExcaliburBeamRenderer.tube(vc, m, len, w * 0.55F, w * 1.0F, 0.18F, age, 80, 140, 255, (int) (80 * fade));
        // Venus: anillos dorados y azules que laten en la punta
        for (int k = 0; k < 3; k++) {
            float pulse = 1.0F + 0.25F * MathHelper.sin(age * 0.8F + k * 2.0F);
            float r = w * (1.4F + k * 0.6F) * pulse;
            ExcaliburBeamRenderer.ring(vc, m, len - k * 0.6F, r * 0.4F, r, k == 1 ? 120 : 255, k == 1 ? 170 : 220, k == 1 ? 255 : 120,
                    (int) (200 * fade));
        }
        matrices.pop();
    }

    @Override
    public Identifier getTexture(AnGalTaKigalSheEntity entity) {
        return TEXTURE;
    }
}
