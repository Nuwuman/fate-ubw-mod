package com.nuwuman.fateubw.client;

import com.nuwuman.fateubw.ability.Abilities;
import com.nuwuman.fateubw.ability.Ability;
import com.nuwuman.fateubw.ability.CommandSeals;
import com.nuwuman.fateubw.ability.Mana;
import com.nuwuman.fateubw.ability.UseAbilityPayload;
import com.nuwuman.fateubw.berserker.BerserkerArmorItem;
import com.nuwuman.fateubw.mixin.CooldownEntryAccessor;
import com.nuwuman.fateubw.mixin.ItemCooldownManagerAccessor;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.client.util.InputUtil;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import org.lwjgl.glfw.GLFW;

import java.util.List;

/**
 * Las habilidades del conjunto de ropa, en pequeño en la esquina superior izquierda: la seleccionada resaltada, con su recarga,
 * el maná y los Sellos de Comando. R usa la seleccionada, G pasa a la siguiente y V gasta un Sello de Comando
 * (se pueden cambiar en Controles). Lo dibuja InGameHudMixin al final, por encima del chat.
 */
public final class AbilityHud {
    private static final int ROW = 20;
    private static final int TOP = 14; // maná y sellos
    private static final float SCALE = 0.7F;
    private static KeyBinding useKey;
    private static KeyBinding nextKey;
    private static KeyBinding sealKey;
    private static int selected;

    private AbilityHud() {
    }

    public static void register() {
        useKey = KeyBindingHelper.registerKeyBinding(new KeyBinding("key.fate_ubw.ability_use", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_R, "key.category.fate_ubw"));
        nextKey = KeyBindingHelper.registerKeyBinding(new KeyBinding("key.fate_ubw.ability_next", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_G, "key.category.fate_ubw"));
        sealKey = KeyBindingHelper.registerKeyBinding(new KeyBinding("key.fate_ubw.command_seal", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_V, "key.category.fate_ubw"));
        ClientTickEvents.END_CLIENT_TICK.register(AbilityHud::tick);
    }

    private static void tick(MinecraftClient client) {
        ClientPlayerEntity player = client.player;
        if (player == null) return;
        while (sealKey.wasPressed()) ClientPlayNetworking.send(new CommandSeals.UsePayload());
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

    // Ticks que le quedan de verdad a una recarga (con fateCooldownPercent la duración no es la de siempre)
    private static int ticksLeft(PlayerEntity player, Item key) {
        ItemCooldownManagerAccessor cooldowns = (ItemCooldownManagerAccessor) player.getItemCooldownManager();
        Object entry = cooldowns.fateubw$entries().get(key);
        return entry == null ? 0 : Math.max(0, ((CooldownEntryAccessor) entry).fateubw$endTick() - cooldowns.fateubw$tick());
    }

    public static void render(DrawContext context, RenderTickCounter counter) {
        MinecraftClient client = MinecraftClient.getInstance();
        ClientPlayerEntity player = client.player;
        if (player == null || client.options.hudHidden) return;
        List<Ability> abilities = Abilities.of(player);
        if (abilities.isEmpty()) return;

        // Cada capa del HUD vanilla sube 200 de profundidad y el chat va de las últimas: sin esto lo taparía
        context.getMatrices().push();
        context.getMatrices().translate(0.0F, 0.0F, 3000.0F);
        context.getMatrices().scale(SCALE, SCALE, 1.0F);
        draw(context, counter, client, player, abilities);
        context.getMatrices().pop();
    }

    private static void draw(DrawContext context, RenderTickCounter counter, MinecraftClient client, ClientPlayerEntity player,
                             List<Ability> abilities) {
        TextRenderer font = client.textRenderer;
        int width = 110;
        for (Ability ability : abilities) width = Math.max(width, font.getWidth(name(ability)) + 30);
        Text hint = Text.translatable("hud.fate_ubw.keys", useKey.getBoundKeyLocalizedText(), nextKey.getBoundKeyLocalizedText(),
                sealKey.getBoundKeyLocalizedText());
        width = Math.max(width, font.getWidth(hint) + 6);
        boolean berserker = BerserkerArmorItem.fullSet(player);
        int rows = abilities.size() + (berserker ? 1 : 0);
        int height = TOP + rows * ROW + 12;
        // Coordenadas del panel ya escalado: arriba a la izquierda, lejos del arma en mano y del chat
        int x = 4;
        int top = 4;
        int bottom = top + height;

        context.fill(x - 2, top - 2, x + width + 2, bottom, 0x80000000);

        // Maná (barra azul) y Sellos de Comando (rombos rojos; apagados los gastados)
        float mana = Mana.get(player) / Mana.MAX;
        int barWidth = width - 44;
        context.fill(x + 2, top + 2, x + 2 + barWidth, top + 8, 0xFF1A1F3A);
        context.fill(x + 2, top + 2, x + 2 + (int) (barWidth * mana), top + 8, 0xFF4A8CFF);
        int seals = CommandSeals.seals(player);
        for (int i = 0; i < CommandSeals.MAX; i++) {
            int sx = x + width - 36 + i * 12;
            int color = i < seals ? 0xFFE0202A : 0xFF4A2024;
            context.fill(sx + 3, top + 1, sx + 6, top + 10, color);
            context.fill(sx + 1, top + 4, sx + 8, top + 7, color);
        }

        int y = top + TOP;
        for (int i = 0; i < abilities.size(); i++) {
            Ability ability = abilities.get(i);
            int rowY = y + i * ROW;
            if (i == selected) context.fill(x, rowY, x + width, rowY + ROW - 2, 0x60FFD24A);
            context.drawItem(new ItemStack(ability.icon()), x + 2, rowY + 1);

            Item key = ability.key().apply(player);
            float left = player.getItemCooldownManager().getCooldownProgress(key, counter.getTickDelta(true));
            boolean noMana = Mana.get(player) < Mana.costOf(key) && !player.isCreative();
            if (left > 0.0F) {
                // Sombra sobre el icono que baja según se recarga, y los segundos que faltan
                int shade = (int) Math.ceil(16 * left);
                context.fill(x + 2, rowY + 1 + 16 - shade, x + 18, rowY + 17, 200, 0xA0000000);
                int seconds = (int) Math.ceil(ticksLeft(player, key) / 20.0F);
                context.drawTextWithShadow(font, name(ability).copy().formatted(Formatting.GRAY), x + 22, rowY + 1, 0xFFFFFF);
                context.drawTextWithShadow(font, Text.literal(seconds + " s").formatted(Formatting.RED), x + 22, rowY + 10, 0xFFFFFF);
            } else if (noMana) {
                context.drawTextWithShadow(font, name(ability).copy().formatted(Formatting.GRAY), x + 22, rowY + 1, 0xFFFFFF);
                context.drawTextWithShadow(font, Text.translatable("hud.fate_ubw.mana_cost", (int) Mana.costOf(key)).formatted(Formatting.BLUE),
                        x + 22, rowY + 10, 0xFFFFFF);
            } else {
                context.drawTextWithShadow(font, name(ability), x + 22, rowY + 5, i == selected ? 0xFFE27A : 0xFFFFFF);
            }
        }
        if (berserker) {
            int rowY = y + abilities.size() * ROW;
            context.drawTextWithShadow(font, Text.translatable("hud.fate_ubw.god_hand", BerserkerArmorItem.lives(player))
                    .formatted(Formatting.DARK_RED), x + 4, rowY + 5, 0xFFFFFF);
        }
        context.drawTextWithShadow(font, hint.copy().formatted(Formatting.GRAY), x + 2, bottom - 11, 0xFFFFFF);
    }

    private static Text name(Ability ability) {
        return Text.translatable("ability.fate_ubw." + ability.id());
    }
}
