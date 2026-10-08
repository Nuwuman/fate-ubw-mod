package com.nuwuman.fateubw.client;

import com.nuwuman.fateubw.archer.UbwCoreEntity;
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

/** Los engranajes gigantes que giran en el cielo de Unlimited Blade Works, como siluetas oscuras de bronce. */
public class UbwCoreRenderer extends EntityRenderer<UbwCoreEntity> {
    private static final Identifier TEXTURE = Identifier.ofVanilla("textures/entity/beacon_beam.png");
    // x, y, z (respecto al centro), radio, velocidad de giro, inclinación
    private static final float[][] GEARS = {
            {-50, 60, 40, 18, 0.4F, 20}, {35, 75, 55, 26, -0.25F, -15}, {70, 50, -20, 14, 0.6F, 35},
            {-30, 85, -60, 30, 0.15F, -25}, {10, 55, 80, 12, -0.7F, 10}, {-75, 45, -10, 16, 0.5F, 40}};

    public UbwCoreRenderer(EntityRendererFactory.Context ctx) {
        super(ctx);
    }

    @Override
    public boolean shouldRender(UbwCoreEntity entity, Frustum frustum, double x, double y, double z) {
        return true;
    }

    @Override
    public void render(UbwCoreEntity entity, float yaw, float tickDelta, MatrixStack matrices,
                       VertexConsumerProvider vertexConsumers, int light) {
        float age = entity.age + tickDelta;
        float appear = Math.min(1.0F, age / 40.0F);
        VertexConsumer vc = vertexConsumers.getBuffer(RenderLayer.getDebugQuads());
        for (float[] g : GEARS) {
            matrices.push();
            matrices.translate(g[0], g[1], g[2]);
            // De cara al centro, algo inclinado
            matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees((float) Math.toDegrees(Math.atan2(-g[0], -g[2]))));
            matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(g[5]));
            matrices.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(age * g[4]));
            gear(vc, matrices.peek().getPositionMatrix(), g[3], (int) (220 * appear));
            matrices.pop();
        }
    }

    // Engranaje en el plano XY: aro, dientes, cubo y radios
    private static void gear(VertexConsumer vc, Matrix4f m, float radius, int alpha) {
        int teeth = 16;
        float inner = radius * 0.78F, hubIn = radius * 0.12F, hubOut = radius * 0.25F;
        for (int i = 0; i < teeth * 2; i++) {
            float a0 = (float) (i * Math.PI / teeth), a1 = (float) ((i + 1) * Math.PI / teeth);
            float c0 = MathHelper.cos(a0), s0 = MathHelper.sin(a0), c1 = MathHelper.cos(a1), s1 = MathHelper.sin(a1);
            float outer = i % 2 == 0 ? radius * 1.12F : radius;
            quad(vc, m, c0 * inner, s0 * inner, c1 * inner, s1 * inner, c1 * outer, s1 * outer, c0 * outer, s0 * outer, alpha);
            quad(vc, m, c0 * hubIn, s0 * hubIn, c1 * hubIn, s1 * hubIn, c1 * hubOut, s1 * hubOut, c0 * hubOut, s0 * hubOut, alpha);
        }
        for (int k = 0; k < 6; k++) {
            float a = (float) (k * Math.PI / 3);
            float c = MathHelper.cos(a), s = MathHelper.sin(a), w = radius * 0.05F;
            quad(vc, m, c * hubOut - s * w, s * hubOut + c * w, c * hubOut + s * w, s * hubOut - c * w,
                    c * inner + s * w, s * inner - c * w, c * inner - s * w, s * inner + c * w, alpha);
        }
    }

    private static void quad(VertexConsumer vc, Matrix4f m, float ax, float ay, float bx, float by,
                             float cx, float cy, float dx, float dy, int alpha) {
        int r = 58, g = 36, b = 24;
        vc.vertex(m, ax, ay, 0).color(r, g, b, alpha);
        vc.vertex(m, bx, by, 0).color(r, g, b, alpha);
        vc.vertex(m, cx, cy, 0).color(r, g, b, alpha);
        vc.vertex(m, dx, dy, 0).color(r, g, b, alpha);
        vc.vertex(m, dx, dy, 0).color(r, g, b, alpha);
        vc.vertex(m, cx, cy, 0).color(r, g, b, alpha);
        vc.vertex(m, bx, by, 0).color(r, g, b, alpha);
        vc.vertex(m, ax, ay, 0).color(r, g, b, alpha);
    }

    @Override
    public Identifier getTexture(UbwCoreEntity entity) {
        return TEXTURE;
    }
}
