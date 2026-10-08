package com.nuwuman.fateubw.client;

import com.nuwuman.fateubw.gilgamesh.EnumaElishEntity;
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

/** Enuma Elish: tres cintas rojas en espiral que giran alrededor de un núcleo, abriéndose a lo largo del vórtice. */
public class EnumaElishRenderer extends EntityRenderer<EnumaElishEntity> {
    private static final Identifier TEXTURE = Identifier.ofVanilla("textures/entity/beacon_beam.png");
    private static final int SEGMENTS = 64;

    public EnumaElishRenderer(EntityRendererFactory.Context ctx) {
        super(ctx);
    }

    @Override
    public boolean shouldRender(EnumaElishEntity entity, Frustum frustum, double x, double y, double z) {
        return true;
    }

    @Override
    public void render(EnumaElishEntity entity, float yaw, float tickDelta, MatrixStack matrices,
                       VertexConsumerProvider vertexConsumers, int light) {
        float age = entity.age + tickDelta;
        float len = entity.length(age);
        float fade = entity.fade(age);
        if (len <= 0.0F || fade <= 0.0F) return;
        float grow = Math.min(1.0F, age / EnumaElishEntity.GROW_TICKS);
        float radius = entity.radius() * grow;

        matrices.push();
        matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(-entity.getYaw()));
        matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(90.0F + entity.getPitch()));
        Matrix4f m = matrices.peek().getPositionMatrix();
        VertexConsumer vc = vertexConsumers.getBuffer(RenderLayer.getLightning());

        // Núcleo rojo y halo
        ExcaliburBeamRenderer.prism(vc, m, len, radius * 0.15F, radius * 0.3F, 255, 120, 80, (int) (230 * fade));
        ExcaliburBeamRenderer.prism(vc, m, len, radius * 0.5F, radius * 1.1F, 200, 20, 20, (int) (60 * fade));

        // Tres cintas en espiral que se abren hacia la punta
        for (int k = 0; k < 3; k++) {
            float phase = (float) (k * Math.PI * 2 / 3) + age * 0.5F;
            for (int i = 0; i < SEGMENTS; i++) {
                float t0 = i / (float) SEGMENTS, t1 = (i + 1) / (float) SEGMENTS;
                ribbon(vc, m, len, radius, phase, t0, t1, (int) (200 * fade));
            }
        }
        matrices.pop();
    }

    private static void ribbon(VertexConsumer vc, Matrix4f m, float len, float radius, float phase, float t0, float t1, int alpha) {
        float y0 = t0 * len, y1 = t1 * len;
        float a0 = phase + y0 * 0.7F, a1 = phase + y1 * 0.7F;
        float r0 = radius * (0.4F + 0.6F * t0), r1 = radius * (0.4F + 0.6F * t1);
        float c0 = MathHelper.cos(a0), s0 = MathHelper.sin(a0), c1 = MathHelper.cos(a1), s1 = MathHelper.sin(a1);
        float w = 0.25F + 0.35F * t0;
        // Cinta ancha en la dirección del eje: se ve desde cualquier lado
        quad(vc, m, r0 * c0, y0 - w, r0 * s0, r1 * c1, y1 - w, r1 * s1, r1 * c1, y1 + w, r1 * s1, r0 * c0, y0 + w, r0 * s0, alpha);
        // Y otra en dirección radial, para que no desaparezca de canto
        quad(vc, m, (r0 - w) * c0, y0, (r0 - w) * s0, (r1 - w) * c1, y1, (r1 - w) * s1,
                (r1 + w) * c1, y1, (r1 + w) * s1, (r0 + w) * c0, y0, (r0 + w) * s0, alpha);
    }

    private static void quad(VertexConsumer vc, Matrix4f m, float ax, float ay, float az, float bx, float by, float bz,
                             float cx, float cy, float cz, float dx, float dy, float dz, int alpha) {
        vc.vertex(m, ax, ay, az).color(255, 40, 30, alpha);
        vc.vertex(m, bx, by, bz).color(255, 40, 30, alpha);
        vc.vertex(m, cx, cy, cz).color(255, 140, 60, alpha);
        vc.vertex(m, dx, dy, dz).color(255, 140, 60, alpha);
        vc.vertex(m, dx, dy, dz).color(255, 140, 60, alpha);
        vc.vertex(m, cx, cy, cz).color(255, 140, 60, alpha);
        vc.vertex(m, bx, by, bz).color(255, 40, 30, alpha);
        vc.vertex(m, ax, ay, az).color(255, 40, 30, alpha);
    }

    @Override
    public Identifier getTexture(EnumaElishEntity entity) {
        return TEXTURE;
    }
}
