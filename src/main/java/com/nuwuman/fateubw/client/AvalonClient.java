package com.nuwuman.fateubw.client;

import com.nuwuman.fateubw.FateUBW;
import com.nuwuman.fateubw.saber.SaberArmorItem;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.render.LightmapTextureManager;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.RotationAxis;
import net.minecraft.util.math.Vec3d;

/**
 * Avalon a la vista: mientras dura, la vaina flota delante del jugador y se despliega en fragmentos de oro y esmalte
 * azul que giran a su alrededor como una barrera (como en el anime, donde Avalon se abre en cientos de piezas).
 */
public final class AvalonClient {
    private static final Identifier TEXTURE = FateUBW.id("textures/misc/avalon.png");
    private static final float WIDTH = 0.64F, HEIGHT = 1.7F;     // misma proporción que la textura (48x128)
    private static final int OPEN = 8, CLOSE = 12;            // ticks en desplegarse y en recogerse
    private static final float[] RING_HEIGHTS = {0.25F, 1.0F, 1.75F};
    private static final int PER_RING = 9;
    private static final float RING_RADIUS = 1.7F;

    private AvalonClient() {
    }

    public static void register() {
        WorldRenderEvents.AFTER_ENTITIES.register(AvalonClient::render);
    }

    private static void render(WorldRenderContext context) {
        MinecraftClient client = MinecraftClient.getInstance();
        ClientWorld world = client.world;
        MatrixStack matrices = context.matrixStack();
        VertexConsumerProvider consumers = context.consumers();
        if (world == null || matrices == null || consumers == null) return;
        float tickDelta = context.tickCounter().getTickDelta(false);
        Vec3d cam = context.camera().getPos();
        for (AbstractClientPlayerEntity player : world.getPlayers()) {
            Long until = player.getAttached(SaberArmorItem.AVALON_UNTIL);
            if (until == null) continue;
            float left = until - world.getTime() - tickDelta;
            float age = SaberArmorItem.AVALON_TICKS - left;
            if (left <= 0 || age < 0) continue;
            float open = MathHelper.clamp(age / OPEN, 0.0F, 1.0F) * MathHelper.clamp(left / CLOSE, 0.0F, 1.0F);
            open = open * open * (3 - 2 * open);
            renderFor(client, world, player, matrices, consumers, cam, tickDelta, age, open);
        }
    }

    private static void renderFor(MinecraftClient client, ClientWorld world, AbstractClientPlayerEntity player, MatrixStack matrices,
                                  VertexConsumerProvider consumers, Vec3d cam, float tickDelta, float age, float open) {
        int light = LightmapTextureManager.MAX_LIGHT_COORDINATE;
        Vec3d pos = player.getLerpedPos(tickDelta);
        float yaw = MathHelper.lerpAngleDegrees(tickDelta, player.prevBodyYaw, player.bodyYaw);
        matrices.push();
        matrices.translate(pos.x - cam.x, pos.y - cam.y, pos.z - cam.z);
        matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(-yaw));

        // La vaina delante, de cara al enemigo: la boca a la altura de las manos y la punta hacia el suelo (en primera persona taparía la vista)
        boolean ownFirstPerson = player == client.player && client.options.getPerspective().isFirstPerson();
        if (!ownFirstPerson) {
            matrices.push();
            matrices.translate(0.0, 0.05 + 0.04 * MathHelper.sin(age * 0.15F), 0.7);
            matrices.multiply(RotationAxis.POSITIVE_Z.rotationDegrees(3.0F * MathHelper.sin(age * 0.1F)));
            float size = Math.max(open, 0.35F);
            matrices.scale(size, size, size);
            drawScabbard(matrices, consumers, light);
            matrices.pop();
        }

        // Los fragmentos salen de la vaina y forman anillos alrededor; cada anillo gira en un sentido
        Vec3d from = new Vec3d(0.0, 1.2, 0.85);
        for (int ring = 0; ring < RING_HEIGHTS.length; ring++) {
            float spin = age * (ring % 2 == 0 ? 2.5F : -2.0F) + ring * 15.0F;
            for (int i = 0; i < PER_RING; i++) {
                float angle = spin + i * 360.0F / PER_RING;
                float rad = angle * MathHelper.RADIANS_PER_DEGREE;
                Vec3d to = new Vec3d(MathHelper.sin(rad) * RING_RADIUS, RING_HEIGHTS[ring], MathHelper.cos(rad) * RING_RADIUS);
                Vec3d at = from.lerp(to, open);
                matrices.push();
                matrices.translate(at.x, at.y, at.z);
                matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(angle));   // mirando hacia fuera
                matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(-12.0F * (ring - 1)));
                float s = 0.3F * open;  // cada fragmento, una Avalon en pequeño
                matrices.scale(s, s, s);
                matrices.translate(0.0, -HEIGHT / 2, 0.0);
                drawScabbard(matrices, consumers, light);
                matrices.pop();
            }
        }
        matrices.pop();
    }

    // La vaina: una placa con su textura, visible por las dos caras; la punta abajo, en el origen
    private static void drawScabbard(MatrixStack matrices, VertexConsumerProvider consumers, int light) {
        VertexConsumer vc = consumers.getBuffer(RenderLayer.getEntityCutoutNoCull(TEXTURE));
        MatrixStack.Entry entry = matrices.peek();
        float w = WIDTH / 2;
        vertex(vc, entry, -w, 0, 1, 1, light);
        vertex(vc, entry, w, 0, 0, 1, light);
        vertex(vc, entry, w, HEIGHT, 0, 0, light);
        vertex(vc, entry, -w, HEIGHT, 1, 0, light);
    }

    private static void vertex(VertexConsumer vc, MatrixStack.Entry entry, float x, float y, float u, float v, int light) {
        vc.vertex(entry, x, y, 0.0F).color(0xFFFFFFFF).texture(u, v).overlay(OverlayTexture.DEFAULT_UV).light(light).normal(entry, 0.0F, 0.0F, 1.0F);
    }
}
