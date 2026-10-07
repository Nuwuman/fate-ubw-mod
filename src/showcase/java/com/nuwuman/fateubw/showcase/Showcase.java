package com.nuwuman.fateubw.showcase;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.TitleScreen;
import net.minecraft.client.gui.screen.ingame.InventoryScreen;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.option.Perspective;
import net.minecraft.client.util.ScreenshotRecorder;
import net.minecraft.resource.DataConfiguration;
import net.minecraft.util.Hand;
import net.minecraft.world.Difficulty;
import net.minecraft.world.GameMode;
import net.minecraft.world.GameRules;
import net.minecraft.world.gen.GeneratorOptions;
import net.minecraft.world.gen.WorldPresets;
import net.minecraft.world.level.LevelInfo;

/**
 * Escena de prueba sin intervención: crea un mundo creativo, viste a Archer, usa cada arma y habilidad,
 * guarda capturas en run-showcase/screenshots y cierra el juego.
 */
public class Showcase implements ClientModInitializer {
    private int ticks;
    private int worldTicks;
    private boolean started;
    private boolean holdUse;
    private boolean holdSneak;
    private float yaw;

    @Override
    public void onInitializeClient() {
        ClientTickEvents.END_CLIENT_TICK.register(this::tick);
    }

    private void tick(MinecraftClient client) {
        ticks++;
        client.options.pauseOnLostFocus = false;
        if (client.world == null || client.player == null) {
            if (!started && ticks > 40 && client.getOverlay() == null) {
                started = true;
                client.options.onboardAccessibility = false;
                log("creando mundo");
                LevelInfo info = new LevelInfo("showcase", GameMode.CREATIVE, false, Difficulty.EASY, true,
                        new GameRules(), DataConfiguration.SAFE_MODE);
                client.createIntegratedServerLoader().createAndStart("showcase-" + System.currentTimeMillis(), info,
                        GeneratorOptions.DEMO_OPTIONS, WorldPresets::createDemoOptions, new TitleScreen());
            }
            return;
        }
        worldTicks++;
        client.options.useKey.setPressed(holdUse);
        client.options.sneakKey.setPressed(holdSneak);
        ClientPlayerEntity p = client.player;
        p.setYaw(yaw);
        p.setPitch(0.0F);
        script(client, p, worldTicks);
    }

    private void script(MinecraftClient c, ClientPlayerEntity p, int t) {
        switch (t) {
            case 5 -> {
                for (String cmd : new String[]{
                        "gamerule sendCommandFeedback false", "gamerule doDaylightCycle false", "gamerule doWeatherCycle false",
                        "gamerule doMobSpawning false", "gamerule fateAbilitiesBreakBlocks true", "time set noon", "weather clear",
                        "fill ~-6 ~-1 ~-3 ~6 ~-1 ~18 grass_block", "fill ~-6 ~ ~-3 ~6 ~6 ~18 air",
                        "item replace entity @s armor.chest with fate_ubw:archer_chestplate",
                        "item replace entity @s armor.legs with fate_ubw:archer_leggings",
                        "item replace entity @s armor.feet with fate_ubw:archer_boots",
                        "item replace entity @s hotbar.0 with fate_ubw:kanshou",
                        "item replace entity @s weapon.offhand with fate_ubw:bakuya",
                        "item replace entity @s hotbar.1 with fate_ubw:bakuya",
                        "item replace entity @s hotbar.2 with fate_ubw:archer_bow",
                        "item replace entity @s hotbar.3 with fate_ubw:excalibur",
                        "item replace entity @s hotbar.4 with fate_ubw:archer_chestplate",
                        "item replace entity @s hotbar.5 with fate_ubw:archer_leggings",
                        "item replace entity @s hotbar.6 with fate_ubw:archer_boots",
                        "item replace entity @s hotbar.7 with fate_ubw:caladbolg",
                        "item replace entity @s hotbar.8 with fate_ubw:sword_arrow",
                        "summon husk ~-2 ~ ~8 {NoAI:1b,PersistenceRequired:1b}",
                        "summon husk ~2 ~ ~11 {NoAI:1b,PersistenceRequired:1b}",
                        "summon husk ~ ~ ~15 {NoAI:1b,PersistenceRequired:1b}"}) {
                    p.networkHandler.sendChatCommand(cmd);
                }
                p.getInventory().selectedSlot = 0;
                c.options.setPerspective(Perspective.FIRST_PERSON);
            }
            case 80 -> shot(c, "01_firstperson_kanshou_bakuya");
            case 82 -> c.options.setPerspective(Perspective.THIRD_PERSON_FRONT);
            case 95 -> shot(c, "02_armor_front");
            case 97 -> c.options.setPerspective(Perspective.THIRD_PERSON_BACK);
            case 110 -> shot(c, "03_armor_back");

            // Arco: tensado normal y luego Caladbolg
            case 120 -> {
                p.getInventory().selectedSlot = 2;
                c.options.setPerspective(Perspective.THIRD_PERSON_FRONT);
            }
            case 125 -> use(c, p);
            case 150 -> shot(c, "04_bow_pulled_front");
            case 152 -> c.options.setPerspective(Perspective.FIRST_PERSON);
            case 155 -> shot(c, "05_bow_pulled_firstperson");
            case 157 -> holdUse = false;
            case 170 -> holdSneak = true;
            case 175 -> use(c, p);
            case 200 -> shot(c, "06_caladbolg_nocked_firstperson");
            case 202 -> c.options.setPerspective(Perspective.THIRD_PERSON_FRONT);
            case 205 -> shot(c, "07_caladbolg_nocked_front");
            case 207 -> c.options.setPerspective(Perspective.THIRD_PERSON_BACK);
            case 210 -> holdUse = false;
            case 213 -> shot(c, "08_caladbolg_flight");
            case 218 -> shot(c, "09_caladbolg_impact");
            case 222 -> holdSneak = false;

            // Kanshō y Bakuya lanzados a la vez
            case 240 -> p.getInventory().selectedSlot = 0;
            case 245 -> c.interactionManager.interactItem(p, Hand.MAIN_HAND);
            case 250 -> shot(c, "10_falchions_out");
            case 256 -> shot(c, "11_falchions_cross");
            case 266 -> shot(c, "12_falchions_return");

            // Rho Aias
            case 290 -> holdSneak = true;
            case 292 -> c.interactionManager.interactItem(p, Hand.MAIN_HAND);
            case 295 -> holdSneak = false;
            case 305 -> shot(c, "13_rho_aias_back");
            case 307 -> c.options.setPerspective(Perspective.THIRD_PERSON_FRONT);
            case 315 -> shot(c, "14_rho_aias_front");

            // Excalibur: carga, carga completa y haz
            case 420 -> p.getInventory().selectedSlot = 3;
            case 425 -> use(c, p);
            case 455 -> shot(c, "15_excalibur_charging_front");
            case 490 -> shot(c, "16_excalibur_charged_front");
            case 492 -> c.options.setPerspective(Perspective.FIRST_PERSON);
            case 495 -> shot(c, "17_excalibur_charged_firstperson");
            case 497 -> c.options.setPerspective(Perspective.THIRD_PERSON_BACK);
            case 500 -> holdUse = false;
            case 508 -> shot(c, "18_excalibur_beam_back");
            case 509 -> yaw = 35.0F; // el haz queda fijo; girar la cámara para verlo en diagonal
            case 513 -> shot(c, "19_excalibur_beam_diagonal");
            case 514 -> c.options.setPerspective(Perspective.FIRST_PERSON);
            case 517 -> shot(c, "19b_excalibur_beam_firstperson");
            case 518 -> c.options.setPerspective(Perspective.THIRD_PERSON_BACK);
            case 527 -> shot(c, "19c_excalibur_trench");

            case 530 -> {
                yaw = 0.0F;
                c.options.setPerspective(Perspective.FIRST_PERSON);
                c.setScreen(new InventoryScreen(p));
            }
            case 540 -> shot(c, "20_inventory");
            case 545 -> {
                c.setScreen(null);
                log("fin");
                c.scheduleStop();
            }
            default -> {
            }
        }
    }

    private void use(MinecraftClient c, ClientPlayerEntity p) {
        holdUse = true;
        c.options.useKey.setPressed(true);
        c.interactionManager.interactItem(p, Hand.MAIN_HAND);
    }

    private static void shot(MinecraftClient c, String name) {
        ScreenshotRecorder.saveScreenshot(c.runDirectory, name + ".png", c.getFramebuffer(), msg -> log("captura " + name));
    }

    private static void log(String msg) {
        System.out.println("[showcase] " + msg);
    }
}
