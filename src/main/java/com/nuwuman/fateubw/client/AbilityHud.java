package com.nuwuman.fateubw.client;

import com.nuwuman.fateubw.ability.Abilities;
import com.nuwuman.fateubw.ability.Ability;
import com.nuwuman.fateubw.ability.UseAbilityPayload;
import com.nuwuman.fateubw.berserker.BerserkerArmorItem;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import org.lwjgl.glfw.GLFW;

import java.util.List;

/**
 * Las habilidades del conjunto de ropa en la esquina inferior derecha: la seleccionada resaltada, con su cooldown.
 * R usa la seleccionada y G pasa a la siguiente (se pueden cambiar en Controles).
 */
public final class AbilityHud {
    private static final int ROW = 20;
    private static KeyBinding useKey;
    private static KeyBinding nextKey;
    private static int selected;

    private AbilityHud() {
    }

    public static void register() {
        useKey = KeyBindingHelper.registerKeyBinding(new KeyBinding("key.fate_ubw.ability_use", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_R, "key.category.fate_ubw"));
        nextKey = KeyBindingHelper.registerKeyBinding(new KeyBinding("key.fate_ubw.ability_next", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_G, "key.category.fate_ubw"));
        ClientTickEvents.END_CLIENT_TICK.register(AbilityHud::tick);
        HudRenderCallback.EVENT.register(AbilityHud::render);
    }

    private static void tick(MinecraftClient client) {
        ClientPlayerEntity player = client.player;
        if (player == null) return;
        List<Ability> abilities = Abilities.of(player);
        if (abilities.isEmpty()) {
            selected = 0;
            while (useKey.wasPressed()) ;
            while (nextKey.wasPressed()) ;
            return;
        }
        while (nextKey.wasPressed()) selected = (selected + 1) % abilities.size();
        if (selected >= abilities.size()) selected = 0;
        while (useKey.wasPressed()) ClientPlayNetworking.send(new UseAbilityPayload(selected));
    }

    private static void render(DrawContext context, net.minecraft.client.render.RenderTickCounter counter) {
        MinecraftClient client = MinecraftClient.getInstance();
        ClientPlayerEntity player = client.player;
        if (player == null || client.options.hudHidden) return;
        List<Ability> abilities = Abilities.of(player);
        if (abilities.isEmpty()) return;

        TextRenderer font = client.textRenderer;
        int width = 0;
        for (Ability ability : abilities) width = Math.max(width, font.getWidth(name(ability)) + 30);
        Text hint = Text.translatable("hud.fate_ubw.keys", useKey.getBoundKeyLocalizedText(), nextKey.getBoundKeyLocalizedText());
        width = Math.max(width, font.getWidth(hint) + 6);
        boolean berserker = BerserkerArmorItem.fullSet(player);
        int rows = abilities.size() + (berserker ? 1 : 0);
        int x = context.getScaledWindowWidth() - width - 4;
        int bottom = context.getScaledWindowHeight() - 4;
        // En pantallas estrechas se pisaría con la barra de objetos: entonces va justo encima de ella
        if (x < context.getScaledWindowWidth() / 2 + 96) bottom -= 24;
        int y = bottom - rows * ROW - 12;

        context.fill(x - 2, y - 2, x + width + 2, y + rows * ROW + 12, 0x80000000);
        float tickDelta = counter.getTickDelta(true);
        for (int i = 0; i < abilities.size(); i++) {
            Ability ability = abilities.get(i);
            int rowY = y + i * ROW;
            if (i == selected) context.fill(x, rowY, x + width, rowY + ROW - 2, 0x60FFD24A);
            context.drawItem(new ItemStack(ability.icon()), x + 2, rowY + 1);

            Item key = ability.key().apply(player);
            float left = player.getItemCooldownManager().getCooldownProgress(key, tickDelta);
            if (left > 0.0F) {
                // Sombra sobre el icono que baja según se recarga, y los segundos que faltan
                int shade = (int) Math.ceil(16 * left);
                context.fill(x + 2, rowY + 1 + 16 - shade, x + 18, rowY + 17, 200, 0xA0000000);
                int seconds = (int) Math.ceil(left * Abilities.cooldownOf(key) / 20.0F);
                context.drawTextWithShadow(font, name(ability).copy().formatted(Formatting.GRAY), x + 22, rowY + 1, 0xFFFFFF);
                context.drawTextWithShadow(font, Text.literal(seconds + " s").formatted(Formatting.RED), x + 22, rowY + 10, 0xFFFFFF);
            } else {
                context.drawTextWithShadow(font, name(ability), x + 22, rowY + 5, i == selected ? 0xFFE27A : 0xFFFFFF);
            }
        }
        if (berserker) {
            int rowY = y + abilities.size() * ROW;
            context.drawTextWithShadow(font, Text.translatable("hud.fate_ubw.god_hand", BerserkerArmorItem.lives(player))
                    .formatted(Formatting.DARK_RED), x + 4, rowY + 5, 0xFFFFFF);
        }
        context.drawTextWithShadow(font, hint.copy().formatted(Formatting.GRAY), x + 2, y + rows * ROW + 1, 0xFFFFFF);
    }

    private static Text name(Ability ability) {
        return Text.translatable("ability.fate_ubw." + ability.id());
    }
}
