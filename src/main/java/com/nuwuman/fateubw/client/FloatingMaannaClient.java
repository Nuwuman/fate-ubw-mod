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
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
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
    // El arco es el modelo del usuario (tools/blockbench/maanna.bbmodel, partido con tools/maanna_bow.py en cinco ítems):
    // HULL (empuñadura, abanico y cintas, fijo), PROW/STERN (brazos de fuera de arriba y de abajo, que giran alrededor de
    // la empuñadura) e INNER_UP/INNER_DOWN (el creciente de dentro con sus campanas, que gira alrededor de su centro).
    // Las medidas van en unidades del modelo (16 = un bloque)
    private static final ItemStack HULL = new ItemStack(FateUBW.MAANNA_HULL);
    private static final ItemStack PROW = new ItemStack(FateUBW.MAANNA_PROW);
    private static final ItemStack STERN = new ItemStack(FateUBW.MAANNA_STERN);
    private static final ItemStack INNER_UP = new ItemStack(FateUBW.MAANNA_INNER_UP);
    private static final ItemStack INNER_DOWN = new ItemStack(FateUBW.MAANNA_INNER_DOWN);
    private static final float SCALE = 1.1F;                 // tamaño del arco respecto al modelo
    private static final float GRIP_Y = 15.0F, GRIP_Z = 1.0F; // la empuñadura: el origen del dibujo
    private static final float CRESCENT_Z = 15.5F;           // centro del creciente de dentro (su pivote, a la altura de GRIP_Y)
    private static final float TIP_Y = 26.0F, TIP_Z = 27.5F; // boca de la campana de arriba: ahí se ata la cuerda
    private static final float BEND_OUTER = 6.0F, BEND_INNER = 11.0F; // grados que se cierra cada brazo al tensar del todo
    private static final float ARROW = 3.4F;                 // largo de la flecha: tensada del todo, la punta queda bien por delante de la empuñadura
    // Por qué tramo de la tensión va cada uno (1 empezando, 2 a la mitad, 3 al tope), para sonar una vez por tramo
    private static final Map<AbstractClientPlayerEntity, Integer> STAGE = new WeakHashMap<>();
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
        Draw draw = null;
        if (start != null) {
            LAST_DRAWN.put(player, time);
            boolean holding = start == FloatingMaanna.HOLDING;
            float used = holding ? MaannaItem.NP_CHARGE : time - start + tickDelta;
            k = holding ? 1.0F : MathHelper.clamp(used / 6.0F, 0.0F, 1.0F);
            pull = holding ? 1.0F : smoothPull(used);
            // Noble Phantasm: agachada y sin recarga, la carga se tiñe del rosa dorado de Venus hasta los 3 s
            boolean np = holding || MaannaItem.npReady(player);
            draw = new Draw(used, np ? MathHelper.clamp(used / MaannaItem.NP_CHARGE, 0.0F, 1.0F) : 0.0F, time + tickDelta,
                    player == client.player && client.options.getPerspective() == Perspective.FIRST_PERSON);
            if (!holding) sounds(world, player, (int) used, np);
        } else {
            STAGE.remove(player);
            Long last = LAST_DRAWN.get(player);
            k = last == null ? 0.0F : MathHelper.clamp(1.0F - (time - last + tickDelta) / 6.0F, 0.0F, 1.0F);
            pull = 0.0F;
        }
        k = k * k * (3 - 2 * k);
        float t = time + tickDelta + player.getId() * 13;
        float bodyYaw = MathHelper.lerpAngleDegrees(tickDelta, player.prevBodyYaw, player.bodyYaw);
        float headYaw = MathHelper.lerpAngleDegrees(tickDelta, player.prevHeadYaw, player.headYaw);
        float yaw = MathHelper.lerpAngleDegrees(k, bodyYaw, headYaw);
        // Tensada se queda a su derecha, adelantada; en primera persona algo más fuera para no tapar el centro de la pantalla
        boolean ownFirstPerson = player == client.player && client.options.getPerspective() == Perspective.FIRST_PERSON;
        float drawnX = ownFirstPerson ? -1.35F : -1.1F, drawnZ = ownFirstPerson ? 1.0F : 0.5F;

        Vec3d pos = player.getLerpedPos(tickDelta);
        matrices.push();
        matrices.translate(pos.x - cam.x, pos.y - cam.y, pos.z - cam.z);
        // Ejes locales: +X a la izquierda del jugador, +Z hacia delante
        matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(-yaw));
        matrices.translate(MathHelper.lerp(k, -1.4F, drawnX), MathHelper.lerp(k, 1.2F + 0.12F * MathHelper.sin(t * 0.08F), 1.45F),
                MathHelper.lerp(k, 0.1F, drawnZ));
        matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(player.getPitch(tickDelta) * k));
        // Tensada apunta un poco hacia dentro, para que la flecha vaya hacia la mira
        matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(6.0F * k));
        // En reposo se inclina hacia fuera y se mece
        matrices.multiply(RotationAxis.POSITIVE_Z.rotationDegrees((1 - k) * (-10.0F + 3.0F * MathHelper.sin(t * 0.05F))));
        matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees((1 - k) * 4.0F * MathHelper.sin(t * 0.06F)));
        int light = WorldRenderer.getLightmapCoordinates(world, player.getBlockPos().up());
        drawBow(client, world, matrices, consumers, light, pull, draw);
        matrices.pop();
    }

    /** Lo que hace falta para dibujar la flecha: ticks tensando, carga del Noble Phantasm (0-1) y el reloj para el temblor. */
    private record Draw(float used, float np, float clock, boolean firstPerson) {}

    // Como BowItem.getPullProgress pero continuo (con tickDelta), para que la cuerda no avance a saltos
    private static float smoothPull(float used) {
        float f = used / 20.0F;
        return Math.min(1.0F, (f * f + f * 2.0F) / 3.0F);
    }

    // Sonidos al tensar, en el cliente de cada uno que la ve: empieza, cruje a la mitad y encaja al tope
    private static void sounds(ClientWorld world, AbstractClientPlayerEntity player, int used, boolean np) {
        int stage = used >= 20 ? 3 : used >= 10 ? 2 : 1;
        Integer last = STAGE.put(player, stage);
        if (last != null && last >= stage) return;
        double x = player.getX(), y = player.getY(), z = player.getZ();
        if (stage == 1) world.playSound(x, y, z, SoundEvents.ITEM_CROSSBOW_LOADING_START.value(), SoundCategory.PLAYERS, 0.8F, 1.15F, false);
        if (stage == 2) world.playSound(x, y, z, SoundEvents.ITEM_CROSSBOW_LOADING_MIDDLE.value(), SoundCategory.PLAYERS, 0.8F, 1.3F, false);
        if (stage == 3) {
            world.playSound(x, y, z, SoundEvents.ITEM_CROSSBOW_LOADING_END.value(), SoundCategory.PLAYERS, 0.9F, 1.1F, false);
            world.playSound(x, y, z, SoundEvents.BLOCK_AMETHYST_BLOCK_CHIME, SoundCategory.PLAYERS, 1.2F, np ? 0.8F : 1.4F, false);
        }
    }

    // El arco en el plano YZ: la empuñadura en el origen, delante, y las puntas detrás arriba y abajo.
    // Al tensar, los brazos se cierran hacia dentro
    private static void drawBow(MinecraftClient client, ClientWorld world, MatrixStack matrices, VertexConsumerProvider consumers,
                                int light, float pull, Draw draw) {
        float outer = BEND_OUTER * pull, inner = BEND_INNER * pull;
        part(client, world, matrices, consumers, light, HULL, GRIP_Z, 0.0F);
        part(client, world, matrices, consumers, light, PROW, GRIP_Z, -outer);
        part(client, world, matrices, consumers, light, STERN, GRIP_Z, outer);
        part(client, world, matrices, consumers, light, INNER_UP, CRESCENT_Z, -inner);
        part(client, world, matrices, consumers, light, INNER_DOWN, CRESCENT_Z, inner);
        Vec3d top = tip(-inner), bottom = new Vec3d(0.0, -top.y, top.z);   // la de abajo es el espejo de la de arriba
        // Cuerda de luz entre las puntas; tensando, el centro se va hacia atrás y brilla más
        Vec3d middle = top.add(bottom).multiply(0.5).add(0.0, 0.0, -0.9 * pull);
        VertexConsumer vc = consumers.getBuffer(RenderLayer.getLightning());
        Matrix4f m = matrices.peek().getPositionMatrix();
        int[] string = mix(new int[]{255, 240, 200, 170}, new int[]{255, 250, 225, 255}, pull);
        line(vc, m, top, middle, 0.022F, string);
        line(vc, m, middle, bottom, 0.022F, string);
        if (draw == null) return;

        // Flecha de luz encajada en la cuerda: de largo fijo, retrocede con ella. Aparece en 4 ticks, engorda y brilla al
        // tensar, tiembla en tensión máxima, y con el Noble Phantasm se vuelve del rosa dorado de Venus
        float appear = MathHelper.clamp(draw.used() / 4.0F, 0.0F, 1.0F);
        boolean full = draw.used() >= 20;
        Vec3d shake = full ? new Vec3d(0.012 * MathHelper.sin(draw.clock() * 2.7F), 0.012 * MathHelper.cos(draw.clock() * 3.3F), 0.0) : Vec3d.ZERO;
        Vec3d nock = middle.add(shake);
        Vec3d tip = nock.add(0.0, 0.0, ARROW * appear);
        int[] colour = mix(mix(new int[]{255, 235, 190, 160}, new int[]{255, 225, 120, 255}, pull), new int[]{255, 120, 170, 255}, draw.np());
        float thick = (0.02F + 0.03F * pull + 0.02F * draw.np()) * appear;
        line(vc, m, nock, tip, thick, colour);
        // Halo (en primera persona no: de canto y tan cerca de la cámara se ve como un recuadro)
        if (!draw.firstPerson()) line(vc, m, nock, tip, thick * 2.6F, new int[]{colour[0], colour[1], colour[2], (int) (60 * pull)});
        float head = (0.16F + 0.08F * pull + 0.1F * draw.np()) * appear;
        for (Vec3d d : new Vec3d[]{new Vec3d(1, 0, 0), new Vec3d(-1, 0, 0), new Vec3d(0, 1, 0), new Vec3d(0, -1, 0)}) {
            line(vc, m, tip, tip.add(d.multiply(head)).add(0.0, 0.0, -head * 1.4), thick * (draw.firstPerson() ? 0.35F : 0.7F), colour);
        }
        // Al llegar al tope: un destello en la punta que se abre y se apaga en 6 ticks
        float flash = draw.used() - 20;
        if (flash >= 0 && flash < 6) {
            float f = flash / 6.0F, r = 0.15F + 0.5F * f;
            int[] c = {255, 250, 220, (int) (255 * (1 - f))};
            line(vc, m, tip.add(-r, 0, 0), tip.add(r, 0, 0), 0.02F, c);
            line(vc, m, tip.add(0, -r, 0), tip.add(0, r, 0), 0.02F, c);
        }
    }

    private static int[] mix(int[] a, int[] b, float t) {
        int[] out = new int[4];
        for (int i = 0; i < 4; i++) out[i] = (int) MathHelper.lerp(t, a[i], b[i]);
        return out;
    }

    // Una pieza del arco: el modelo girado para que la empuñadura quede delante y las puntas detrás, con 'angle' grados
    // de giro en X alrededor del punto del eje del arco que está a 'pivotZ' (a la altura de la empuñadura)
    private static void part(MinecraftClient client, ClientWorld world, MatrixStack matrices, VertexConsumerProvider consumers,
                             int light, ItemStack piece, float pivotZ, float angle) {
        matrices.push();
        matrices.scale(SCALE, SCALE, SCALE);
        float back = -(pivotZ - GRIP_Z) / 16.0F;          // el pivote respecto a la empuñadura, ya del lado de atrás (-Z)
        matrices.translate(0.0F, 0.0F, back);
        matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(angle));
        matrices.translate(0.0F, 0.0F, -back);
        matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(180.0F));
        matrices.translate(0.0F, -(GRIP_Y - 8.0F) / 16.0F, (8.0F - GRIP_Z) / 16.0F);
        client.getItemRenderer().renderItem(piece, ModelTransformationMode.NONE, light, OverlayTexture.DEFAULT_UV, matrices, consumers, world, 0);
        matrices.pop();
    }

    // Boca de la campana de arriba tras girar el creciente 'angle' grados en X, respecto a la empuñadura
    private static Vec3d tip(float angle) {
        float a = angle * MathHelper.RADIANS_PER_DEGREE, c = MathHelper.cos(a), s = MathHelper.sin(a);
        float y = (TIP_Y - GRIP_Y) / 16.0F * SCALE, z = -(TIP_Z - CRESCENT_Z) / 16.0F * SCALE;
        float pz = -(CRESCENT_Z - GRIP_Z) / 16.0F * SCALE;
        return new Vec3d(0.0, y * c - z * s, pz + y * s + z * c);
    }

    // Una barra de luz de a a b: dos planos cruzados a lo largo de ella, para que se vea desde cualquier lado
    private static void line(VertexConsumer vc, Matrix4f m, Vec3d a, Vec3d b, float t, int[] c) {
        Vec3d dir = b.subtract(a);
        if (dir.lengthSquared() < 1.0E-8) return;
        dir = dir.normalize();
        Vec3d p1 = dir.crossProduct(Math.abs(dir.y) > 0.9 ? new Vec3d(1, 0, 0) : new Vec3d(0, 1, 0)).normalize().multiply(t);
        Vec3d p2 = dir.crossProduct(p1).normalize().multiply(t);
        quad(vc, m, a.subtract(p1), a.add(p1), b.add(p1), b.subtract(p1), c);
        quad(vc, m, a.subtract(p2), a.add(p2), b.add(p2), b.subtract(p2), c);
    }

    private static void quad(VertexConsumer vc, Matrix4f m, Vec3d a, Vec3d b, Vec3d c, Vec3d d, int[] col) {
        Vec3d[] order = {a, b, c, d, d, c, b, a};
        for (Vec3d v : order) vc.vertex(m, (float) v.x, (float) v.y, (float) v.z).color(col[0], col[1], col[2], col[3]);
    }
}
