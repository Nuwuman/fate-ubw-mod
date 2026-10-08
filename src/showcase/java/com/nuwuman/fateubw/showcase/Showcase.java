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

import java.util.List;

/**
 * Escena de prueba sin intervención: crea un mundo creativo y, servant por servant, se viste, usa cada arma
 * y habilidad, guarda capturas en run-showcase/screenshots y cierra el juego.
 * FATE_SHOWCASE=saber|archer|lancer prueba solo ese servant.
 */
public class Showcase implements ClientModInitializer {
    private final List<String> sections = System.getenv("FATE_SHOWCASE") == null
            ? List.of("saber", "archer", "lancer") : List.of(System.getenv("FATE_SHOWCASE").split(","));
    private int ticks;
    private int worldTicks;
    private boolean started;
    private boolean holdUse;
    private boolean holdSneak;
    private float yaw;
    private int section;
    private int st = -1; // tick dentro de la sección actual

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

        if (worldTicks == 5) {
            for (String cmd : new String[]{
                    "gamerule sendCommandFeedback false", "gamerule doDaylightCycle false", "gamerule doWeatherCycle false",
                    "gamerule doMobSpawning false", "gamerule fateAbilitiesBreakBlocks true", "time set noon", "weather clear"}) {
                p.networkHandler.sendChatCommand(cmd);
            }
        }
        if (worldTicks < 20) return;

        st++;
        // Al final, una sola captura del inventario: en creativo abre el inventario creativo y, al cerrarlo,
        // el cliente deja de recibir los cambios de armadura que hacen los comandos de la sección siguiente
        if (section >= sections.size()) {
            switch (st) {
                case 0 -> {
                    client.options.setPerspective(Perspective.FIRST_PERSON);
                    client.setScreen(new InventoryScreen(p));
                }
                case 10 -> shot(client, "zz_inventory");
                case 15 -> {
                    client.setScreen(null);
                    log("fin");
                    client.scheduleStop();
                }
                default -> {
                }
            }
            return;
        }
        String name = sections.get(section);
        boolean done = switch (name) {
            case "saber" -> saber(client, p, st);
            case "archer" -> archer(client, p, st);
            case "lancer" -> lancer(client, p, st);
            default -> true;
        };
        if (done) {
            section++;
            st = -20; // 1 s de margen: al cerrar el inventario, su sincronización puede pisar el equipo nuevo
        }
    }

    // Prepara la arena delante del jugador, quita los husks viejos, viste al servant y pone objetivos
    private void setup(MinecraftClient c, ClientPlayerEntity p, String[] items, String[] husks) {
        yaw = 0.0F;
        holdUse = false;
        holdSneak = false;
        p.networkHandler.sendChatCommand("fill ~-6 ~-1 ~-3 ~6 ~-1 ~24 grass_block");
        p.networkHandler.sendChatCommand("fill ~-6 ~ ~-3 ~6 ~6 ~24 air");
        p.networkHandler.sendChatCommand("kill @e[type=husk]");
        p.networkHandler.sendChatCommand("kill @e[type=item]");
        p.networkHandler.sendChatCommand("clear @s");
        for (String item : items) p.networkHandler.sendChatCommand("item replace entity @s " + item);
        for (String pos : husks) p.networkHandler.sendChatCommand("summon husk " + pos + " {NoAI:1b,PersistenceRequired:1b}");
        p.getInventory().selectedSlot = 0;
        c.options.setPerspective(Perspective.FIRST_PERSON);
    }

    /** Antes de soltar una carga, esperar a que el servidor (que a veces va retrasado) la haya contado entera. */
    private boolean waitCharge(MinecraftClient c, ClientPlayerEntity p, int ticks) {
        if (c.getServer() == null) return false;
        var serverPlayer = c.getServer().getPlayerManager().getPlayer(p.getUuid());
        if (serverPlayer == null || serverPlayer.getItemUseTime() >= ticks) return false;
        st--;
        return true;
    }

    private boolean saber(MinecraftClient c, ClientPlayerEntity p, int t) {
        switch (t) {
            case 0 -> setup(c, p, new String[]{
                    "armor.chest with fate_ubw:saber_chestplate", "armor.legs with fate_ubw:saber_leggings",
                    "armor.feet with fate_ubw:saber_boots", "hotbar.0 with fate_ubw:excalibur",
                    "hotbar.1 with fate_ubw:saber_chestplate", "hotbar.2 with fate_ubw:saber_leggings",
                    "hotbar.3 with fate_ubw:saber_boots"}, new String[]{"~-2 ~ ~8", "~2 ~ ~12", "~ ~ ~16"});
            case 40 -> shot(c, "saber_01_firstperson");
            case 42 -> c.options.setPerspective(Perspective.THIRD_PERSON_FRONT);
            case 55 -> shot(c, "saber_02_front");
            case 57 -> c.options.setPerspective(Perspective.THIRD_PERSON_BACK);
            case 70 -> shot(c, "saber_03_back");
            case 72 -> yaw = 90.0F;
            case 85 -> shot(c, "saber_04_side");
            case 87 -> {
                yaw = 0.0F;
                c.options.setPerspective(Perspective.THIRD_PERSON_FRONT);
            }
            case 90 -> use(c, p);
            case 120 -> shot(c, "saber_05_excalibur_charging_front");
            case 160 -> {
                if (waitCharge(c, p, 62)) return false;
                shot(c, "saber_06_excalibur_charged_front");
            }
            case 162 -> c.options.setPerspective(Perspective.FIRST_PERSON);
            case 165 -> shot(c, "saber_07_excalibur_charged_firstperson");
            case 167 -> c.options.setPerspective(Perspective.THIRD_PERSON_BACK);
            case 170 -> holdUse = false;
            case 176 -> yaw = 35.0F; // el haz queda fijo; girar la cámara para verlo en diagonal
            case 180 -> shot(c, "saber_08_excalibur_beam");
            case 190 -> shot(c, "saber_09_excalibur_beam_late");
            case 230 -> {
                yaw = 0.0F;
                holdSneak = true;
            }
            case 232 -> c.interactionManager.interactItem(p, Hand.MAIN_HAND);
            case 234 -> holdSneak = false;
            case 236 -> shot(c, "saber_10_strike_air");
            case 250 -> {
                return true;
            }
            default -> {
            }
        }
        return false;
    }

    private boolean archer(MinecraftClient c, ClientPlayerEntity p, int t) {
        switch (t) {
            case 0 -> setup(c, p, new String[]{
                    "armor.chest with fate_ubw:archer_chestplate", "armor.legs with fate_ubw:archer_leggings",
                    "armor.feet with fate_ubw:archer_boots", "hotbar.0 with fate_ubw:kanshou",
                    "weapon.offhand with fate_ubw:bakuya", "hotbar.1 with fate_ubw:bakuya", "hotbar.2 with fate_ubw:archer_bow",
                    "hotbar.3 with fate_ubw:archer_chestplate", "hotbar.4 with fate_ubw:archer_leggings",
                    "hotbar.5 with fate_ubw:archer_boots", "hotbar.6 with fate_ubw:caladbolg", "hotbar.7 with fate_ubw:sword_arrow"},
                    new String[]{"~-2 ~ ~8", "~2 ~ ~11", "~ ~ ~15"});
            case 40 -> shot(c, "archer_01_firstperson");
            case 42 -> c.options.setPerspective(Perspective.THIRD_PERSON_FRONT);
            case 55 -> shot(c, "archer_02_front");
            case 57 -> c.options.setPerspective(Perspective.THIRD_PERSON_BACK);
            case 70 -> shot(c, "archer_03_back");

            // Arco: tensado normal y luego Caladbolg
            case 80 -> {
                p.getInventory().selectedSlot = 2;
                c.options.setPerspective(Perspective.THIRD_PERSON_FRONT);
            }
            case 85 -> use(c, p);
            case 110 -> shot(c, "archer_04_bow_pulled_front");
            case 112 -> c.options.setPerspective(Perspective.FIRST_PERSON);
            case 115 -> shot(c, "archer_05_bow_pulled_firstperson");
            case 117 -> holdUse = false;
            case 130 -> holdSneak = true;
            case 135 -> use(c, p);
            case 160 -> shot(c, "archer_06_caladbolg_nocked_firstperson");
            case 162 -> c.options.setPerspective(Perspective.THIRD_PERSON_BACK);
            case 165 -> {
                if (waitCharge(c, p, 22)) return false;
                holdUse = false;
            }
            case 167 -> yaw = 35.0F;
            case 172 -> shot(c, "archer_07_caladbolg_flight");
            case 180 -> shot(c, "archer_08_caladbolg_impact");
            case 182 -> {
                holdSneak = false;
                yaw = 0.0F;
            }

            // Kanshō y Bakuya lanzados a la vez
            case 200 -> p.getInventory().selectedSlot = 0;
            case 205 -> c.interactionManager.interactItem(p, Hand.MAIN_HAND);
            case 210 -> shot(c, "archer_09_falchions_out");
            case 216 -> shot(c, "archer_10_falchions_cross");

            // Rho Aias
            case 250 -> holdSneak = true;
            case 252 -> c.interactionManager.interactItem(p, Hand.MAIN_HAND);
            case 255 -> holdSneak = false;
            case 265 -> shot(c, "archer_11_rho_aias_back");
            case 267 -> c.options.setPerspective(Perspective.THIRD_PERSON_FRONT);
            case 275 -> shot(c, "archer_12_rho_aias_front");
            case 290 -> {
                return true;
            }
            default -> {
            }
        }
        return false;
    }

    private boolean lancer(MinecraftClient c, ClientPlayerEntity p, int t) {
        switch (t) {
            case 0 -> setup(c, p, new String[]{
                    "armor.chest with fate_ubw:lancer_chestplate", "armor.legs with fate_ubw:lancer_leggings",
                    "armor.feet with fate_ubw:lancer_boots", "hotbar.0 with fate_ubw:gae_bolg",
                    "hotbar.1 with fate_ubw:lancer_chestplate", "hotbar.2 with fate_ubw:lancer_leggings",
                    "hotbar.3 with fate_ubw:lancer_boots"}, new String[]{"~1 ~ ~6", "~ ~ ~20", "~2 ~ ~21"});
            case 40 -> shot(c, "lancer_01_firstperson");
            case 42 -> c.options.setPerspective(Perspective.THIRD_PERSON_FRONT);
            case 55 -> shot(c, "lancer_02_front");
            case 57 -> c.options.setPerspective(Perspective.THIRD_PERSON_BACK);
            case 70 -> shot(c, "lancer_03_back");
            case 72 -> yaw = 90.0F;
            case 85 -> shot(c, "lancer_04_side");
            case 87 -> yaw = 0.0F;

            // Estocada: la lanza que atraviesa con la muerte
            case 95 -> use(c, p);
            case 110 -> shot(c, "lancer_05_charge_back");
            case 112 -> c.options.setPerspective(Perspective.FIRST_PERSON);
            case 115 -> shot(c, "lancer_06_charge_firstperson");
            case 117 -> c.options.setPerspective(Perspective.THIRD_PERSON_BACK);
            case 119 -> {
                if (waitCharge(c, p, 22)) return false;
                holdUse = false;
            }
            case 123 -> shot(c, "lancer_07_pierce");
            case 128 -> shot(c, "lancer_08_pierce_late");

            // Lanzamiento: la lanza que vuela con la muerte
            case 160 -> holdSneak = true;
            case 162 -> use(c, p);
            case 210 -> shot(c, "lancer_09_soaring_charge");
            case 212 -> {
                if (waitCharge(c, p, 42)) return false;
                holdUse = false;
            }
            case 214 -> yaw = 35.0F; // la lanza queda a la izquierda de la cámara, no tapada por el jugador
            case 216 -> holdSneak = false;
            case 220 -> shot(c, "lancer_10_soaring_flight");
            case 226 -> shot(c, "lancer_11_soaring_flight_late");
            case 250 -> shot(c, "lancer_12_soaring_impact");
            case 270 -> {
                yaw = 0.0F;
                return true;
            }
            default -> {
            }
        }
        return false;
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
