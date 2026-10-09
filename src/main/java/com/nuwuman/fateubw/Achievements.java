package com.nuwuman.fateubw;

import net.minecraft.advancement.AdvancementEntry;
import net.minecraft.entity.Entity;
import net.minecraft.server.network.ServerPlayerEntity;

import java.util.Set;

/**
 * Logros del mod (data/fate_ubw/advancement). Los que no se pueden detectar con criterios vanilla usan
 * "minecraft:impossible" y se conceden desde aquí: un criterio por servant o por Noble Phantasm.
 */
public final class Achievements {
    /** Líneas de voz que cuentan como Noble Phantasm (el nombre del criterio es el de la línea). */
    private static final Set<String> NOBLE_PHANTASMS = Set.of("excalibur", "avalon", "enuma_elish", "gate_of_babylon", "gae_bolg",
            "caladbolg", "unlimited_blade_works", "rho_aias", "rule_breaker", "bellerophon", "tsubame_gaeshi", "nine_lives",
            "an_gal_ta_kigal_she");

    private Achievements() {
    }

    public static void grant(ServerPlayerEntity player, String advancement, String criterion) {
        AdvancementEntry entry = player.server.getAdvancementLoader().get(FateUBW.id(advancement));
        if (entry != null) player.getAdvancementTracker().grantCriterion(entry, criterion);
    }

    /** Al invocar un servant con el círculo. */
    public static void summoned(ServerPlayerEntity player, String servant) {
        grant(player, "summon", servant);
        grant(player, "all_servants", servant);
    }

    /** Lo llama Voices al decir una línea: si es de un Noble Phantasm y la dice un jugador, cuenta. */
    public static void spoke(Entity speaker, String line) {
        if (!(speaker instanceof ServerPlayerEntity player) || !NOBLE_PHANTASMS.contains(line)) return;
        grant(player, "noble_phantasm", line);
        grant(player, "all_noble_phantasms", line);
        if (line.equals("unlimited_blade_works")) grant(player, "unlimited_blade_works", "done");
    }
}
