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
            ? List.of("saber", "archer", "lancer", "rider", "gilgamesh", "ubw", "trace", "hud", "caster", "assassin", "berserker") : List.of(System.getenv("FATE_SHOWCASE").split(","));
    private int ticks;
    private int worldTicks;
    private boolean started;
    private boolean holdUse;
    private net.minecraft.util.math.BlockPos chestPos = net.minecraft.util.math.BlockPos.ORIGIN;
    private boolean holdSneak;
    private boolean holdForward;
    private float yaw;
    private float pitch;
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
            // Al reabrir un mundo de prueba pregunta si hacer copia de seguridad: se carga sin ella
            if (client.currentScreen instanceof net.minecraft.client.gui.screen.world.BackupPromptScreen s) {
                String skip = net.minecraft.text.Text.translatable("selectWorld.backupJoinSkipButton").getString();
                for (var child : s.children()) {
                    if (child instanceof net.minecraft.client.gui.widget.ButtonWidget b && b.getMessage().getString().equals(skip)) b.onPress();
                }
            }
            if (!started && ticks > 40 && client.getOverlay() == null) {
                started = true;
                client.options.onboardAccessibility = false;
                log("creando mundo");
                // FATE_WORLD=<carpeta> reabre un mundo de una ejecución anterior (para probar lo que se guarda)
                String reopen = System.getenv("FATE_WORLD");
                if (reopen != null) {
                    client.createIntegratedServerLoader().start(reopen, () -> {
                    });
                    return;
                }
                LevelInfo info = new LevelInfo("showcase", GameMode.CREATIVE, false, Difficulty.EASY, true,
                        new GameRules(), DataConfiguration.SAFE_MODE);
                client.createIntegratedServerLoader().createAndStart("showcase-" + System.currentTimeMillis(), info,
                        GeneratorOptions.DEMO_OPTIONS, WorldPresets::createDemoOptions, new TitleScreen());
            }
            return;
        }
        worldTicks++;
        if (worldTicks == 1) {
            // Para comprobar que las líneas de voz llegan al cliente y se reproducen
            client.getSoundManager().registerListener((sound, soundSet, range) -> {
                if (sound.getId().getPath().startsWith("voice.")) log("voz: " + sound.getId().getPath());
            });
        }
        client.options.useKey.setPressed(holdUse);
        client.options.sneakKey.setPressed(holdSneak);
        client.options.forwardKey.setPressed(holdForward);
        ClientPlayerEntity p = client.player;
        p.setYaw(yaw);
        p.setPitch(pitch);

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
            case "rider" -> rider(client, p, st);
            case "gilgamesh" -> gilgamesh(client, p, st);
            case "ubw" -> ubw(client, p, st);
            case "trace" -> trace(client, p, st);
            case "hud" -> hud(client, p, st);
            case "mana" -> mana(client, p, st);
            case "grail" -> grail(client, p, st);
            case "grailwar" -> grailWar(p, st);
            case "masters" -> masters(client, p, st);
            case "npc" -> npc(client, p, st);
            case "newnps" -> newNps(client, p, st);
            case "glow" -> glow(client, p, st);
            case "armors" -> armors(client, p, st);
            case "caster" -> caster(client, p, st);
            case "ishtar" -> ishtar(client, p, st);
            case "falchions" -> falchions(client, p, st);
            case "enchants" -> enchants(client, p, st);
            case "assassin" -> assassin(client, p, st);
            case "berserker" -> berserker(client, p, st);
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
        // Con límite: si el arma está en recarga, el servidor nunca empieza a cargar
        if (serverPlayer == null || serverPlayer.getItemUseTime() >= ticks || waited >= 200) {
            if (waited >= 200) log("la carga no llegó: " + serverPlayer.getActiveItem());
            waited = 0;
            return false;
        }
        waited++;
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
            case 167 -> {
                c.options.setPerspective(Perspective.THIRD_PERSON_BACK);
                yaw = 90.0F;
            }
            case 168 -> shot(c, "saber_06b_excalibur_raised_side");
            case 170 -> holdUse = false;
            case 171, 172, 173, 174, 175 -> shot(c, "saber_07b_excalibur_swing_t" + t);
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
            case 202 -> c.options.setPerspective(Perspective.THIRD_PERSON_FRONT);
            case 205 -> c.interactionManager.interactItem(p, Hand.MAIN_HAND);
            case 206, 207, 208, 209 -> shot(c, "archer_09a_throw_t" + t);
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

    private int waited;

    /** Retiene el tick de la sección hasta que se cumpla la condición (como mucho maxTicks), para no depender del retraso del servidor. */
    private boolean waitUntil(boolean condition, int maxTicks) {
        if (condition || waited >= maxTicks) {
            waited = 0;
            return false;
        }
        waited++;
        st--;
        return true;
    }

    private static boolean clientHas(MinecraftClient c, net.minecraft.entity.EntityType<?> type) {
        for (var e : c.world.getEntities()) {
            if (e.getType() == type) return true;
        }
        return false;
    }

    private boolean rider(MinecraftClient c, ClientPlayerEntity p, int t) {
        switch (t) {
            case 0 -> setup(c, p, new String[]{
                    "armor.head with fate_ubw:rider_helmet", "armor.chest with fate_ubw:rider_chestplate",
                    "armor.legs with fate_ubw:rider_leggings", "armor.feet with fate_ubw:rider_boots",
                    "hotbar.0 with fate_ubw:rider_dagger",
                    "hotbar.2 with fate_ubw:rider_helmet", "hotbar.3 with fate_ubw:rider_chestplate",
                    "hotbar.4 with fate_ubw:rider_leggings", "hotbar.5 with fate_ubw:rider_boots"},
                    new String[]{"~ ~ ~8", "~-3 ~ ~12", "~2 ~ ~14"});
            case 40 -> shot(c, "rider_01_firstperson");
            case 42 -> c.options.setPerspective(Perspective.THIRD_PERSON_FRONT);
            case 55 -> shot(c, "rider_02_front");
            case 57 -> c.options.setPerspective(Perspective.THIRD_PERSON_BACK);
            case 70 -> shot(c, "rider_03_back");
            case 72 -> yaw = 90.0F;
            case 85 -> shot(c, "rider_04_side");
            case 87 -> yaw = 0.0F;

            // Daga con cadena: contra el husk de enfrente y luego como gancho contra el suelo
            case 92 -> {
                yaw = 0.0F;
                c.options.setPerspective(Perspective.FIRST_PERSON); // en tercera persona el cuerpo tapa la cadena
            }
            case 95 -> c.interactionManager.interactItem(p, Hand.MAIN_HAND);
            case 96 -> yaw = 15.0F;
            case 98 -> {
                if (waitUntil(clientHas(c, com.nuwuman.fateubw.FateUBW.CHAIN_DAGGER), 40)) return false;
            }
            case 100 -> shot(c, "rider_05_chain_hook");
            case 104 -> shot(c, "rider_06_chain_pull");
            case 108 -> {
                yaw = 0.0F;
                pitch = 35.0F;
            }
            case 110 -> c.interactionManager.interactItem(p, Hand.MAIN_HAND);
            case 112 -> {
                if (waitUntil(clientHas(c, com.nuwuman.fateubw.FateUBW.CHAIN_DAGGER), 40)) return false;
            }
            case 114 -> shot(c, "rider_06b_chain_grapple");
            case 118 -> {
                pitch = 0.0F;
                c.options.setPerspective(Perspective.THIRD_PERSON_BACK);
            }

            // Ojos Místicos
            case 122 -> ability(0);
            case 127 -> shot(c, "rider_07_mystic_eyes");
            case 129 -> c.options.setPerspective(Perspective.THIRD_PERSON_FRONT);
            case 133 -> shot(c, "rider_08_mystic_eyes_front");

            // Bellerophon: invocar, montar, despegar, volar y embestir
            case 140 -> c.options.setPerspective(Perspective.THIRD_PERSON_BACK);
            case 145 -> ability(1);
            case 165 -> shot(c, "rider_09_pegasus_mounted");
            case 167 -> c.options.setPerspective(Perspective.THIRD_PERSON_FRONT);
            case 180 -> shot(c, "rider_10_pegasus_front");
            case 182 -> {
                c.options.setPerspective(Perspective.THIRD_PERSON_BACK);
                yaw = 90.0F;
            }
            case 192 -> shot(c, "rider_11_pegasus_side");
            case 194 -> {
                yaw = 0.0F;
                pitch = -35.0F;
                holdForward = true;
            }
            case 225 -> shot(c, "rider_12_takeoff");
            case 227 -> pitch = 0.0F;
            case 235 -> yaw = 60.0F;
            case 240 -> shot(c, "rider_13_flying_side");
            case 242 -> {
                yaw = 0.0F;
                holdForward = false;
            }
            case 250 -> ability(1);
            case 252 -> {
                if (waitUntil(p.getVehicle() instanceof com.nuwuman.fateubw.rider.PegasusEntity peg && peg.getCharge() > 0, 40)) return false;
            }
            case 254 -> shot(c, "rider_14_bellerophon_charge");
            case 256 -> yaw = 50.0F;
            case 260 -> shot(c, "rider_15_bellerophon_side");
            case 285 -> {
                yaw = 0.0F;
                holdSneak = true;
            }
            case 290 -> holdSneak = false;
            case 300 -> {
                return true;
            }
            default -> {
            }
        }
        return false;
    }

    private boolean gilgamesh(MinecraftClient c, ClientPlayerEntity p, int t) {
        switch (t) {
            case 0 -> setup(c, p, new String[]{
                    "armor.chest with fate_ubw:gilgamesh_chestplate", "armor.legs with fate_ubw:gilgamesh_leggings",
                    "armor.feet with fate_ubw:gilgamesh_boots",
                    "hotbar.1 with fate_ubw:ea", "hotbar.2 with fate_ubw:gilgamesh_chestplate",
                    "hotbar.3 with fate_ubw:gilgamesh_leggings", "hotbar.4 with fate_ubw:gilgamesh_boots"},
                    new String[]{"~-2 ~ ~10", "~2 ~ ~12", "~ ~ ~16"});
            case 40 -> shot(c, "gilgamesh_01_firstperson_key");
            case 42 -> c.options.setPerspective(Perspective.THIRD_PERSON_FRONT);
            case 55 -> shot(c, "gilgamesh_02_front");
            case 57 -> c.options.setPerspective(Perspective.THIRD_PERSON_BACK);
            case 70 -> shot(c, "gilgamesh_03_back");

            // Gate of Babylon
            case 80 -> ability(0);
            case 82 -> {
                if (waitUntil(clientHas(c, com.nuwuman.fateubw.FateUBW.BABYLON_PORTAL), 40)) return false;
            }
            case 92 -> shot(c, "gilgamesh_04_gate_back");
            case 94 -> c.options.setPerspective(Perspective.THIRD_PERSON_FRONT);
            case 102 -> shot(c, "gilgamesh_05_gate_front");
            case 104 -> {
                c.options.setPerspective(Perspective.THIRD_PERSON_BACK);
                yaw = 30.0F;
            }
            case 112 -> shot(c, "gilgamesh_06_gate_barrage");
            case 130 -> yaw = 0.0F;
            // Enkidu contra el husk del centro
            case 136 -> ability(1);
            case 141 -> shot(c, "gilgamesh_06b_enkidu");

            // Ea: carga y Enuma Elish
            case 150 -> {
                p.getInventory().selectedSlot = 1;
                c.options.setPerspective(Perspective.FIRST_PERSON);
            }
            case 155 -> use(c, p);
            case 175 -> shot(c, "gilgamesh_07_ea_charging_firstperson");
            case 177 -> c.options.setPerspective(Perspective.THIRD_PERSON_FRONT);
            case 220 -> {
                if (waitCharge(c, p, 62)) return false;
                shot(c, "gilgamesh_08_ea_charged_front");
            }
            case 222 -> c.options.setPerspective(Perspective.THIRD_PERSON_BACK);
            case 225 -> holdUse = false;
            case 227 -> {
                if (waitUntil(clientHas(c, com.nuwuman.fateubw.FateUBW.ENUMA_ELISH), 40)) return false;
            }
            case 228 -> yaw = 35.0F;
            case 233 -> shot(c, "gilgamesh_09_enuma_elish");
            case 240 -> shot(c, "gilgamesh_10_enuma_elish_late");
            case 260 -> {
                yaw = 0.0F;
                return true;
            }
            default -> {
            }
        }
        return false;
    }

    private boolean ubw(MinecraftClient c, ClientPlayerEntity p, int t) {
        boolean inside = clientHas(c, com.nuwuman.fateubw.FateUBW.UBW_CORE);
        switch (t) {
            case 0 -> setup(c, p, new String[]{
                    "armor.chest with fate_ubw:archer_chestplate", "armor.legs with fate_ubw:archer_leggings",
                    "armor.feet with fate_ubw:archer_boots"},
                    new String[]{"~-2 ~ ~6", "~2 ~ ~8", "~ ~ ~10"});
            // Algo que el Marble debe devolver tal cual: una torre y un cofre con diamantes
            case 2 -> {
                p.networkHandler.sendChatCommand("fill ~4 ~ ~5 ~5 ~5 ~6 stone_bricks");
                p.networkHandler.sendChatCommand("setblock ~-4 ~ ~4 chest{Items:[{Slot:0b,id:\"minecraft:diamond\",count:5}]}");
                chestPos = p.getBlockPos().add(-4, 0, 4);
            }
            case 30 -> c.options.setPerspective(Perspective.THIRD_PERSON_BACK);
            // La habilidad del conjunto: el aria dura 3 s y el Marble se despliega solo
            case 40 -> ability(1);
            case 70 -> shot(c, "ubw_01_chant");
            case 107 -> {
                if (waitUntil(inside, 100)) return false;
            }
            case 120 -> shot(c, "ubw_02a_spreading");
            case 140 -> shot(c, "ubw_02_arrival_back");
            case 142 -> c.options.setPerspective(Perspective.THIRD_PERSON_FRONT);
            case 152 -> shot(c, "ubw_03_arrival_front");
            case 154 -> {
                c.options.setPerspective(Perspective.FIRST_PERSON);
                pitch = -20.0F;
                yaw = 120.0F;
            }
            case 164 -> shot(c, "ubw_04_sky_gears");
            case 166 -> {
                yaw = 250.0F;
                pitch = 5.0F;
            }
            case 176 -> shot(c, "ubw_05_sword_field");
            case 178 -> {
                yaw = 0.0F;
                pitch = 0.0F;
                c.options.setPerspective(Perspective.THIRD_PERSON_BACK);
            }
            case 182 -> ability(1);
            case 184 -> yaw = 25.0F;
            case 190 -> shot(c, "ubw_06_barrage");
            case 196 -> shot(c, "ubw_07_barrage_late");
            // Las espadas que llueven solas sobre los demás y el HUD con la ráfaga
            case 210 -> {
                yaw = 0.0F;
                c.options.setPerspective(Perspective.FIRST_PERSON);
            }
            case 214 -> shot(c, "ubw_07a_sword_rain_hud");
            // No se puede romper ni poner bloques dentro
            case 216 -> c.interactionManager.attackBlock(p.getBlockPos().down(), net.minecraft.util.math.Direction.UP);
            case 222 -> {
                var server = c.getServer();
                var below = p.getBlockPos().down();
                log("suelo tras intentar romperlo: " + server.submit(() -> server.getOverworld().getBlockState(below)).join());
            }
            // Deshacer el Marble
            case 228 -> holdSneak = true;
            case 232 -> ability(1);
            case 236 -> holdSneak = false;
            case 238 -> {
                if (waitUntil(!inside, 100)) return false;
            }
            case 247 -> shot(c, "ubw_07b_collapsing");
            case 300 -> {
                shot(c, "ubw_08_returned");
                var server = c.getServer();
                var chest = server.submit(() -> server.getOverworld().getBlockEntity(chestPos)).join();
                log("cofre tras el Marble: " + (chest instanceof net.minecraft.inventory.Inventory inv ? inv.getStack(0) : "no está"));
            }
            case 310 -> {
                return true;
            }
            default -> {
            }
        }
        return false;
    }

    private boolean trace(MinecraftClient c, ClientPlayerEntity p, int t) {
        switch (t) {
            case 0 -> setup(c, p, new String[]{
                    "armor.chest with fate_ubw:archer_chestplate", "armor.legs with fate_ubw:archer_leggings",
                    "armor.feet with fate_ubw:archer_boots"}, new String[]{});
            case 2 -> p.networkHandler.sendChatCommand(
                    "summon husk ~ ~ ~6 {NoAI:1b,PersistenceRequired:1b,HandItems:[{id:\"minecraft:netherite_sword\",count:1}]}");
            // Sin nada analizado: Kanshō y Bakuya
            case 30 -> ability(0);
            case 40 -> c.options.setPerspective(Perspective.THIRD_PERSON_FRONT);
            case 50 -> shot(c, "trace_04_kanshou_bakuya");
            case 52 -> c.options.setPerspective(Perspective.FIRST_PERSON);
            // Analizar la espada del husk
            case 56 -> holdSneak = true;
            case 60 -> ability(0);
            case 64 -> holdSneak = false;
            case 68 -> shot(c, "trace_01_analyze");
            // Proyectarla: va al primer hueco libre de la barra y se selecciona
            case 72 -> ability(0);
            case 84 -> shot(c, "trace_02_projected_firstperson");
            case 86 -> c.options.setPerspective(Perspective.THIRD_PERSON_FRONT);
            case 98 -> shot(c, "trace_03_projected_front");
            // Broken Phantasm: tirar la proyección
            case 102 -> {
                c.options.setPerspective(Perspective.THIRD_PERSON_BACK);
                pitch = -10.0F;
            }
            case 106 -> p.dropSelectedItem(false);
            case 107 -> {
                if (waitUntil(clientHas(c, net.minecraft.entity.EntityType.ITEM), 60)) return false;
                shot(c, "trace_05_thrown");
            }
            case 108 -> {
                if (waitUntil(!clientHas(c, net.minecraft.entity.EntityType.ITEM), 60)) return false;
                shot(c, "trace_06_broken_phantasm");
            }
            case 140 -> {
                pitch = 0.0F;
                return true;
            }
            default -> {
            }
        }
        return false;
    }

    // Las habilidades de Saber y Lancer, y el HUD al cambiar de habilidad con la tecla
    private boolean hud(MinecraftClient c, ClientPlayerEntity p, int t) {
        switch (t) {
            case 0 -> setup(c, p, new String[]{
                    "armor.chest with fate_ubw:saber_chestplate", "armor.legs with fate_ubw:saber_leggings",
                    "armor.feet with fate_ubw:saber_boots", "hotbar.0 with fate_ubw:excalibur"},
                    new String[]{"~-1 ~ ~6", "~1 ~ ~7"});
            case 30 -> shot(c, "hud_01_saber");
            case 32 -> c.options.setPerspective(Perspective.THIRD_PERSON_FRONT);
            case 36 -> ability(0);
            case 44 -> shot(c, "hud_02_avalon");
            case 46 -> {
                c.options.setPerspective(Perspective.THIRD_PERSON_BACK);
                pressKey(org.lwjgl.glfw.GLFW.GLFW_KEY_G);
            }
            case 50 -> ability(1);
            case 53 -> shot(c, "hud_03_mana_burst");
            // Excalibur tiene ahora un cooldown por habilidad: Strike Air no bloquea el haz
            case 60 -> holdSneak = true;
            case 62 -> c.interactionManager.interactItem(p, Hand.MAIN_HAND);
            case 64 -> holdSneak = false;
            case 69 -> log("Strike Air en cooldown: " + p.getItemCooldownManager().isCoolingDown(com.nuwuman.fateubw.FateUBW.STRIKE_AIR)
                    + ", haz de Excalibur bloqueado: " + p.getItemCooldownManager().isCoolingDown(com.nuwuman.fateubw.FateUBW.EXCALIBUR_NP));
            case 70 -> setup(c, p, new String[]{
                    "armor.chest with fate_ubw:lancer_chestplate", "armor.legs with fate_ubw:lancer_leggings",
                    "armor.feet with fate_ubw:lancer_boots"}, new String[]{"~ ~ ~8"});
            case 100 -> {
                c.options.setPerspective(Perspective.THIRD_PERSON_BACK);
                ability(0);
            }
            case 104 -> shot(c, "hud_04_ansuz");
            case 120 -> {
                return true;
            }
            default -> {
            }
        }
        return false;
    }

    private boolean caster(MinecraftClient c, ClientPlayerEntity p, int t) {
        switch (t) {
            case 0 -> setup(c, p, new String[]{
                    "armor.chest with fate_ubw:caster_chestplate", "armor.legs with fate_ubw:caster_leggings",
                    "armor.feet with fate_ubw:caster_boots", "hotbar.0 with fate_ubw:rule_breaker",
                    "hotbar.1 with fate_ubw:caster_chestplate", "hotbar.2 with fate_ubw:caster_leggings", "hotbar.3 with fate_ubw:caster_boots"},
                    new String[]{});
            case 40 -> shot(c, "caster_01_firstperson");
            case 42 -> c.options.setPerspective(Perspective.THIRD_PERSON_FRONT);
            case 55 -> shot(c, "caster_02_front");
            case 57 -> c.options.setPerspective(Perspective.THIRD_PERSON_BACK);
            case 70 -> shot(c, "caster_03_back");
            case 72 -> yaw = 90.0F;
            case 82 -> shot(c, "caster_04_side");
            case 84 -> {
                yaw = 0.0F;
                husks(p, "~ ~ ~3", "~-3 ~ ~10", "~3 ~ ~12");
            }
            case 90 -> ability(0);
            case 93 -> shot(c, "caster_05_divine_words");
            case 100 -> c.interactionManager.interactItem(p, Hand.MAIN_HAND);
            case 103 -> shot(c, "caster_06_rule_breaker");
            case 110 -> {
                pitch = 30.0F;
                pressKey(org.lwjgl.glfw.GLFW.GLFW_KEY_G);
            }
            case 114 -> ability(1);
            case 120 -> {
                pitch = 0.0F;
                shot(c, "caster_07_spatial_transfer");
            }
            case 130 -> {
                return true;
            }
            default -> {
            }
        }
        return false;
    }

    // Encantamientos: cada uno se activa contra un husk delante y se comprueba en el servidor
    private boolean enchants(MinecraftClient c, ClientPlayerEntity p, int t) {
        var server = c.getServer();
        var sp = server == null ? null : server.getPlayerManager().getPlayer(p.getUuid());
        if (sp == null) return t > 400;
        var husk = sp.getServerWorld().getEntitiesByClass(net.minecraft.entity.mob.HuskEntity.class, sp.getBoundingBox().expand(8), e -> true)
                .stream().findFirst().orElse(null);
        String hp = husk == null ? "sin husk" : "husk " + husk.getHealth() + " efectos " + husk.getStatusEffects().stream()
                .map(e -> e.getEffectType().getIdAsString()).toList();
        switch (t) {
            case 0 -> {
                setup(c, p, new String[]{
                        "hotbar.0 with fate_ubw:excalibur[enchantments={levels:{'fate_ubw:radiant_blade':3,'fate_ubw:avalons_grace':1}}]",
                        "hotbar.1 with fate_ubw:kanshou[enchantments={levels:{'fate_ubw:yin_yang_resonance':3}}]",
                        "weapon.offhand with fate_ubw:bakuya",
                        "hotbar.2 with fate_ubw:archer_bow[enchantments={levels:{'fate_ubw:broken_blade':3}}]",
                        "hotbar.3 with fate_ubw:rule_breaker[enchantments={levels:{'fate_ubw:contract_breaker':1}}]",
                        "hotbar.4 with fate_ubw:berserker_axe_sword[enchantments={levels:{'fate_ubw:god_hand':3}}]",
                        "hotbar.5 with fate_ubw:gae_bolg[enchantments={levels:{'fate_ubw:bloodied_spear':1}}]"},
                        new String[]{"~ ~ ~3"});
                p.networkHandler.sendChatCommand("gamemode survival");
            }
            // Radiant Blade y Avalon's Grace: cargar 1,5 s y soltar
            case 30 -> {
                sp.setHealth(10.0F);
                use(c, p);
            }
            case 55 -> log("avalon, regeneración mientras carga: " + sp.hasStatusEffect(net.minecraft.entity.effect.StatusEffects.REGENERATION));
            case 62 -> holdUse = false;
            case 66 -> {
                c.options.setPerspective(Perspective.THIRD_PERSON_FRONT);
                log("radiant blade: " + hp + ", vida jugador " + sp.getHealth());
            }
            case 75 -> {
                shot(c, "ench_01_radiant_blade");
                log("radiant blade tras 0,5 s: " + hp + ", en llamas " + (husk != null && husk.isOnFire()));
            }
            // Yin-Yang Resonance: diez golpes con Kanshō (Bakuya en la otra mano)
            case 90 -> {
                c.options.setPerspective(Perspective.FIRST_PERSON);
                p.networkHandler.sendChatCommand("kill @e[type=husk]");
                p.networkHandler.sendChatCommand("summon husk ~ ~ ~2 {NoAI:1b,PersistenceRequired:1b,Health:200f,attributes:[{id:'minecraft:generic.max_health',base:200d}]}");
                p.getInventory().selectedSlot = 1;
            }
            case 100, 112, 124, 136, 148, 160 -> {
                if (husk != null) c.interactionManager.attackEntity(p, c.world.getEntityById(husk.getId()));
                p.swingHand(Hand.MAIN_HAND);
            }
            case 170 -> log("yin-yang tras 6 golpes (sin encantamiento serían 6x9=54): " + hp);
            // Broken Blade: tres flechas
            case 175 -> {
                p.getInventory().selectedSlot = 2;
                pitch = 5.0F;
            }
            case 180, 205, 230 -> use(c, p);
            case 198, 223, 248 -> holdUse = false;
            case 255 -> log("broken blade: " + hp);
            // Contract Breaker: el husk tiene Velocidad II
            case 260 -> {
                pitch = 0.0F;
                p.getInventory().selectedSlot = 3;
                p.networkHandler.sendChatCommand("effect give @e[type=husk] speed 60 1");
            }
            case 266 -> {
                if (husk != null) c.interactionManager.attackEntity(p, c.world.getEntityById(husk.getId()));
                p.swingHand(Hand.MAIN_HAND);
            }
            case 272 -> log("contract breaker, nada más golpear: " + hp + ", jugador con velocidad " + sp.hasStatusEffect(net.minecraft.entity.effect.StatusEffects.SPEED));
            case 292 -> log("contract breaker, 1 s después: jugador con velocidad " + sp.hasStatusEffect(net.minecraft.entity.effect.StatusEffects.SPEED));
            // God Hand: con poca vida
            case 300 -> {
                p.getInventory().selectedSlot = 4;
                sp.setHealth(20.0F);
                sp.clearStatusEffects();
            }
            case 305 -> sp.damage(sp.getServerWorld().getDamageSources().generic(), 15.0F);
            case 308 -> log("god hand: vida " + sp.getHealth() + ", resistencia " + sp.hasStatusEffect(net.minecraft.entity.effect.StatusEffects.RESISTANCE)
                    + ", absorción " + sp.getAbsorptionAmount());
            // Bloodied Spear: matar con la Gáe Bolg deja el círculo
            case 315 -> {
                p.getInventory().selectedSlot = 5;
                sp.setHealth(20.0F);
                p.networkHandler.sendChatCommand("kill @e[type=husk]");
                p.networkHandler.sendChatCommand("summon husk ~ ~ ~2 {NoAI:1b,PersistenceRequired:1b,Health:1f}");
                p.networkHandler.sendChatCommand("summon husk ~1 ~ ~3 {NoAI:1b,PersistenceRequired:1b}");
            }
            case 325 -> {
                var first = sp.getServerWorld().getEntitiesByClass(net.minecraft.entity.mob.HuskEntity.class, sp.getBoundingBox().expand(3),
                        e -> e.getHealth() <= 1.0F).stream().findFirst().orElse(null);
                if (first != null) c.interactionManager.attackEntity(p, c.world.getEntityById(first.getId()));
                p.swingHand(Hand.MAIN_HAND);
            }
            case 345 -> {
                c.options.setPerspective(Perspective.THIRD_PERSON_FRONT);
                log("bloodied spear, el husk de al lado: " + sp.getServerWorld().getEntitiesByClass(net.minecraft.entity.mob.HuskEntity.class,
                        sp.getBoundingBox().expand(8), e -> e.isAlive()).stream()
                        .map(e -> e.getHealth() + " " + e.getStatusEffects().stream().map(x -> x.getEffectType().getIdAsString()).toList()).toList());
            }
            case 352 -> shot(c, "ench_02_bloodied_spear");
            case 360 -> {
                p.networkHandler.sendChatCommand("gamemode creative");
                return true;
            }
            default -> {
            }
        }
        return false;
    }

    // Kanshō y Bakuya de cerca: en las manos y en la barra
    private boolean falchions(MinecraftClient c, ClientPlayerEntity p, int t) {
        switch (t) {
            case 0 -> setup(c, p, new String[]{"hotbar.0 with fate_ubw:kanshou", "weapon.offhand with fate_ubw:bakuya",
                    "hotbar.1 with fate_ubw:bakuya"}, new String[]{});
            case 30 -> shot(c, "falchions_01_firstperson");
            case 32 -> c.options.setPerspective(Perspective.THIRD_PERSON_FRONT);
            case 45 -> shot(c, "falchions_02_front");
            case 47 -> yaw = 60.0F;
            case 57 -> shot(c, "falchions_03_angle");
            case 60 -> {
                yaw = 0.0F;
                return true;
            }
            default -> {
            }
        }
        return false;
    }

    private boolean ishtar(MinecraftClient c, ClientPlayerEntity p, int t) {
        switch (t) {
            case 0 -> setup(c, p, new String[]{
                    "armor.head with fate_ubw:ishtar_tiara", "armor.chest with fate_ubw:ishtar_chestplate",
                    "armor.legs with fate_ubw:ishtar_leggings", "armor.feet with fate_ubw:ishtar_boots", "hotbar.0 with fate_ubw:maanna",
                    "hotbar.1 with fate_ubw:ishtar_tiara", "hotbar.2 with fate_ubw:ishtar_chestplate",
                    "hotbar.3 with fate_ubw:ishtar_leggings", "hotbar.4 with fate_ubw:ishtar_boots"},
                    new String[]{"~-2 ~ ~10", "~2 ~ ~12", "~ ~ ~16"});
            case 40 -> shot(c, "ishtar_01_firstperson");
            case 42 -> c.options.setPerspective(Perspective.THIRD_PERSON_FRONT);
            case 55 -> shot(c, "ishtar_02_front");
            case 57 -> c.options.setPerspective(Perspective.THIRD_PERSON_BACK);
            case 70 -> shot(c, "ishtar_03_back");
            case 72 -> yaw = 90.0F;
            case 82 -> shot(c, "ishtar_04_side");
            case 84 -> yaw = 0.0F;
            case 90 -> ability(0);
            case 96 -> shot(c, "ishtar_05_jewel_burst");
            case 110 -> ability(1);
            case 113 -> shot(c, "ishtar_06_beauty");
            case 125 -> use(c, p);
            case 150 -> {
                if (waitCharge(c, p, 22)) return false;
                holdUse = false;
            }
            case 154 -> shot(c, "ishtar_07_jewel_shot");
            case 170 -> holdSneak = true;
            case 172 -> use(c, p);
            case 200 -> shot(c, "ishtar_08_np_charging");
            case 240 -> {
                if (waitCharge(c, p, 62)) return false;
                holdUse = false;
            }
            case 243 -> yaw = 35.0F;
            case 246 -> holdSneak = false;
            case 250 -> shot(c, "ishtar_09_an_gal_ta_kigal_she");
            case 258 -> shot(c, "ishtar_10_an_gal_ta_kigal_she_late");
            case 275 -> {
                yaw = 0.0F;
                pitch = -20.0F;
                ability(2);
            }
            case 280 -> {
                shot(c, "ishtar_11_sky_boat");
                log("barca, recarga tras el primer salto: " + p.getItemCooldownManager().isCoolingDown(com.nuwuman.fateubw.FateUBW.SKY_BOAT));
            }
            case 284 -> ability(2);
            case 292 -> {
                shot(c, "ishtar_12_sky_boat_second");
                log("barca, recarga tras el segundo salto: " + p.getItemCooldownManager().isCoolingDown(com.nuwuman.fateubw.FateUBW.SKY_BOAT));
            }
            case 300 -> {
                pitch = 0.0F;
                return true;
            }
            default -> {
            }
        }
        return false;
    }

    private boolean assassin(MinecraftClient c, ClientPlayerEntity p, int t) {
        switch (t) {
            case 0 -> setup(c, p, new String[]{
                    "armor.chest with fate_ubw:assassin_chestplate", "armor.legs with fate_ubw:assassin_leggings",
                    "armor.feet with fate_ubw:assassin_boots", "hotbar.0 with fate_ubw:monohoshizao",
                    "hotbar.1 with fate_ubw:assassin_chestplate", "hotbar.2 with fate_ubw:assassin_leggings", "hotbar.3 with fate_ubw:assassin_boots"},
                    new String[]{"~ ~ ~5"});
            case 40 -> shot(c, "assassin_01_firstperson");
            case 42 -> c.options.setPerspective(Perspective.THIRD_PERSON_FRONT);
            case 55 -> shot(c, "assassin_02_front");
            case 57 -> c.options.setPerspective(Perspective.THIRD_PERSON_BACK);
            case 70 -> shot(c, "assassin_03_back");
            case 72 -> yaw = 90.0F;
            case 82 -> shot(c, "assassin_04_side");
            case 84 -> {
                yaw = 60.0F;
                c.options.setPerspective(Perspective.THIRD_PERSON_FRONT);
            }
            case 90 -> use(c, p);
            case 108 -> shot(c, "assassin_04b_stance");
            case 112 -> {
                if (waitCharge(c, p, 22)) return false;
                holdUse = false;
            }
            case 114 -> shot(c, "assassin_05_tsubame_gaeshi");
            case 115, 116, 117, 118, 119 -> shot(c, "assassin_05b_tsubame_t" + t);
            case 120 -> {
                yaw = 0.0F;
                ability(0);
            }
            case 122 -> c.options.setPerspective(Perspective.THIRD_PERSON_FRONT);
            case 132 -> shot(c, "assassin_06_presence_concealment");
            case 140 -> {
                return true;
            }
            default -> {
            }
        }
        return false;
    }

    private boolean berserker(MinecraftClient c, ClientPlayerEntity p, int t) {
        switch (t) {
            case 0 -> setup(c, p, new String[]{
                    "armor.chest with fate_ubw:berserker_chestplate", "armor.legs with fate_ubw:berserker_leggings",
                    "armor.feet with fate_ubw:berserker_boots", "hotbar.0 with fate_ubw:berserker_axe_sword",
                    "hotbar.1 with fate_ubw:berserker_chestplate", "hotbar.2 with fate_ubw:berserker_leggings", "hotbar.3 with fate_ubw:berserker_boots"},
                    new String[]{});
            case 40 -> shot(c, "berserker_01_firstperson");
            case 42 -> c.options.setPerspective(Perspective.THIRD_PERSON_FRONT);
            case 55 -> shot(c, "berserker_02_front");
            case 57 -> c.options.setPerspective(Perspective.THIRD_PERSON_BACK);
            case 70 -> shot(c, "berserker_03_back");
            case 72 -> yaw = 90.0F;
            case 82 -> shot(c, "berserker_04_side");
            case 84 -> {
                yaw = 0.0F;
                husks(p, "~ ~ ~4", "~-2 ~ ~5", "~2 ~ ~5");
            }
            case 90 -> use(c, p);
            case 122 -> {
                if (waitCharge(c, p, 32)) return false;
                holdUse = false;
            }
            case 124 -> shot(c, "berserker_05_nine_lives");
            case 130 -> ability(0);
            case 134 -> shot(c, "berserker_06_mad_enhancement");
            // God Hand: morir con el conjunto puesto resucita y gasta una vida
            case 140 -> p.networkHandler.sendChatCommand("kill @s");
            case 150 -> {
                c.options.setPerspective(Perspective.FIRST_PERSON);
                log("vivo tras /kill: " + p.isAlive() + ", vidas: " + com.nuwuman.fateubw.berserker.BerserkerArmorItem.lives(p));
            }
            case 152 -> shot(c, "berserker_07_god_hand");
            case 160 -> {
                return true;
            }
            default -> {
            }
        }
        return false;
    }

    // Maná y Sellos de Comando en supervivencia (en creativo no se gasta maná)
    private boolean mana(MinecraftClient c, ClientPlayerEntity p, int t) {
        switch (t) {
            case 0 -> setup(c, p, new String[]{
                    "armor.chest with fate_ubw:saber_chestplate", "armor.legs with fate_ubw:saber_leggings",
                    "armor.feet with fate_ubw:saber_boots", "hotbar.0 with fate_ubw:excalibur"}, new String[]{});
            case 2 -> {
                p.networkHandler.sendChatCommand("gamemode survival");
                p.networkHandler.sendChatCommand("gamerule fateCooldownPercent 0");
                c.options.setPerspective(Perspective.THIRD_PERSON_BACK);
            }
            // Avalon cuesta 40: el tercero no llega
            case 20 -> ability(0);
            case 24 -> ability(0);
            case 28 -> ability(0);
            case 32 -> {
                log("maná tras tres Avalon: " + (int) com.nuwuman.fateubw.ability.Mana.get(p));
                shot(c, "mana_01_no_mana");
            }
            case 36 -> pressKey(org.lwjgl.glfw.GLFW.GLFW_KEY_V);
            case 44 -> {
                log("tras el Sello de Comando: maná " + (int) com.nuwuman.fateubw.ability.Mana.get(p)
                        + ", sellos " + com.nuwuman.fateubw.ability.CommandSeals.seals(p));
                shot(c, "mana_02_command_seal");
            }
            case 50 -> {
                p.networkHandler.sendChatCommand("gamerule fateCooldownPercent 100");
                p.networkHandler.sendChatCommand("gamemode creative");
                return true;
            }
            default -> {
            }
        }
        return false;
    }

    // Usar el objeto de la mano sobre el bloque al que apunta la mira
    private static void useOnBlock(MinecraftClient c, ClientPlayerEntity p) {
        if (c.crosshairTarget instanceof net.minecraft.util.hit.BlockHitResult hit) {
            c.interactionManager.interactBlock(p, Hand.MAIN_HAND, hit);
        } else {
            log("la mira no apunta a un bloque");
        }
    }

    private static long countNear(MinecraftClient c, ClientPlayerEntity p, net.minecraft.entity.EntityType<?> type) {
        var server = c.getServer();
        var center = p.getPos();
        return server.submit(() -> server.getOverworld().getEntitiesByType(type,
                new net.minecraft.util.math.Box(center, center).expand(24), e -> e.isAlive()).size()).join();
    }

    // Invocación con catalizador y el Santo Grial
    // Guerra que sobrevive al reinicio: la primera ejecución la empieza; con FATE_WORLD, la segunda comprueba que sigue
    private boolean grailWar(ClientPlayerEntity p, int t) {
        if (t == 0) {
            log("guerra al cargar: " + com.nuwuman.fateubw.grail.GrailWar.active());
            if (System.getenv("FATE_WORLD") == null) p.networkHandler.sendChatCommand("grailwar start");
        }
        if (t == 20) log("guerra activa: " + com.nuwuman.fateubw.grail.GrailWar.active());
        return t >= 20;
    }

    private boolean grail(MinecraftClient c, ClientPlayerEntity p, int t) {
        switch (t) {
            case 0 -> setup(c, p, new String[]{"hotbar.0 with fate_ubw:summoning_circle", "weapon.offhand with minecraft:prismarine_shard"},
                    new String[]{});
            case 10 -> pitch = 45.0F;
            case 20 -> useOnBlock(c, p);
            case 24 -> {
                pitch = 25.0F;
                c.options.setPerspective(Perspective.THIRD_PERSON_BACK);
            }
            case 50 -> shot(c, "grail_01_summoning");
            case 90 -> {
                shot(c, "grail_02_summoned_lancer");
                var server = c.getServer();
                var center = p.getPos();
                log("objetos invocados: " + server.submit(() -> server.getOverworld().getEntitiesByClass(net.minecraft.entity.ItemEntity.class,
                        new net.minecraft.util.math.Box(center, center).expand(8), e -> true).stream()
                        .map(e -> net.minecraft.registry.Registries.ITEM.getId(e.getStack().getItem()).getPath()).toList()).join());
            }
            case 95 -> {
                pitch = 0.0F;
                p.networkHandler.sendChatCommand("item replace entity @s weapon.mainhand with fate_ubw:holy_grail");
            }
            case 105 -> c.interactionManager.interactItem(p, Hand.MAIN_HAND);
            case 110 -> shot(c, "grail_03_wish_screen");
            // Pulsa el botón de Gilgamesh como lo haría el jugador
            case 112 -> {
                if (c.currentScreen == null) {
                    log("no se abrió la pantalla del deseo");
                } else {
                    c.currentScreen.children().stream()
                            .filter(w -> w instanceof net.minecraft.client.gui.widget.ButtonWidget b && b.getMessage().getString().equals("Gilgamesh"))
                            .findFirst().ifPresent(w -> ((net.minecraft.client.gui.widget.ButtonWidget) w).onPress());
                }
            }
            case 120 -> {
                log("tras el deseo: pecho " + p.getEquippedStack(net.minecraft.entity.EquipmentSlot.CHEST).getItem()
                        + ", Grial en la mano: " + p.getMainHandStack().getItem());
                c.options.setPerspective(Perspective.THIRD_PERSON_FRONT);
            }
            case 130 -> shot(c, "grail_04_wish_granted");
            case 132 -> p.networkHandler.sendChatCommand("grailwar start");
            case 140 -> {
                return true;
            }
            default -> {
            }
        }
        return false;
    }

    // Joya de Tohsaka, póster reforzado de Shirou y Zelzeriz de Illya
    private boolean masters(MinecraftClient c, ClientPlayerEntity p, int t) {
        switch (t) {
            case 0 -> setup(c, p, new String[]{"hotbar.0 with fate_ubw:rin_jewel 16", "hotbar.1 with fate_ubw:shirou_poster",
                    "hotbar.2 with fate_ubw:zelzeriz", "weapon.offhand with minecraft:iron_sword[damage=200]"},
                    new String[]{"~-2 ~ ~8", "~2 ~ ~9", "~ ~ ~12"});
            case 30 -> c.options.setPerspective(Perspective.THIRD_PERSON_BACK);
            case 35 -> c.interactionManager.interactItem(p, Hand.MAIN_HAND);
            case 44 -> shot(c, "masters_01_rin_jewel");
            case 50 -> {
                p.getInventory().selectedSlot = 1;
                log("espada antes del Refuerzo: " + p.getOffHandStack().getDamage());
            }
            case 54 -> c.interactionManager.interactItem(p, Hand.MAIN_HAND);
            case 62 -> {
                log("espada tras el Refuerzo: " + p.getOffHandStack().getDamage());
                shot(c, "masters_02_reinforcement");
            }
            case 66 -> p.getInventory().selectedSlot = 2;
            case 70 -> c.interactionManager.interactItem(p, Hand.MAIN_HAND);
            case 90 -> shot(c, "masters_03_zelzeriz");
            case 96 -> c.options.setPerspective(Perspective.THIRD_PERSON_FRONT);
            case 104 -> shot(c, "masters_04_zelzeriz_front");
            case 130 -> {
                return true;
            }
            default -> {
            }
        }
        return false;
    }

    // Servants enemigos: Berserker (jefe), Lancer y Assassin
    private boolean npc(MinecraftClient c, ClientPlayerEntity p, int t) {
        switch (t) {
            case 0 -> setup(c, p, new String[]{}, new String[]{});
            case 5 -> {
                p.networkHandler.sendChatCommand("summon fate_ubw:berserker_servant ~ ~ ~7 {NoAI:1b,Rotation:[180f,0f]}");
                p.networkHandler.sendChatCommand("summon fate_ubw:lancer_servant ~-3 ~ ~6 {NoAI:1b,Rotation:[180f,0f]}");
                p.networkHandler.sendChatCommand("summon fate_ubw:assassin_servant ~3 ~ ~6 {NoAI:1b,Rotation:[180f,0f]}");
            }
            case 40 -> shot(c, "npc_01_servants");
            case 42 -> {
                pitch = 10.0F;
                yaw = -15.0F;
            }
            case 50 -> shot(c, "npc_02_berserker_bossbar");
            // God Hand: /kill no basta para matarlo
            case 55 -> p.networkHandler.sendChatCommand("kill @e[type=fate_ubw:berserker_servant]");
            case 65 -> log("Berserker tras /kill: " + countNear(c, p, com.nuwuman.fateubw.npc.ServantNpcs.BERSERKER) + " vivo(s)");
            // Lancer con IA contra un gólem: debe lanzarse con la Gáe Bolg
            case 70 -> {
                yaw = 0.0F;
                pitch = 0.0F;
                p.networkHandler.sendChatCommand("kill @e[type=fate_ubw:lancer_servant]");
                p.networkHandler.sendChatCommand("summon fate_ubw:lancer_servant ~ ~ ~16");
                p.networkHandler.sendChatCommand("summon iron_golem ~ ~ ~10");
            }
            case 160 -> shot(c, "npc_03_lancer_vs_golem");
            case 200 -> {
                p.networkHandler.sendChatCommand("kill @e[type=iron_golem]");
                p.networkHandler.sendChatCommand("kill @e[type=fate_ubw:lancer_servant]");
                p.networkHandler.sendChatCommand("kill @e[type=fate_ubw:assassin_servant]");
                p.networkHandler.sendChatCommand("tp @e[type=fate_ubw:berserker_servant] ~ -200 ~");
                return true;
            }
            default -> {
            }
        }
        return false;
    }

    // Hrunting, Blood Fort Andromeda e Invisible Air
    private boolean newNps(MinecraftClient c, ClientPlayerEntity p, int t) {
        switch (t) {
            case 0 -> setup(c, p, new String[]{
                    "armor.chest with fate_ubw:archer_chestplate", "armor.legs with fate_ubw:archer_leggings",
                    "armor.feet with fate_ubw:archer_boots"}, new String[]{"~ ~ ~12"});
            case 25 -> {
                c.options.setPerspective(Perspective.THIRD_PERSON_BACK);
                pressKey(org.lwjgl.glfw.GLFW.GLFW_KEY_G);
                pressKey(org.lwjgl.glfw.GLFW.GLFW_KEY_G);
            }
            case 30 -> ability(2);
            case 33 -> shot(c, "np_01_hrunting");
            case 38 -> shot(c, "np_02_hrunting_hit");
            case 50 -> setup(c, p, new String[]{
                    "armor.head with fate_ubw:rider_helmet", "armor.chest with fate_ubw:rider_chestplate",
                    "armor.legs with fate_ubw:rider_leggings", "armor.feet with fate_ubw:rider_boots"},
                    new String[]{"~-3 ~ ~4", "~3 ~ ~5", "~ ~ ~7"});
            case 75 -> {
                c.options.setPerspective(Perspective.THIRD_PERSON_BACK);
                ability(2);
            }
            case 100 -> shot(c, "np_03_blood_fort_andromeda");
            case 130 -> setup(c, p, new String[]{"hotbar.0 with fate_ubw:excalibur"}, new String[]{});
            case 150 -> shot(c, "np_04_invisible_air_firstperson");
            case 152 -> c.options.setPerspective(Perspective.THIRD_PERSON_FRONT);
            case 160 -> shot(c, "np_05_invisible_air_front");
            case 162 -> use(c, p);
            case 185 -> shot(c, "np_06_revealed_charging");
            case 187 -> holdUse = false;
            case 200 -> {
                return true;
            }
            default -> {
            }
        }
        return false;
    }

    // De noche: las líneas de Ea brillan en la oscuridad y sus cilindros giran
    // Cada armadura puesta, de frente, de espaldas y de lado
    private static final String[] ARMOR_SETS = System.getenv("FATE_ARMORS") != null ? System.getenv("FATE_ARMORS").split(",")
            : new String[]{"archer", "lancer", "rider", "gilgamesh", "caster", "assassin", "berserker"};

    private boolean armors(MinecraftClient c, ClientPlayerEntity p, int t) {
        if (t < 40) return false;                          // que el mundo termine de cargar
        int set = (t - 40) / 50, step = (t - 40) % 50;
        if (set >= ARMOR_SETS.length) return true;
        String n = ARMOR_SETS[set];
        switch (step) {
            case 0 -> {
                String head = n.equals("rider") ? "rider_helmet" : n.equals("caster") ? "caster_hood" : null;
                setup(c, p, head == null
                        ? new String[]{"armor.chest with fate_ubw:" + n + "_chestplate", "armor.legs with fate_ubw:" + n + "_leggings", "armor.feet with fate_ubw:" + n + "_boots"}
                        : new String[]{"armor.head with fate_ubw:" + head, "armor.chest with fate_ubw:" + n + "_chestplate",
                        "armor.legs with fate_ubw:" + n + "_leggings", "armor.feet with fate_ubw:" + n + "_boots"}, new String[]{});
                p.networkHandler.sendChatCommand("fill ~-8 ~ ~-8 ~8 ~12 ~8 air");   // que no haya hojas delante de la cámara
                c.options.setPerspective(Perspective.THIRD_PERSON_FRONT);
            }
            case 20 -> shot(c, "armor_" + n + "_1_front");
            case 22 -> c.options.setPerspective(Perspective.THIRD_PERSON_BACK);
            case 32 -> shot(c, "armor_" + n + "_2_back");
            case 34 -> yaw = 90.0F;
            case 46 -> shot(c, "armor_" + n + "_3_side");
            default -> {
            }
        }
        return false;
    }

    private boolean glow(MinecraftClient c, ClientPlayerEntity p, int t) {
        switch (t) {
            case 0 -> setup(c, p, new String[]{"hotbar.0 with fate_ubw:ea", "hotbar.1 with fate_ubw:excalibur",
                    "hotbar.2 with fate_ubw:gae_bolg", "hotbar.3 with fate_ubw:rule_breaker", "hotbar.4 with fate_ubw:hrunting"}, new String[]{});
            case 2 -> p.networkHandler.sendChatCommand("time set midnight");
            case 30 -> shot(c, "glow_01_ea_night_firstperson");
            case 32 -> c.options.setPerspective(Perspective.THIRD_PERSON_FRONT);
            case 44 -> shot(c, "glow_02_ea_night_front");
            case 46 -> use(c, p);
            case 70 -> shot(c, "glow_03_ea_charging_night");
            case 72 -> holdUse = false;

            // Excalibur: tapada por Invisible Air, cargando (dorada y brillante) y revelada por Strike Air
            case 80 -> p.getInventory().selectedSlot = 1;
            case 100 -> shot(c, "glow_04_excalibur_veiled_night");
            case 102 -> use(c, p);
            case 105, 108, 111 -> shot(c, "glow_05_excalibur_unveil_t" + t);
            case 130 -> shot(c, "glow_06_excalibur_charging_night");
            case 140 -> {
                if (waitCharge(c, p, 62)) return false;
                shot(c, "glow_07_excalibur_charged_night");
            }
            case 142 -> c.options.setPerspective(Perspective.FIRST_PERSON);
            case 145 -> shot(c, "glow_08_excalibur_charged_firstperson");
            case 147 -> {
                c.options.setPerspective(Perspective.THIRD_PERSON_FRONT);
                holdUse = false;
            }
            case 200 -> shot(c, "glow_09_excalibur_veiled_again");
            case 205 -> holdSneak = true;
            case 208 -> c.interactionManager.interactItem(p, Hand.MAIN_HAND);
            case 210, 214, 220 -> shot(c, "glow_10_strike_air_reveal_t" + t);
            case 222 -> holdSneak = false;

            // Armas que brillan: Gáe Bolg, Rule Breaker y Hrunting
            case 240 -> p.getInventory().selectedSlot = 2;
            case 255 -> shot(c, "glow_11_gae_bolg_night");
            case 257 -> p.getInventory().selectedSlot = 3;
            case 272 -> shot(c, "glow_12_rule_breaker_night");
            case 274 -> p.getInventory().selectedSlot = 4;
            case 289 -> shot(c, "glow_13_hrunting_night");
            case 291 -> c.options.setPerspective(Perspective.FIRST_PERSON);
            case 300 -> shot(c, "glow_14_hrunting_firstperson");
            case 310 -> {
                p.networkHandler.sendChatCommand("time set noon");
                return true;
            }
            default -> {
            }
        }
        return false;
    }

    private static void husks(ClientPlayerEntity p, String... positions) {
        for (String pos : positions) p.networkHandler.sendChatCommand("summon husk " + pos + " {NoAI:1b,PersistenceRequired:1b}");
    }

    private static void ability(int index) {
        net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking.send(new com.nuwuman.fateubw.ability.UseAbilityPayload(index));
    }

    private static void pressKey(int key) {
        net.minecraft.client.option.KeyBinding.onKeyPressed(net.minecraft.client.util.InputUtil.Type.KEYSYM.createFromCode(key));
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
