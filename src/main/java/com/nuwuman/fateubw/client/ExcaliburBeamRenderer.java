package com.nuwuman.fateubw.client;

import com.nuwuman.fateubw.saber.ExcaliburBeamEntity;
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

/** Dibuja el haz como tres prismas aditivos (núcleo blanco, capa dorada, halo) que giran sobre su eje. */
public class ExcaliburBeamRenderer extends EntityRenderer<ExcaliburBeamEntity> {
    private static final Identifier TEXTURE = Identifier.ofVanilla("textures/entity/beacon_beam.png");

    public ExcaliburBeamRenderer(EntityRendererFactory.Context ctx) {
        super(ctx);
    }

    @Override
    public boolean shouldRender(ExcaliburBeamEntity entity, Frustum frustum, double x, double y, double z) {
        return true;
    }

    @Override
    public void render(ExcaliburBeamEntity entity, float yaw, float tickDelta, MatrixStack matrices,
                       VertexConsumerProvider vertexConsumers, int light) {
        float age = entity.age + tickDelta;
        float len = entity.length(age);
        float fade = entity.fade(age);
        if (len <= 0.0F || fade <= 0.0F) return;

        float grow = Math.min(1.0F, age / ExcaliburBeamEntity.GROW_TICKS);
        // Al apagarse se estrecha además de desvanecerse
        float w = entity.radius() * grow * (1.0F + 0.08F * MathHelper.sin(age * 1.7F)) * (0.35F + 0.65F * fade);
        NpCamera.impact(entity, 0xFFF4C8, 1.0F);

        matrices.push();
        // Alinear +Y con la dirección de la mirada al disparar
        matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(-entity.getYaw()));
        matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(90.0F + entity.getPitch()));
        matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(age * 15.0F));
        Matrix4f m = matrices.peek().getPositionMatrix();
        VertexConsumer vc = vertexConsumers.getBuffer(RenderLayer.getLightning());

        // Tubos redondos con ondas que recorren el haz: núcleo blanco, luz dorada y halo naranja
        tube(vc, m, len, w * 0.15F, w * 0.3F, 0.06F, age, 255, 255, 245, (int) (255 * fade));
        tube(vc, m, len, w * 0.35F, w * 0.65F, 0.12F, age, 255, 215, 90, (int) (180 * fade));
        tube(vc, m, len, w * 0.6F, w * 1.1F, 0.2F, age, 255, 170, 40, (int) (95 * fade));
        // Anillos de luz que salen disparados a lo largo del haz
        for (int k = 0; k < 4; k++) {
            float at = ((age * 2.5F + k * len / 4) % len);
            float ringW = w * (1.15F + 0.25F * (at / len));
            ring(vc, m, at, ringW, ringW * 1.25F, 255, 230, 150, (int) (140 * fade * (1 - at / len)));
        }
        // Estelas doradas que corren hacia la punta
        BeamFx.streaks(vc, m, len, w, age, 18, entity.getId(), 255, 245, 200, (int) (190 * fade));
        // Estallido de rayos de luz en la espada y una onda que se abre al disparar
        float pulse = 1.0F + 0.15F * MathHelper.sin(age * 2.3F);
        BeamFx.rays(vc, m, 0.4F, w * 0.35F, w * 3.2F * pulse, 14, age * 0.05F, 255, 240, 170, (int) (210 * fade));
        if (age < 12) {
            float wave = 1.5F + age * 1.8F;
            ring(vc, m, 0.5F, wave * 0.7F, wave, 255, 250, 210, (int) (230 * (1.0F - age / 12.0F)));
        }
        // Destello en la punta
        BeamFx.rays(vc, m, len, w * 0.3F, w * 2.0F * pulse, 10, -age * 0.07F, 255, 225, 140, (int) (170 * fade));
        matrices.pop();
    }

    private static final int SIDES = 12;

    // Tubo de SIDES lados, de y=0 a y=len, que se ensancha de w0 a w1 y cuyo radio ondula (amp) con el tiempo
    static void tube(VertexConsumer vc, Matrix4f m, float len, float w0, float w1, float amp, float age, int r, int g, int b, int a) {
        int segments = Math.max(2, (int) (len / 2));
        for (int s = 0; s < segments; s++) {
            float y0 = len * s / segments, y1 = len * (s + 1) / segments;
            float r0 = radius(y0, len, w0, w1, amp, age), r1 = radius(y1, len, w0, w1, amp, age);
            int a0 = alongAlpha(a, y0, len), a1 = alongAlpha(a, y1, len);
            for (int i = 0; i < SIDES; i++) {
                float t0 = (float) (i * Math.PI * 2 / SIDES), t1 = (float) ((i + 1) * Math.PI * 2 / SIDES);
                float c0 = MathHelper.cos(t0), s0 = MathHelper.sin(t0), c1 = MathHelper.cos(t1), s1 = MathHelper.sin(t1);
                // Las dos caras: la capa del rayo descarta las de espaldas
                vc.vertex(m, c0 * r0, y0, s0 * r0).color(r, g, b, a0);
                vc.vertex(m, c1 * r0, y0, s1 * r0).color(r, g, b, a0);
                vc.vertex(m, c1 * r1, y1, s1 * r1).color(r, g, b, a1);
                vc.vertex(m, c0 * r1, y1, s0 * r1).color(r, g, b, a1);
                vc.vertex(m, c0 * r1, y1, s0 * r1).color(r, g, b, a1);
                vc.vertex(m, c1 * r1, y1, s1 * r1).color(r, g, b, a1);
                vc.vertex(m, c1 * r0, y0, s1 * r0).color(r, g, b, a0);
                vc.vertex(m, c0 * r0, y0, s0 * r0).color(r, g, b, a0);
            }
        }
    }

    private static float radius(float y, float len, float w0, float w1, float amp, float age) {
        float base = MathHelper.lerp(y / len, w0, w1);
        return base * (1.0F + amp * MathHelper.sin(y * 0.7F - age * 1.4F));
    }

    // Se desvanece hacia la punta
    private static int alongAlpha(int a, float y, float len) {
        return (int) (a * (1.0F - 0.5F * y / len));
    }

    // Anillo plano perpendicular al haz a la altura y
    static void ring(VertexConsumer vc, Matrix4f m, float y, float inner, float outer, int r, int g, int b, int a) {
        if (a <= 0) return;
        for (int i = 0; i < SIDES * 2; i++) {
            float t0 = (float) (i * Math.PI / SIDES), t1 = (float) ((i + 1) * Math.PI / SIDES);
            float c0 = MathHelper.cos(t0), s0 = MathHelper.sin(t0), c1 = MathHelper.cos(t1), s1 = MathHelper.sin(t1);
            vc.vertex(m, c0 * inner, y, s0 * inner).color(r, g, b, a);
            vc.vertex(m, c1 * inner, y, s1 * inner).color(r, g, b, a);
            vc.vertex(m, c1 * outer, y, s1 * outer).color(r, g, b, 0);
            vc.vertex(m, c0 * outer, y, s0 * outer).color(r, g, b, 0);
            vc.vertex(m, c0 * outer, y, s0 * outer).color(r, g, b, 0);
            vc.vertex(m, c1 * outer, y, s1 * outer).color(r, g, b, 0);
            vc.vertex(m, c1 * inner, y, s1 * inner).color(r, g, b, a);
            vc.vertex(m, c0 * inner, y, s0 * inner).color(r, g, b, a);
        }
    }

    // Prisma cuadrado de y=0 a y=len que se ensancha de w0 a w1; caras con las dos orientaciones.
    // También lo usa la estela de la embestida de Pegaso
    static void prism(VertexConsumer vc, Matrix4f m, float len, float w0, float w1, int r, int g, int b, int a) {
        float[] xs = {-1, 1, 1, -1};
        float[] zs = {-1, -1, 1, 1};
        int tipAlpha = a / 2;
        for (int i = 0; i < 4; i++) {
            int j = (i + 1) % 4;
            vc.vertex(m, xs[i] * w0, 0, zs[i] * w0).color(r, g, b, a);
            vc.vertex(m, xs[j] * w0, 0, zs[j] * w0).color(r, g, b, a);
            vc.vertex(m, xs[j] * w1, len, zs[j] * w1).color(r, g, b, tipAlpha);
            vc.vertex(m, xs[i] * w1, len, zs[i] * w1).color(r, g, b, tipAlpha);

            vc.vertex(m, xs[i] * w1, len, zs[i] * w1).color(r, g, b, tipAlpha);
            vc.vertex(m, xs[j] * w1, len, zs[j] * w1).color(r, g, b, tipAlpha);
            vc.vertex(m, xs[j] * w0, 0, zs[j] * w0).color(r, g, b, a);
            vc.vertex(m, xs[i] * w0, 0, zs[i] * w0).color(r, g, b, a);
        }
    }

    @Override
    public Identifier getTexture(ExcaliburBeamEntity entity) {
        return TEXTURE;
    }
}
