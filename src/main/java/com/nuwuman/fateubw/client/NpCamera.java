package com.nuwuman.fateubw.client;

import com.nuwuman.fateubw.PlayerAnims;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.option.Perspective;
import net.minecraft.entity.Entity;
import net.minecraft.util.math.MathHelper;

import java.util.HashSet;
import java.util.Set;

/**
 * Al lanzar un Noble Phantasm, la cámara pasa a tercera persona detrás del jugador, a la derecha y desde arriba, para
 * ver el ataque entero; luego vuelve a la vista que tenías. Al lanzarlo tiembla y el campo de visión da un golpe, el
 * plano gira despacio mientras dura y salen bandas de cine arriba y abajo. Lo aplican CameraMixin (posición),
 * GameRendererMixin (campo de visión) e InGameHudMixin (bandas).
 */
public final class NpCamera {
    private static final int DURATION = 50 + PlayerAnims.WINDUP, EASE = 10;
    private static final float FAR = 1.25F; // veces la distancia normal de tercera persona
    private static final float ORBIT = 45.0F;
    private static final float TILT = 20.0F;
    private static final float DRIFT = 10.0F;        // grados extra que gira despacio durante el plano
    private static final int SHAKE_TICKS = 10;
    private static final float FOV_PUNCH = 7.0F;     // grados de campo de visión que se abren al lanzarlo
    private static final float BARS = 0.085F;        // alto de cada banda de cine, en fracción de la pantalla
    private static long start = -1;
    private static long ticks;
    private static Perspective previous;

    private NpCamera() {
    }

    public static void register() {
        PlayerAnims.clientCinematic = player -> {
            MinecraftClient client = MinecraftClient.getInstance();
            if (player != client.player) return;
            if (start < 0) previous = client.options.getPerspective();
            client.options.setPerspective(Perspective.THIRD_PERSON_BACK);
            start = ticks;
        };
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            ticks++;
            if (start >= 0 && elapsedTicks() >= DURATION) {
                start = -1;
                client.options.setPerspective(previous);
            }
        });
    }

    // En ticks de juego, con la fracción del frame para que el movimiento sea suave
    private static float elapsedTicks() {
        return ticks - start + MinecraftClient.getInstance().getRenderTickCounter().getTickDelta(false);
    }

    // 0..1: sube suave, se mantiene y vuelve
    private static float ease() {
        if (start < 0) return 0.0F;
        float t = elapsedTicks();
        float in = MathHelper.clamp(t / EASE, 0.0F, 1.0F), out = MathHelper.clamp((DURATION - t) / EASE, 0.0F, 1.0F);
        float k = Math.min(in, out);
        return k * k * (3 - 2 * k);
    }

    /** Multiplica la distancia de la cámara en tercera persona. */
    public static float distanceFactor() {
        return 1.0F + (FAR - 1.0F) * ease();
    }

    public static boolean active() {
        return start >= 0;
    }

    /** Grados que la cámara gira alrededor del jugador: queda detrás y a la derecha, en diagonal, y sigue girando despacio. */
    public static float orbit() {
        if (start < 0) return 0.0F;
        return (ORBIT + DRIFT * MathHelper.clamp(elapsedTicks() / DURATION, 0.0F, 1.0F)) * ease();
    }

    // Temblor al lanzarlo, que se apaga en medio segundo
    private static float shake(float frequency) {
        if (start < 0) return 0.0F;
        float t = elapsedTicks(), decay = MathHelper.clamp(1.0F - t / SHAKE_TICKS, 0.0F, 1.0F);
        return decay * decay * MathHelper.sin(t * frequency);
    }

    public static float shakeYaw() {
        return 1.4F * shake(2.9F);
    }

    public static float shakePitch() {
        return 0.9F * shake(3.7F);
    }

    /** Grados que se abre el campo de visión: un golpe rápido al lanzarlo que vuelve poco a poco. */
    public static float fovPunch() {
        if (start < 0) return 0.0F;
        float t = elapsedTicks();
        return FOV_PUNCH * (t < 3 ? t / 3 : MathHelper.clamp(1.0F - (t - 3) / 15, 0.0F, 1.0F));
    }

    // ---------- Impacto de un Noble Phantasm cercano: destello de pantalla y sacudida, para quien esté cerca ----------
    private static final Set<Integer> IMPACTED = new HashSet<>();
    private static final float IMPACT_RANGE = 80.0F;
    private static long impactStart = -1;
    private static float impactPower;
    private static int flashColor;

    /** Lo llama el renderer de un haz en sus primeros ticks; cada haz cuenta una vez. Más fuerte cuanto más cerca. */
    public static void impact(Entity source, int rgb, float power) {
        if (source.age > 2 || IMPACTED.contains(source.getId())) return;
        if (IMPACTED.size() > 64) IMPACTED.clear();
        IMPACTED.add(source.getId());
        double distance = MinecraftClient.getInstance().gameRenderer.getCamera().getPos().distanceTo(source.getPos());
        float strength = MathHelper.clamp(1.0F - (float) distance / IMPACT_RANGE, 0.0F, 1.0F) * power;
        if (strength < 0.05F) return;
        impactStart = ticks;
        impactPower = Math.min(1.2F, strength);
        flashColor = rgb;
    }

    private static float sinceImpact() {
        return ticks - impactStart + MinecraftClient.getInstance().getRenderTickCounter().getTickDelta(false);
    }

    private static float impactShake(float frequency) {
        if (impactStart < 0) return 0.0F;
        float t = sinceImpact(), decay = MathHelper.clamp(1.0F - t / 16.0F, 0.0F, 1.0F);
        return impactPower * decay * decay * MathHelper.sin(t * frequency);
    }

    public static float impactYaw() {
        return 2.2F * impactShake(3.1F);
    }

    public static float impactPitch() {
        return 1.6F * impactShake(4.3F);
    }

    /** Destello del color del haz que se apaga en poco más de medio segundo. */
    public static void drawFlash(DrawContext context) {
        if (impactStart < 0) return;
        float alpha = impactPower * 0.45F * MathHelper.clamp(1.0F - sinceImpact() / 12.0F, 0.0F, 1.0F);
        if (alpha <= 0.0F) return;
        context.fill(0, 0, context.getScaledWindowWidth(), context.getScaledWindowHeight(),
                ((int) (alpha * 255) << 24) | flashColor);
    }

    /** Bandas negras de cine arriba y abajo, que entran y salen con el plano. */
    public static void drawBars(DrawContext context) {
        float k = ease();
        if (k <= 0.0F) return;
        int w = context.getScaledWindowWidth(), h = context.getScaledWindowHeight(), bar = Math.round(h * BARS * k);
        context.fill(0, 0, w, bar, 0xFF000000);
        context.fill(0, h - bar, w, h, 0xFF000000);
    }

    /** Grados que la cámara mira hacia abajo: al retroceder por esa línea queda más alta, como vista desde arriba. */
    public static float tilt() {
        return TILT * ease();
    }
}
