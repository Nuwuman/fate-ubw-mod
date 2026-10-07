package com.nuwuman.fateubw.client;

import com.nuwuman.fateubw.archer.RhoAiasEntity;
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

/** Siete pétalos rosas de luz, cada uno en su propia capa, perpendiculares a la mirada. */
public class RhoAiasRenderer extends EntityRenderer<RhoAiasEntity> {
    private static final Identifier TEXTURE = Identifier.ofVanilla("textures/entity/beacon_beam.png");
    private static final int SEGMENTS = 8;

    public RhoAiasRenderer(EntityRendererFactory.Context ctx) {
        super(ctx);
    }

    @Override
    public boolean shouldRender(RhoAiasEntity entity, Frustum frustum, double x, double y, double z) {
        return true;
    }

    @Override
    public void render(RhoAiasEntity entity, float yaw, float tickDelta, MatrixStack matrices,
                       VertexConsumerProvider vertexConsumers, int light) {
        float age = entity.age + tickDelta;
        float scale = entity.scale(age);
        float fade = entity.fade(age);
        if (scale <= 0.0F || fade <= 0.0F) return;

        matrices.push();
        matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(-MathHelper.lerpAngleDegrees(tickDelta, entity.prevYaw, entity.getYaw())));
        matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(90.0F + MathHelper.lerp(tickDelta, entity.prevPitch, entity.getPitch())));
        Matrix4f m = matrices.peek().getPositionMatrix();
        VertexConsumer vc = vertexConsumers.getBuffer(RenderLayer.getLightning());

        float length = RhoAiasEntity.RADIUS * scale;
        float pulse = 1.0F + 0.05F * MathHelper.sin(age * 0.4F);
        for (int k = 0; k < 7; k++) {
            float theta = (float) (k * Math.PI * 2 / 7) + age * 0.01F;
            float depth = -0.06F * k;
            petal(vc, m, theta, length * pulse, depth, 0.95F * scale, 255, 110, 170, (int) (80 * fade));
            petal(vc, m, theta, length * 0.92F, depth + 0.01F, 0.45F * scale, 255, 200, 230, (int) (60 * fade));
        }
        matrices.pop();
    }

    // Pétalo en el plano XZ local: ancho cero en la base y la punta, máximo en el centro
    private static void petal(VertexConsumer vc, Matrix4f m, float theta, float length, float y, float width,
                              int r, int g, int b, int a) {
        float ux = MathHelper.cos(theta), uz = MathHelper.sin(theta);
        float wx = -uz, wz = ux;
        for (int j = 0; j < SEGMENTS; j++) {
            float t0 = j / (float) SEGMENTS, t1 = (j + 1) / (float) SEGMENTS;
            float d0 = 0.15F + t0 * (length - 0.15F), d1 = 0.15F + t1 * (length - 0.15F);
            float h0 = width * (float) Math.pow(Math.sin(Math.PI * t0), 0.7);
            float h1 = width * (float) Math.pow(Math.sin(Math.PI * t1), 0.7);
            float ax = ux * d0 - wx * h0, az = uz * d0 - wz * h0;
            float bx = ux * d0 + wx * h0, bz = uz * d0 + wz * h0;
            float cx = ux * d1 + wx * h1, cz = uz * d1 + wz * h1;
            float dx = ux * d1 - wx * h1, dz = uz * d1 - wz * h1;
            vc.vertex(m, ax, y, az).color(r, g, b, a);
            vc.vertex(m, bx, y, bz).color(r, g, b, a);
            vc.vertex(m, cx, y, cz).color(r, g, b, a);
            vc.vertex(m, dx, y, dz).color(r, g, b, a);

            vc.vertex(m, dx, y, dz).color(r, g, b, a);
            vc.vertex(m, cx, y, cz).color(r, g, b, a);
            vc.vertex(m, bx, y, bz).color(r, g, b, a);
            vc.vertex(m, ax, y, az).color(r, g, b, a);
        }
    }

    @Override
    public Identifier getTexture(RhoAiasEntity entity) {
        return TEXTURE;
    }
}
