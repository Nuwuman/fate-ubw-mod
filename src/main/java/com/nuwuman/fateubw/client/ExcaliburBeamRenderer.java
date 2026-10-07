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
        float w = ExcaliburBeamEntity.RADIUS * grow * (1.0F + 0.08F * MathHelper.sin(age * 1.7F));

        matrices.push();
        // Alinear +Y con la dirección de la mirada al disparar
        matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(-entity.getYaw()));
        matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(90.0F + entity.getPitch()));
        matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(age * 15.0F));
        Matrix4f m = matrices.peek().getPositionMatrix();
        VertexConsumer vc = vertexConsumers.getBuffer(RenderLayer.getLightning());

        prism(vc, m, len, w * 0.15F, w * 0.3F, 255, 255, 245, (int) (255 * fade));
        prism(vc, m, len, w * 0.35F, w * 0.65F, 255, 215, 90, (int) (190 * fade));
        prism(vc, m, len, w * 0.6F, w * 1.1F, 255, 170, 40, (int) (110 * fade));
        matrices.pop();
    }

    // Prisma cuadrado de y=0 a y=len que se ensancha de w0 a w1; caras con las dos orientaciones
    private static void prism(VertexConsumer vc, Matrix4f m, float len, float w0, float w1, int r, int g, int b, int a) {
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
