package com.nuwuman.fateubw.client;

import com.nuwuman.fateubw.saber.BeamClash;
import com.nuwuman.fateubw.saber.ExcaliburBeamEntity;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.MathHelper;

/**
 * El quick time event del choque de Noble Phantasms en pantalla: la tira de teclas a pulsar (W/A/S/D, o las que tengas
 * para moverte), el tiempo que queda y una barra de tira y afloja con la ventaja sobre el rival.
 */
public final class ClashQteClient {
    private static int beam = -1;
    private static String keys = "";
    private static int index;
    private static long endsAt, startedAt, wrongAt = -100, rightAt = -100;

    private ClashQteClient() {
    }

    public static void register() {
        ClientPlayNetworking.registerGlobalReceiver(BeamClash.QtePayload.ID, (payload, context) -> {
            if (context.client().world == null) return;
            long now = context.client().world.getTime();
            if (payload.ticks() <= 0) {
                if (payload.beam() == beam) beam = -1;
                return;
            }
            beam = payload.beam();
            keys = payload.keys();
            index = 0;
            startedAt = now;
            endsAt = now + payload.ticks();
        });
        ClientTickEvents.END_CLIENT_TICK.register(ClashQteClient::input);
        HudRenderCallback.EVENT.register(ClashQteClient::draw);
    }

    private static boolean active(MinecraftClient client) {
        return beam >= 0 && client.world != null && client.world.getTime() < endsAt;
    }

    private static void input(MinecraftClient client) {
        if (!active(client)) {
            beam = -1;
            return;
        }
        var o = client.options;
        while (o.forwardKey.wasPressed()) press(client, 'W');
        while (o.leftKey.wasPressed()) press(client, 'A');
        while (o.backKey.wasPressed()) press(client, 'S');
        while (o.rightKey.wasPressed()) press(client, 'D');
    }

    /** La tecla que toca ahora, o 0 si no hay QTE (para la escena de prueba). */
    public static char next(MinecraftClient client) {
        return active(client) ? keys.charAt(index % keys.length()) : 0;
    }

    /** Pulsa una tecla del QTE (público para la escena de prueba). */
    public static void press(MinecraftClient client, char key) {
        if (!active(client) || client.player == null) return;
        ClientPlayNetworking.send(new BeamClash.KeyPayload(beam, String.valueOf(key)));
        long now = client.world.getTime();
        if (key == keys.charAt(index % keys.length())) {
            index++;
            rightAt = now;
            client.player.playSound(SoundEvents.BLOCK_NOTE_BLOCK_CHIME.value(), 0.6F, 1.0F + Math.min(index, 24) * 0.04F);
        } else {
            wrongAt = now;
            client.player.playSound(SoundEvents.BLOCK_NOTE_BLOCK_BASS.value(), 0.8F, 0.6F);
        }
    }

    private static void draw(DrawContext ctx, net.minecraft.client.render.RenderTickCounter tick) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (!active(client)) return;
        float now = client.world.getTime() + tick.getTickDelta(false);
        int w = ctx.getScaledWindowWidth(), h = ctx.getScaledWindowHeight();
        int cx = w / 2, cy = h / 2 + 12;
        var text = client.textRenderer;

        // Título
        ctx.getMatrices().push();
        ctx.getMatrices().translate(cx, cy - 58, 0);
        ctx.getMatrices().scale(1.6F, 1.6F, 1.0F);
        Text title = Text.translatable("hud.fate_ubw.clash").formatted(Formatting.GOLD, Formatting.BOLD);
        ctx.drawTextWithShadow(text, title, -text.getWidth(title) / 2, 0, 0xFFFFFF);
        ctx.getMatrices().pop();

        // La tira: la tecla que toca, grande, y las cuatro siguientes
        boolean wrong = now - wrongAt < 4, right = now - rightAt < 3;
        for (int i = 4; i >= 0; i--) {
            char key = keys.charAt((index + i) % keys.length());
            int size = i == 0 ? 26 : 16;
            int x = cx - size / 2 + (i == 0 ? 0 : 6 + i * 20), y = cy - size / 2 - (i == 0 && right ? 2 : 0);
            int border = i == 0 ? (wrong ? 0xFFE03030 : 0xFFF2C14E) : 0xFF8A8A8A;
            int fill = i == 0 ? (wrong ? 0xC0601010 : 0xC0201810) : 0x90101010;
            ctx.fill(x - 1, y - 1, x + size + 1, y + size + 1, border);
            ctx.fill(x, y, x + size, y + size, fill);
            String label = label(client, key);
            float scale = i == 0 ? 2.0F : 1.0F;
            ctx.getMatrices().push();
            ctx.getMatrices().translate(x + size / 2.0F, y + size / 2.0F - 4 * scale, 0);
            ctx.getMatrices().scale(scale, scale, 1.0F);
            ctx.drawTextWithShadow(text, label, -text.getWidth(label) / 2, 0, i == 0 ? 0xFFFFFF : 0xB0B0B0);
            ctx.getMatrices().pop();
        }

        // Tira y afloja: la ventaja sobre el rival (llena del todo, ganas el choque)
        int push = client.world.getEntityById(beam) instanceof ExcaliburBeamEntity b ? b.push() : 0;
        int barW = 160, barY = cy + 24;
        ctx.fill(cx - barW / 2 - 1, barY - 1, cx + barW / 2 + 1, barY + 7, 0xFF000000);
        ctx.fill(cx - barW / 2, barY, cx + barW / 2, barY + 6, 0xFF3A3A3A);
        int fillW = (int) (barW / 2.0F * MathHelper.clamp(Math.abs(push) / (float) ExcaliburBeamEntity.KO_POINTS, 0.0F, 1.0F));
        if (push > 0) ctx.fill(cx, barY, cx + fillW, barY + 6, 0xFFF2C14E);
        if (push < 0) ctx.fill(cx - fillW, barY, cx, barY + 6, 0xFFD03030);
        ctx.fill(cx - 1, barY - 2, cx + 1, barY + 8, 0xFFFFFFFF);

        // Tiempo restante
        float left = MathHelper.clamp((endsAt - now) / (float) (endsAt - startedAt), 0.0F, 1.0F);
        ctx.fill(cx - barW / 2, barY + 10, cx - barW / 2 + (int) (barW * left), barY + 12, 0xFFE8E8E8);
    }

    // Lo que pone en la tecla: la que tengas asignada a moverte en esa dirección (W/A/S/D por defecto)
    private static String label(MinecraftClient client, char key) {
        KeyBinding bind = switch (key) {
            case 'W' -> client.options.forwardKey;
            case 'A' -> client.options.leftKey;
            case 'S' -> client.options.backKey;
            default -> client.options.rightKey;
        };
        String name = bind.getBoundKeyLocalizedText().getString();
        return name.length() <= 3 ? name.toUpperCase() : String.valueOf(key);
    }
}
