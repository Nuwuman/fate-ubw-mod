package com.nuwuman.fateubw.client;

import com.nuwuman.fateubw.FateUBW;
import com.nuwuman.fateubw.ishtar.FloatingMaanna;
import com.nuwuman.fateubw.ishtar.MaannaItem;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.option.Perspective;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.WorldRenderer;
import net.minecraft.client.render.model.json.ModelTransformationMode;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.item.BowItem;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.tag.BlockTags;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.RotationAxis;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;

import java.util.Map;
import java.util.WeakHashMap;

/**
 * Cliente de la Maanna flotante: la dibuja junto a cada Ishtar (en reposo a su derecha, meciéndose; tensando, delante
 * y apuntando con la mirada) y convierte el click derecho con la mano vacía en tensarla y soltarla.
 */
public final class FloatingMaannaClient {
    private static final float RADIUS = 1.3F;               // radio del arco de la barca
    private static final int SEGMENTS = 14;
    private static final float ARC = 100.0F;                 // grados de cada brazo desde el centro
    private static final ItemStack HULL = new ItemStack(FateUBW.MAANNA_HULL);
    private static final ItemStack PROW = new ItemStack(FateUBW.MAANNA_PROW);
    // Último tick en que se vio tensa a cada jugador, para que vuelva a su sitio suavemente
    private static final Map<AbstractClientPlayerEntity, Long> LAST_DRAWN = new WeakHashMap<>();
    private static boolean drawing;
    private static long pressedAt;

    private FloatingMaannaClient() {
    }

    public static void register() {
        ClientTickEvents.END_CLIENT_TICK.register(FloatingMaannaClient::input);
        WorldRenderEvents.AFTER_ENTITIES.register(FloatingMaannaClient::render);
    }

    // ---------- Click derecho con la mano vacía ----------
    private static void input(MinecraftClient client) {
        if (client.player == null || client.world == null) {
            drawing = false;
            return;
        }
        boolean held = client.options.useKey.isPressed() && client.currentScreen == null;
        boolean usable = FloatingMaanna.usable(client.player);
        if (!drawing) {
            if (held && usable && !interactiveTarget(client)) {
                ClientPlayNetworking.send(new FloatingMaanna.BowPayload(true));
                drawing = true;
                pressedAt = client.world.getTime();
            }
        } else if (!held || !usable) {
            if (usable) MaannaItem.clientRelease(client.player, (int) (client.world.getTime() - pressedAt));
            ClientPlayNetworking.send(new FloatingMaanna.BowPayload(false));
            drawing = false;
        }
    }

    // Con la mano vacía el click derecho también abre puertas, cofres o habla con aldeanos: ahí no se tensa
    private static boolean interactiveTarget(MinecraftClient client) {
        HitResult hit = client.crosshairTarget;
        if (hit instanceof EntityHitResult) return true;
        if (!(hit instanceof BlockHitResult block) || hit.getType() != HitResult.Type.BLOCK) return false;
        BlockState state = client.world.getBlockState(block.getBlockPos());
        return client.world.getBlockEntity(block.getBlockPos()) != null || state.isIn(BlockTags.DOORS) || state.isIn(BlockTags.TRAPDOORS)
                || state.isIn(BlockTags.BUTTONS) || state.isIn(BlockTags.FENCE_GATES) || state.isIn(BlockTags.BEDS) || state.isOf(Blocks.LEVER);
    }

    // ---------- Dibujo ----------
    private static void render(WorldRenderContext context) {
        MinecraftClient client = MinecraftClient.getInstance();
        ClientWorld world = client.world;
        MatrixStack matrices = context.matrixStack();
        VertexConsumerProvider consumers = context.consumers();
        if (world == null || matrices == null || consumers == null) return;
        float tickDelta = context.tickCounter().getTickDelta(false);
        Vec3d cam = context.camera().getPos();
        for (AbstractClientPlayerEntity player : world.getPlayers()) {
            if (!FloatingMaanna.present(player) || player.isInvisible()) continue;
            renderFor(client, world, player, matrices, consumers, cam, tickDelta);
        }
    }

    private static void renderFor(MinecraftClient client, ClientWorld world, AbstractClientPlayerEntity player, MatrixStack matrices,
                                  VertexConsumerProvider consumers, Vec3d cam, float tickDelta) {
        long time = world.getTime();
        Long start = player.getAttached(FloatingMaanna.DRAW);
        float k, pull;
        if (start != null) {
            LAST_DRAWN.put(player, time);
            boolean holding = start == FloatingMaanna.HOLDING;
            k = holding ? 1.0F : MathHelper.clamp((time - start + tickDelta) / 6.0F, 0.0F, 1.0F);
            pull = holding ? 1.0F : BowItem.getPullProgress((int) (time - start));
        } else {
            Long last = LAST_DRAWN.get(player);
            k = last == null ? 0.0F : MathHelper.clamp(1.0F - (time - last + tickDelta) / 6.0F, 0.0F, 1.0F);
            pull = 0.0F;
        }
        k = k * k * (3 - 2 * k);
        float t = time + tickDelta + player.getId() * 13;
        float bodyYaw = MathHelper.lerpAngleDegrees(tickDelta, player.prevBodyYaw, player.bodyYaw);
        float headYaw = MathHelper.lerpAngleDegrees(tickDelta, player.prevHeadYaw, player.headYaw);
        float yaw = MathHelper.lerpAngleDegrees(k, bodyYaw, headYaw);
        // En primera persona, tensada queda a la izquierda para no tapar el centro de la pantalla
        boolean ownFirstPerson = player == client.player && client.options.getPerspective() == Perspective.FIRST_PERSON;
        float drawnX = ownFirstPerson ? 1.35F : 0.3F, drawnZ = ownFirstPerson ? 1.0F : 0.8F;

        Vec3d pos = player.getLerpedPos(tickDelta);
        matrices.push();
        matrices.translate(pos.x - cam.x, pos.y - cam.y, pos.z - cam.z);
        // Ejes locales: +X a la izquierda del jugador, +Z hacia delante
        matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(-yaw));
        matrices.translate(MathHelper.lerp(k, -1.4F, drawnX), MathHelper.lerp(k, 1.2F + 0.12F * MathHelper.sin(t * 0.08F), 1.45F),
                MathHelper.lerp(k, 0.1F, drawnZ));
        matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(player.getPitch(tickDelta) * k));
        // Tensada se gira un poco para que desde atrás se vea la cara de la barca y no solo el canto
        matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(22.0F * k));
        // En reposo se inclina hacia fuera y se mece
        matrices.multiply(RotationAxis.POSITIVE_Z.rotationDegrees((1 - k) * (-10.0F + 3.0F * MathHelper.sin(t * 0.05F))));
        matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees((1 - k) * 4.0F * MathHelper.sin(t * 0.06F)));
        int light = WorldRenderer.getLightmapCoordinates(world, player.getBlockPos().up());
        drawBow(client, world, matrices, consumers, light, pull);
        matrices.pop();
    }

    // El arco en el plano YZ: el centro (la empuñadura) en el origen, curvado hacia delante, puntas detrás arriba y abajo
    private static void drawBow(MinecraftClient client, ClientWorld world, MatrixStack matrices, VertexConsumerProvider consumers,
                                int light, float pull) {
        float step = 2 * ARC / SEGMENTS;
        for (int i = 0; i < SEGMENTS; i++) {
            float theta = -ARC + (i + 0.5F) * step;
            float width = 0.55F + 0.6F * MathHelper.cos(theta * MathHelper.RADIANS_PER_DEGREE * 0.8F);
            place(matrices, theta);
            matrices.scale(width, width, RADIUS * step * MathHelper.RADIANS_PER_DEGREE * 1.2F);
            client.getItemRenderer().renderItem(HULL, ModelTransformationMode.NONE, light, OverlayTexture.DEFAULT_UV, matrices, consumers, world, 0);
            matrices.pop();
        }
        for (float tip : new float[]{-ARC, ARC}) {
            place(matrices, tip);
            matrices.scale(0.7F, 0.7F, 0.7F);
            client.getItemRenderer().renderItem(PROW, ModelTransformationMode.NONE, light, OverlayTexture.DEFAULT_UV, matrices, consumers, world, 0);
            matrices.pop();
        }
        // Cuerda de luz entre las puntas; tensando, el centro se va hacia atrás
        Vec3d top = point(ARC), bottom = point(-ARC);
        Vec3d middle = top.add(bottom).multiply(0.5).add(0.0, 0.0, -0.9 * pull);
        VertexConsumer vc = consumers.getBuffer(RenderLayer.getLightning());
        Matrix4f m = matrices.peek().getPositionMatrix();
        line(vc, m, top, middle);
        line(vc, m, middle, bottom);
    }

    private static Vec3d point(float theta) {
        float a = theta * MathHelper.RADIANS_PER_DEGREE;
        return new Vec3d(0.0, RADIUS * MathHelper.sin(a), RADIUS * (MathHelper.cos(a) - 1.0F));
    }

    // Deja la matriz en el punto del arco, con Z a lo largo de la tangente (hay que hacer pop después)
    private static void place(MatrixStack matrices, float theta) {
        Vec3d p = point(theta);
        matrices.push();
        matrices.translate(p.x, p.y, p.z);
        matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(-(theta + 90.0F)));
    }

    private static void line(VertexConsumer vc, Matrix4f m, Vec3d a, Vec3d b) {
        float t = 0.025F;
        quad(vc, m, a.add(-t, 0, 0), a.add(t, 0, 0), b.add(t, 0, 0), b.add(-t, 0, 0));
        quad(vc, m, a.add(0, 0, -t), a.add(0, 0, t), b.add(0, 0, t), b.add(0, 0, -t));
    }

    private static void quad(VertexConsumer vc, Matrix4f m, Vec3d a, Vec3d b, Vec3d c, Vec3d d) {
        Vec3d[] order = {a, b, c, d, d, c, b, a};
        for (Vec3d v : order) vc.vertex(m, (float) v.x, (float) v.y, (float) v.z).color(255, 240, 200, 230);
    }
}
