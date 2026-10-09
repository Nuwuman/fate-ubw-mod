package com.nuwuman.fateubw.client;

import com.nuwuman.fateubw.gilgamesh.BabylonPortalEntity;
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

/** Portal dorado: disco luminoso con un anillo brillante y ondas que giran, de cara a donde mira el dueño. */
public class BabylonPortalRenderer extends EntityRenderer<BabylonPortalEntity> {
    private static final Identifier TEXTURE = Identifier.ofVanilla("textures/entity/beacon_beam.png");
    private static final int SIDES = 24;
    private static final float RADIUS = 0.75F;

    public BabylonPortalRenderer(EntityRendererFactory.Context ctx) {
        super(ctx);
    }

    @Override
    public boolean shouldRender(BabylonPortalEntity entity, Frustum frustum, double x, double y, double z) {
        return true;
    }

    @Override
    public void render(BabylonPortalEntity entity, float yaw, float tickDelta, MatrixStack matrices,
                       VertexConsumerProvider vertexConsumers, int light) {
        float age = entity.age + tickDelta;
        float scale = entity.scale(age);
        if (scale <= 0.0F) return;

        matrices.push();
        matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(-MathHelper.lerpAngleDegrees(tickDelta, entity.prevYaw, entity.getYaw())));
        matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(90.0F + MathHelper.lerp(tickDelta, entity.prevPitch, entity.getPitch())));
        matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(age * 6.0F));
        drawPortal(vertexConsumers.getBuffer(RenderLayer.getLightning()), matrices.peek().getPositionMatrix(), RADIUS * scale, age);
        matrices.pop();
    }

    /**
     * Un portal dorado lleno (también lo usan las cadenas de Enkidu) en el plano XZ local: disco brillante en el centro,
     * anillo, una onda que pulsa y brazos en espiral que giran hacia dentro, como un remolino.
     */
    public static void drawPortal(VertexConsumer vc, Matrix4f m, float r, float age) {
        for (int i = 0; i < SIDES; i++) {
            float a0 = (float) (i * Math.PI * 2 / SIDES), a1 = (float) ((i + 1) * Math.PI * 2 / SIDES);
            float c0 = MathHelper.cos(a0), s0 = MathHelper.sin(a0), c1 = MathHelper.cos(a1), s1 = MathHelper.sin(a1);
            // Disco: del centro al borde, más brillante en el centro
            quad(vc, m, 0, 0, 0, 0, c0 * r, s0 * r, c1 * r, s1 * r, 255, 230, 140, 150, 60);
            // Anillo exterior brillante
            float ri = r * 0.85F, ro = r * 1.08F;
            quad(vc, m, c0 * ri, s0 * ri, c1 * ri, s1 * ri, c1 * ro, s1 * ro, c0 * ro, s0 * ro, 255, 210, 80, 220, 220);
            // Onda interior que pulsa
            float wave = r * (0.45F + 0.15F * MathHelper.sin(age * 0.6F));
            quad(vc, m, c0 * wave, s0 * wave, c1 * wave, s1 * wave, c1 * (wave + 0.05F), s1 * (wave + 0.05F),
                    c0 * (wave + 0.05F), s0 * (wave + 0.05F), 255, 240, 170, 200, 200);
        }
        // Remolino: cuatro brazos en espiral, más finos y tenues hacia fuera
        int arms = 4, steps = 10;
        for (int arm = 0; arm < arms; arm++) {
            float base = (float) (arm * Math.PI * 2 / arms) - age * 0.15F;
            for (int k = 0; k < steps; k++) {
                float t0 = k / (float) steps, t1 = (k + 1) / (float) steps;
                float a0 = base + t0 * 4.0F, a1 = base + t1 * 4.0F;
                float w0 = 0.09F * r * (1 - t0) + 0.01F, w1 = 0.09F * r * (1 - t1) + 0.01F;
                float r0 = r * (0.08F + 0.9F * t0), r1 = r * (0.08F + 0.9F * t1);
                float c0 = MathHelper.cos(a0), s0 = MathHelper.sin(a0), c1 = MathHelper.cos(a1), s1 = MathHelper.sin(a1);
                quad(vc, m, c0 * (r0 - w0), s0 * (r0 - w0), c0 * (r0 + w0), s0 * (r0 + w0), c1 * (r1 + w1), s1 * (r1 + w1),
                        c1 * (r1 - w1), s1 * (r1 - w1), 255, 245, 190, (int) (230 * (1 - t0)), (int) (230 * (1 - t1)));
            }
        }
    }

    // Cuadrilátero en el plano XZ local (y = 0), con alfa distinto en los dos primeros y los dos últimos vértices
    private static void quad(VertexConsumer vc, Matrix4f m, float ax, float az, float bx, float bz, float cx, float cz,
                             float dx, float dz, int r, int g, int b, int alphaIn, int alphaOut) {
        vc.vertex(m, ax, 0, az).color(r, g, b, alphaIn);
        vc.vertex(m, bx, 0, bz).color(r, g, b, alphaIn);
        vc.vertex(m, cx, 0, cz).color(r, g, b, alphaOut);
        vc.vertex(m, dx, 0, dz).color(r, g, b, alphaOut);
        vc.vertex(m, dx, 0, dz).color(r, g, b, alphaOut);
        vc.vertex(m, cx, 0, cz).color(r, g, b, alphaOut);
        vc.vertex(m, bx, 0, bz).color(r, g, b, alphaIn);
        vc.vertex(m, ax, 0, az).color(r, g, b, alphaIn);
    }

    @Override
    public Identifier getTexture(BabylonPortalEntity entity) {
        return TEXTURE;
    }
}
