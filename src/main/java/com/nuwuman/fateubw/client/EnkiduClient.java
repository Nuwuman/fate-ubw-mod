package com.nuwuman.fateubw.client;

import com.nuwuman.fateubw.gilgamesh.EnkiduEffect;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.LightmapTextureManager;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

/**
 * Enkidu a la vista: mientras alguien está atado, cuatro cadenas salen de portales dorados a su alrededor, lo agarran
 * y le dan dos vueltas al cuerpo (textura propia: eslabones de oro, como la cadena vanilla).
 */
public final class EnkiduClient {
    private static final Identifier CHAIN = com.nuwuman.fateubw.FateUBW.id("textures/misc/enkidu_chain.png");
    private static final float LINK_WIDTH = 0.13F;            // medio ancho de la cadena (la vanilla es 3/16, aquí más gruesa)
    private static final float SHOOT = 5.0F, RETRACT = 6.0F;  // ticks en llegar y en recogerse
    private static final int TINT = 0xFFFFFFFF;

    private EnkiduClient() {
    }

    public static void register() {
        WorldRenderEvents.AFTER_ENTITIES.register(EnkiduClient::render);
    }

    private static void render(WorldRenderContext context) {
        ClientWorld world = MinecraftClient.getInstance().world;
        MatrixStack matrices = context.matrixStack();
        VertexConsumerProvider consumers = context.consumers();
        if (world == null || matrices == null || consumers == null) return;
        float tickDelta = context.tickCounter().getTickDelta(false);
        Vec3d cam = context.camera().getPos();
        for (Entity entity : world.getEntities()) {
            if (!(entity instanceof LivingEntity target)) continue;
            Long until = target.getAttached(EnkiduEffect.BOUND_UNTIL);
            if (until == null) continue;
            float left = until - world.getTime() - tickDelta;
            if (left <= 0 || !target.isAlive()) continue;
            float age = EnkiduEffect.TICKS - left;
            float reach = MathHelper.clamp(age / SHOOT, 0.0F, 1.0F) * MathHelper.clamp(left / RETRACT, 0.0F, 1.0F);
            matrices.push();
            matrices.translate(-cam.x, -cam.y, -cam.z);
            draw(matrices, consumers, target, tickDelta, age, reach);
            matrices.pop();
        }
    }

    private static void draw(MatrixStack matrices, VertexConsumerProvider consumers, LivingEntity target, float tickDelta, float age, float reach) {
        MatrixStack.Entry entry = matrices.peek();
        Vec3d pos = target.getLerpedPos(tickDelta);
        float h = target.getHeight(), r = target.getWidth() * 0.6F + 0.12F;
        Vec3d[] anchors = EnkiduEffect.anchors(target);
        for (int i = 0; i < anchors.length; i++) {
            // Cada cadena agarra a una altura distinta y en el lado de su portal
            Vec3d anchor = anchors[i];
            double grabY = pos.y + h * (i % 2 == 0 ? 0.7 : 0.4);
            Vec3d side = new Vec3d(anchor.x - pos.x, 0.0, anchor.z - pos.z).normalize().multiply(r);
            Vec3d grab = new Vec3d(pos.x + side.x, grabY, pos.z + side.z);
            link(consumers, entry, anchor, anchor.lerp(grab, reach), age * 0.02F);
            portal(matrices, consumers, anchor, grab.subtract(anchor).normalize(), age, reach);
        }
        // Dos vueltas alrededor del cuerpo, cuando las cadenas ya han llegado
        if (reach < 0.95F) return;
        for (double level : new double[]{0.38, 0.72}) {
            int n = 10;
            for (int k = 0; k < n; k++) {
                double a0 = Math.PI * 2 * k / n + level * 3, a1 = Math.PI * 2 * (k + 1) / n + level * 3;
                Vec3d p0 = new Vec3d(pos.x + Math.cos(a0) * r, pos.y + h * level + (k % 2) * 0.03, pos.z + Math.sin(a0) * r);
                Vec3d p1 = new Vec3d(pos.x + Math.cos(a1) * r, pos.y + h * level + ((k + 1) % 2) * 0.03, pos.z + Math.sin(a1) * r);
                link(consumers, entry, p0, p1, k * 0.37F);
            }
        }
    }

    // Una cadena recta de a a b: dos planos cruzados con la textura vanilla repetida (un bloque de largo por textura)
    // Pide su capa cada vez: cambiar de capa (el portal) cierra la anterior
    private static void link(VertexConsumerProvider consumers, MatrixStack.Entry entry, Vec3d a, Vec3d b, float vOffset) {
        VertexConsumer vc = consumers.getBuffer(RenderLayer.getEntityCutoutNoCull(CHAIN));
        Vec3d d = b.subtract(a);
        double length = d.length();
        if (length < 1.0E-3) return;
        Vec3d dir = d.multiply(1.0 / length);
        Vec3d p1 = dir.crossProduct(Math.abs(dir.y) > 0.9 ? new Vec3d(1, 0, 0) : new Vec3d(0, 1, 0)).normalize().multiply(LINK_WIDTH);
        Vec3d p2 = dir.crossProduct(p1).normalize().multiply(LINK_WIDTH);
        for (double s = 0; s < length; s += 1.0) {
            double e = Math.min(length, s + 1.0);
            Vec3d s0 = a.add(dir.multiply(s)), s1 = a.add(dir.multiply(e));
            float v0 = vOffset, v1 = vOffset + (float) (e - s);
            plane(vc, entry, s0, s1, p1, 0.0F, 3.0F / 16, v0, v1);
            plane(vc, entry, s0, s1, p2, 3.0F / 16, 6.0F / 16, v0, v1);
        }
    }

    private static void plane(VertexConsumer vc, MatrixStack.Entry entry, Vec3d s0, Vec3d s1, Vec3d w, float u0, float u1, float v0, float v1) {
        // La textura no se repite sola: v se queda en [0, 1) cortando por el número de bloques entero
        float shift = (float) Math.floor(v0);
        v0 -= shift;
        v1 -= shift;
        vertex(vc, entry, s0.subtract(w), u0, v0);
        vertex(vc, entry, s0.add(w), u1, v0);
        vertex(vc, entry, s1.add(w), u1, v1);
        vertex(vc, entry, s1.subtract(w), u0, v1);
    }

    private static void vertex(VertexConsumer vc, MatrixStack.Entry entry, Vec3d p, float u, float v) {
        vc.vertex(entry, (float) p.x, (float) p.y, (float) p.z).color(TINT).texture(u, v).overlay(OverlayTexture.DEFAULT_UV)
                .light(LightmapTextureManager.MAX_LIGHT_COORDINATE).normal(entry, 0.0F, 1.0F, 0.0F);
    }

    // El portal dorado del que sale la cadena: el mismo remolino que el Gate of Babylon, de cara al atado
    private static void portal(MatrixStack matrices, VertexConsumerProvider consumers, Vec3d center, Vec3d normal, float age, float open) {
        matrices.push();
        matrices.translate(center.x, center.y, center.z);
        matrices.multiply(new org.joml.Quaternionf().rotationTo(0.0F, 1.0F, 0.0F, (float) normal.x, (float) normal.y, (float) normal.z));
        matrices.multiply(net.minecraft.util.math.RotationAxis.POSITIVE_Y.rotationDegrees(age * 6.0F));
        BabylonPortalRenderer.drawPortal(consumers.getBuffer(RenderLayer.getLightning()), matrices.peek().getPositionMatrix(),
                0.6F * Math.max(open, 0.2F), age);
        matrices.pop();
    }
}
