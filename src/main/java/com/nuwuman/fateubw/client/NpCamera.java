package com.nuwuman.fateubw.client;

import com.nuwuman.fateubw.PlayerAnims;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.Perspective;
import net.minecraft.util.math.MathHelper;

/**
 * Al lanzar un Noble Phantasm, la cámara pasa a tercera persona detrás del jugador, a la derecha y desde arriba, para
 * ver el ataque entero; luego vuelve a la vista que tenías. Al lanzarlo tiembla y el campo de visión da un golpe, el
 * plano gira despacio mientras dura y salen bandas de cine arriba y abajo. Lo aplican CameraMixin (posición),
 * GameRendererMixin (campo de visión) e InGameHudMixin (bandas).
 */
public final class NpCamera {
    private static final int DURATION = 50, EASE = 10;
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

    /** Bandas negras de cine arriba y abajo, que entran y salen con el plano. */
    public static void drawBars(net.minecraft.client.gui.DrawContext context) {
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
