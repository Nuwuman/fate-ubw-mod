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
            ? List.of("saber", "archer", "lancer", "rider", "gilgamesh", "ubw") : List.of(System.getenv("FATE_SHOWCASE").split(","));
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
                    "hotbar.0 with fate_ubw:rider_dagger", "hotbar.1 with fate_ubw:bellerophon",
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
            case 120 -> holdSneak = true;
            case 122 -> c.interactionManager.interactItem(p, Hand.MAIN_HAND);
            case 124 -> holdSneak = false;
            case 127 -> shot(c, "rider_07_mystic_eyes");
            case 129 -> c.options.setPerspective(Perspective.THIRD_PERSON_FRONT);
            case 133 -> shot(c, "rider_08_mystic_eyes_front");

            // Bellerophon: invocar, montar, despegar, volar y embestir
            case 140 -> {
                c.options.setPerspective(Perspective.THIRD_PERSON_BACK);
                p.getInventory().selectedSlot = 1;
            }
            case 145 -> c.interactionManager.interactItem(p, Hand.MAIN_HAND);
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
            case 250 -> c.interactionManager.interactItem(p, Hand.MAIN_HAND);
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
                    "armor.feet with fate_ubw:gilgamesh_boots", "hotbar.0 with fate_ubw:gate_of_babylon",
                    "hotbar.1 with fate_ubw:ea", "hotbar.2 with fate_ubw:gilgamesh_chestplate",
                    "hotbar.3 with fate_ubw:gilgamesh_leggings", "hotbar.4 with fate_ubw:gilgamesh_boots"},
                    new String[]{"~-2 ~ ~10", "~2 ~ ~12", "~ ~ ~16"});
            case 40 -> shot(c, "gilgamesh_01_firstperson_key");
            case 42 -> c.options.setPerspective(Perspective.THIRD_PERSON_FRONT);
            case 55 -> shot(c, "gilgamesh_02_front");
            case 57 -> c.options.setPerspective(Perspective.THIRD_PERSON_BACK);
            case 70 -> shot(c, "gilgamesh_03_back");

            // Gate of Babylon
            case 80 -> c.interactionManager.interactItem(p, Hand.MAIN_HAND);
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
                    "armor.feet with fate_ubw:archer_boots", "hotbar.0 with fate_ubw:unlimited_blade_works"},
                    new String[]{"~-2 ~ ~6", "~2 ~ ~8", "~ ~ ~10"});
            // Algo que el Marble debe devolver tal cual: una torre y un cofre con diamantes
            case 2 -> {
                p.networkHandler.sendChatCommand("fill ~4 ~ ~5 ~5 ~5 ~6 stone_bricks");
                p.networkHandler.sendChatCommand("setblock ~-4 ~ ~4 chest{Items:[{Slot:0b,id:\"minecraft:diamond\",count:5}]}");
                chestPos = p.getBlockPos().add(-4, 0, 4);
            }
            case 30 -> c.options.setPerspective(Perspective.THIRD_PERSON_BACK);
            case 40 -> use(c, p);
            case 70 -> shot(c, "ubw_01_chant");
            case 105 -> {
                if (waitCharge(c, p, 62)) return false;
                holdUse = false;
            }
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
            case 182 -> c.interactionManager.interactItem(p, Hand.MAIN_HAND);
            case 184 -> yaw = 25.0F;
            case 190 -> shot(c, "ubw_06_barrage");
            case 196 -> shot(c, "ubw_07_barrage_late");
            // Deshacer el Marble (después de la recarga de 1,5 s de la ráfaga)
            case 228 -> {
                yaw = 0.0F;
                holdSneak = true;
            }
            case 232 -> c.interactionManager.interactItem(p, Hand.MAIN_HAND);
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
