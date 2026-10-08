package com.nuwuman.fateubw.client;

import com.nuwuman.fateubw.rider.ChainDaggerEntity;
import net.minecraft.client.render.Frustum;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.EntityRendererFactory;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.Entity;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;

/** La daga (modelo 3D orientado al vuelo) y la cadena que la une a la mano del dueño, con la textura de la cadena vanilla. */
public class ChainDaggerRenderer extends OrientedItemRenderer<ChainDaggerEntity> {
    private static final Identifier CHAIN = Identifier.ofVanilla("textures/block/chain.png");
    private static final float HALF_WIDTH = 0.09F;

    public ChainDaggerRenderer(EntityRendererFactory.Context ctx) {
        super(ctx, ChainDaggerEntity::getStack, e -> false);
    }

    @Override
    public boolean shouldRender(ChainDaggerEntity entity, Frustum frustum, double x, double y, double z) {
        return true;
    }

    @Override
    public void render(ChainDaggerEntity entity, float yaw, float tickDelta, MatrixStack matrices,
                       VertexConsumerProvider vertexConsumers, int light) {
        super.render(entity, yaw, tickDelta, matrices, vertexConsumers, light);
        Entity owner = entity.getOwner();
        if (owner == null) return;

        // Mano del dueño, en coordenadas relativas a la daga (el origen de este render)
        float bodyYaw = (float) Math.toRadians(-MathHelper.lerp(tickDelta, owner.prevYaw, owner.getYaw()));
        Vec3d side = new Vec3d(-Math.cos(bodyYaw), 0.0, Math.sin(bodyYaw)).multiply(0.35);
        Vec3d hand = owner.getLerpedPos(tickDelta).add(0.0, owner.getHeight() * 0.55, 0.0).add(side);
        Vec3d d = hand.subtract(entity.getLerpedPos(tickDelta));
        float len = (float) d.length();
        if (len < 0.1F) return;

        Vec3d a = d.crossProduct(new Vec3d(0.0, 1.0, 0.0));
        a = (a.lengthSquared() < 1.0E-4 ? new Vec3d(1.0, 0.0, 0.0) : a.normalize()).multiply(HALF_WIDTH);
        Vec3d b = d.crossProduct(a).normalize().multiply(HALF_WIDTH);

        VertexConsumer vc = vertexConsumers.getBuffer(RenderLayer.getEntityCutoutNoCull(CHAIN));
        MatrixStack.Entry entry = matrices.peek();
        float v = len * 2.0F; // dos tramos de textura por bloque
        plane(vc, entry, d, a, 0.0F, 3.0F / 16.0F, v, light);
        plane(vc, entry, d, b, 3.0F / 16.0F, 6.0F / 16.0F, v, light);
    }

    // Un plano de la cadena desde la daga (0) hasta la mano (d), de ancho 2·w
    private static void plane(VertexConsumer vc, MatrixStack.Entry entry, Vec3d d, Vec3d w, float u0, float u1, float v, int light) {
        Matrix4f m = entry.getPositionMatrix();
        vertex(vc, entry, m, -w.x, -w.y, -w.z, u0, 0.0F, light);
        vertex(vc, entry, m, w.x, w.y, w.z, u1, 0.0F, light);
        vertex(vc, entry, m, d.x + w.x, d.y + w.y, d.z + w.z, u1, v, light);
        vertex(vc, entry, m, d.x - w.x, d.y - w.y, d.z - w.z, u0, v, light);
    }

    private static void vertex(VertexConsumer vc, MatrixStack.Entry entry, Matrix4f m, double x, double y, double z,
                               float u, float v, int light) {
        vc.vertex(m, (float) x, (float) y, (float) z).color(255, 255, 255, 255).texture(u, v)
                .overlay(OverlayTexture.DEFAULT_UV).light(light).normal(entry, 0.0F, 1.0F, 0.0F);
    }
}
