package com.nuwuman.fateubw.client;

import com.nuwuman.fateubw.mash.LordCamelotEntity;
import net.minecraft.client.render.Frustum;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.EntityRenderer;
import net.minecraft.client.render.entity.EntityRendererFactory;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.screen.PlayerScreenHandler;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.RotationAxis;
import org.joml.Matrix4f;

/**
 * Lord Camelot a la vista: la silueta de luz de las murallas de Camelot (lienzo con almenas y tres torres), con el
 * brillo que sube por ella. Se alza en un instante y se desvanece al final.
 */
public class LordCamelotRenderer extends EntityRenderer<LordCamelotEntity> {
    private static final float HW = LordCamelotEntity.HALF_WIDTH, H = LordCamelotEntity.HEIGHT;
    private static final float WALL_TOP = 3.4F, MERLON = 0.6F, TOWER = 0.55F;

    public LordCamelotRenderer(EntityRendererFactory.Context ctx) {
        super(ctx);
    }

    @Override
    public boolean shouldRender(LordCamelotEntity entity, Frustum frustum, double x, double y, double z) {
        return true;
    }

    @Override
    public void render(LordCamelotEntity entity, float yaw, float tickDelta, MatrixStack matrices, VertexConsumerProvider consumers, int light) {
        float age = entity.age + tickDelta;
        float s = entity.strength(age);
        if (s <= 0) return;
        matrices.push();
        matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(-entity.getYaw()));
        Matrix4f m = matrices.peek().getPositionMatrix();
        VertexConsumer vc = consumers.getBuffer(RenderLayer.getLightning());
        float rise = MathHelper.clamp(age / 6.0F, 0.0F, 1.0F);   // se alza desde el suelo

        // Lienzo de la muralla, más claro abajo
        rect(vc, m, -HW, 0, HW, WALL_TOP * rise, 180, 210, 255, (int) (70 * s), (int) (35 * s));
        // Almenas
        for (float x = -HW; x < HW - 0.01F; x += MERLON * 2) {
            rect(vc, m, x, WALL_TOP * rise, Math.min(x + MERLON, HW), (WALL_TOP + 0.5F) * rise, 210, 230, 255, (int) (110 * s), (int) (110 * s));
        }
        // Tres torres, más altas, con su remate
        for (float x : new float[]{-HW, 0.0F, HW}) {
            rect(vc, m, x - TOWER, 0, x + TOWER, H * rise, 200, 225, 255, (int) (95 * s), (int) (60 * s));
            rect(vc, m, x - TOWER - 0.15F, (H - 0.15F) * rise, x + TOWER + 0.15F, (H + 0.25F) * rise, 235, 245, 255, (int) (160 * s), (int) (160 * s));
        }
        // Brillo que sube por el muro
        for (int k = 0; k < 6; k++) {
            float x = -HW + (k + 0.5F) * (2 * HW / 6);
            float y = ((age * 0.12F + k * 0.37F) % 1.0F) * WALL_TOP * rise;
            rect(vc, m, x - 0.06F, y, x + 0.06F, y + 0.8F, 255, 255, 255, (int) (120 * s), 0);
        }
        // Borde de abajo, donde toca el suelo
        rect(vc, m, -HW - 0.2F, 0, HW + 0.2F, 0.12F, 255, 255, 255, (int) (200 * s), (int) (200 * s));
        matrices.pop();
        super.render(entity, yaw, tickDelta, matrices, consumers, light);
    }

    // Un rectángulo vertical en el plano del muro (z = 0), visible por las dos caras; alfa distinto abajo y arriba
    private static void rect(VertexConsumer vc, Matrix4f m, float x0, float y0, float x1, float y1, int r, int g, int b, int alphaBottom, int alphaTop) {
        float[][] v = {{x0, y0}, {x1, y0}, {x1, y1}, {x0, y1}};
        int[] a = {alphaBottom, alphaBottom, alphaTop, alphaTop};
        for (int i = 0; i < 4; i++) vc.vertex(m, v[i][0], v[i][1], 0.0F).color(r, g, b, a[i]);
        for (int i = 3; i >= 0; i--) vc.vertex(m, v[i][0], v[i][1], 0.0F).color(r, g, b, a[i]);
    }

    @Override
    public Identifier getTexture(LordCamelotEntity entity) {
        return PlayerScreenHandler.BLOCK_ATLAS_TEXTURE;
    }
}
