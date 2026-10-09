package com.nuwuman.fateubw.ability;

import com.mojang.serialization.Codec;
import com.nuwuman.fateubw.FateUBW;
import com.nuwuman.fateubw.Rules;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentSyncPredicate;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.util.HashMap;
import java.util.Map;

/**
 * Maná: un depósito de 100 que gastan los Noble Phantasm y las habilidades (además de su recarga) y se rellena solo
 * en unos 50 s. El coste va por la clave de recarga de cada habilidad. En creativo o con /gamerule fateMana false no
 * se gasta.
 */
public final class Mana {
    public static final float MAX = 100.0F;
    private static final float REGEN_PER_SECOND = 2.0F;
    public static final AttachmentType<Float> MANA = AttachmentRegistry.<Float>builder()
            .persistent(Codec.FLOAT).copyOnDeath().initializer(() -> MAX)
            .syncWith(PacketCodecs.FLOAT.cast(), AttachmentSyncPredicate.targetOnly())
            .buildAndRegister(FateUBW.id("mana"));
    private static final Map<Item, Float> COSTS = new HashMap<>();

    private Mana() {
    }

    public static void register() {
        cost(FateUBW.EXCALIBUR_NP, 50); cost(FateUBW.STRIKE_AIR, 10); cost(FateUBW.AVALON, 40); cost(FateUBW.MANA_BURST, 10);
        cost(FateUBW.RHO_AIAS, 20); cost(FateUBW.CALADBOLG, 25); cost(FateUBW.TRACE_ON, 10); cost(FateUBW.UBW_COOLDOWN, 60);
        cost(FateUBW.UBW_BARRAGE, 5); cost(FateUBW.HRUNTING, 20);
        cost(FateUBW.GAE_BOLG_PIERCE, 25); cost(FateUBW.GAE_BOLG_SOARING, 35); cost(FateUBW.ANSUZ, 10);
        cost(FateUBW.MYSTIC_EYES, 20); cost(FateUBW.BELLEROPHON, 10); cost(FateUBW.BELLEROPHON_CHARGE, 40);
        cost(FateUBW.BLOOD_FORT_ANDROMEDA, 35);
        cost(FateUBW.ENUMA_ELISH_NP, 50); cost(FateUBW.GATE_OF_BABYLON, 25); cost(FateUBW.ENKIDU, 25);
        cost(FateUBW.RULE_BREAKER_NP, 20); cost(FateUBW.DIVINE_WORDS, 15); cost(FateUBW.SPATIAL_TRANSFER, 10);
        cost(FateUBW.TSUBAME_GAESHI, 25); cost(FateUBW.PRESENCE_CONCEALMENT, 15);
        cost(FateUBW.NINE_LIVES, 35); cost(FateUBW.MAD_ENHANCEMENT, 20);
        cost(FateUBW.AN_GAL_TA_KIGAL_SHE_NP, 50); cost(FateUBW.JEWEL_BURST, 20); cost(FateUBW.MANIFESTATION_OF_BEAUTY, 15);
        cost(FateUBW.SKY_BOAT, 10);

        // Se recarga sola; se sincroniza con su cliente cada medio segundo como mucho
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            if (server.getTicks() % 10 != 0) return;
            for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
                float mana = get(player);
                if (mana < MAX) player.setAttached(MANA, Math.min(MAX, mana + REGEN_PER_SECOND / 2));
            }
        });
    }

    private static void cost(Item key, float mana) {
        COSTS.put(key, mana);
    }

    public static float costOf(Item key) {
        return COSTS.getOrDefault(key, 0.0F);
    }

    public static float get(PlayerEntity player) {
        Float mana = player.getAttached(MANA);
        return mana == null ? MAX : mana;
    }

    private static boolean free(PlayerEntity player) {
        return player.isCreative() || (!player.getWorld().isClient && !player.getWorld().getGameRules().getBoolean(Rules.MANA));
    }

    /** ¿Le llega el maná? Si no, avisa (en el servidor). Vale en los dos lados: el cliente conoce su maná. */
    public static boolean has(PlayerEntity player, Item key) {
        float cost = costOf(key);
        if (cost <= 0 || free(player) || get(player) >= cost) return true;
        if (!player.getWorld().isClient) {
            player.sendMessage(Text.translatable("message.fate_ubw.no_mana", (int) get(player), (int) cost).formatted(Formatting.BLUE), true);
        }
        return false;
    }

    public static void spend(PlayerEntity player, Item key) {
        if (player.getWorld().isClient || free(player)) return;
        player.setAttached(MANA, Math.max(0.0F, get(player) - costOf(key)));
    }

    public static void fill(PlayerEntity player) {
        player.setAttached(MANA, MAX);
    }
}
