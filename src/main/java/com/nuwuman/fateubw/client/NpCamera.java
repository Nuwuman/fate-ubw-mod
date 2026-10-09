package com.nuwuman.fateubw.client;

import com.nuwuman.fateubw.PlayerAnims;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.Perspective;
import net.minecraft.util.math.MathHelper;

/**
 * Al lanzar un Noble Phantasm, la cámara pasa a tercera persona y se aleja detrás del jugador para ver el ataque
 * entero; luego vuelve a la vista que tenías. La distancia la aplica CameraMixin.
 */
public final class NpCamera {
    private static final int DURATION = 50, EASE = 10;
    private static final float FAR = 2.6F; // veces la distancia normal de tercera persona
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

    /** Multiplica la distancia de la cámara en tercera persona: sube suave, se mantiene y vuelve. */
    public static float distanceFactor() {
        if (start < 0) return 1.0F;
        float t = elapsedTicks();
        float in = MathHelper.clamp(t / EASE, 0.0F, 1.0F), out = MathHelper.clamp((DURATION - t) / EASE, 0.0F, 1.0F);
        float k = Math.min(in, out);
        k = k * k * (3 - 2 * k);
        return 1.0F + (FAR - 1.0F) * k;
    }
}
