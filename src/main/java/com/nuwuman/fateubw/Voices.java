package com.nuwuman.fateubw;

import net.minecraft.entity.Entity;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvent;
import net.minecraft.world.World;

import java.util.HashMap;
import java.util.Map;

/**
 * Líneas de voz de los Noble Phantasm (sounds/voice/*.ogg, generadas con las voces de Windows).
 * Se apagan con /gamerule fateVoiceLines false.
 */
public final class Voices {
    private static final String[] LINES = {"an_gal_ta_kigal_she", "avalon", "bellerophon", "caladbolg", "enuma_elish", "excalibur", "gae_bolg",
            "gate_of_babylon", "nine_lives", "rho_aias", "rule_breaker", "trace_on", "tsubame_gaeshi", "ubw_chant",
            "unlimited_blade_works", "lord_camelot"};
    private static final Map<String, SoundEvent> SOUNDS = new HashMap<>();

    private Voices() {
    }

    public static void register() {
        for (String line : LINES) {
            SOUNDS.put(line, Registry.register(Registries.SOUND_EVENT, FateUBW.id("voice." + line),
                    SoundEvent.of(FateUBW.id("voice." + line))));
        }
    }

    /** Dice la línea desde la posición de quien usa la habilidad. */
    public static void say(World world, Entity speaker, String line) {
        Achievements.spoke(speaker, line);
        SoundEvent sound = SOUNDS.get(line);
        if (sound == null || !(world instanceof ServerWorld server) || !server.getGameRules().getBoolean(FateUBW.VOICE_LINES)) return;
        world.playSound(null, speaker.getX(), speaker.getEyeY(), speaker.getZ(), sound, SoundCategory.VOICE, 1.0F, 1.0F);
    }
}
