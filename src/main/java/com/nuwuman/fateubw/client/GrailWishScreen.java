package com.nuwuman.fateubw.client;

import com.nuwuman.fateubw.grail.HolyGrailItem;
import com.nuwuman.fateubw.grail.Servants;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.ScreenTexts;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.util.List;

/** "¿Qué deseas?": los ocho servants con su arma; al elegir uno, el Grial concede su poder. */
public class GrailWishScreen extends Screen {
    private static final int BUTTON_W = 120, BUTTON_H = 20, GAP = 6;

    public GrailWishScreen() {
        super(Text.translatable("screen.fate_ubw.wish.title"));
    }

    @Override
    protected void init() {
        List<Servants.Servant> servants = Servants.all();
        int columns = 2;
        int rows = (servants.size() + 1) / columns;
        int left = width / 2 - BUTTON_W - GAP / 2;
        int top = height / 2 - rows * (BUTTON_H + GAP) / 2 + 10;
        for (int i = 0; i < servants.size(); i++) {
            Servants.Servant servant = servants.get(i);
            int x = left + (i % columns) * (BUTTON_W + GAP), y = top + (i / columns) * (BUTTON_H + GAP);
            addDrawableChild(ButtonWidget.builder(Text.translatable("servant.fate_ubw." + servant.id()), button -> {
                ClientPlayNetworking.send(new HolyGrailItem.WishPayload(servant.id()));
                close();
            }).dimensions(x + 20, y, BUTTON_W - 20, BUTTON_H).build());
        }
        addDrawableChild(ButtonWidget.builder(ScreenTexts.CANCEL, button -> close())
                .dimensions(width / 2 - 50, top + rows * (BUTTON_H + GAP) + 8, 100, BUTTON_H).build());
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        super.render(context, mouseX, mouseY, delta);
        List<Servants.Servant> servants = Servants.all();
        int rows = (servants.size() + 1) / 2;
        int left = width / 2 - BUTTON_W - GAP / 2;
        int top = height / 2 - rows * (BUTTON_H + GAP) / 2 + 10;
        context.drawCenteredTextWithShadow(textRenderer, title.copy().formatted(Formatting.GOLD, Formatting.BOLD), width / 2, top - 34, 0xFFFFFF);
        context.drawCenteredTextWithShadow(textRenderer, Text.translatable("screen.fate_ubw.wish.subtitle").formatted(Formatting.GRAY),
                width / 2, top - 20, 0xFFFFFF);
        // El arma de cada servant junto a su botón
        for (int i = 0; i < servants.size(); i++) {
            int x = left + (i % 2) * (BUTTON_W + GAP), y = top + (i / 2) * (BUTTON_H + GAP);
            context.drawItem(new ItemStack(servants.get(i).weapons().get(0)), x, y + 2);
        }
    }

    @Override
    public boolean shouldPause() {
        return false;
    }
}
