package com.nuwuman.fateubw.grail;

import com.mojang.brigadier.context.CommandContext;
import com.nuwuman.fateubw.FateUBW;
import com.nuwuman.fateubw.ability.CommandSeals;
import com.nuwuman.fateubw.ability.Mana;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.item.ItemStack;
import net.minecraft.network.packet.s2c.play.SubtitleS2CPacket;
import net.minecraft.network.packet.s2c.play.TitleS2CPacket;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.world.GameMode;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * La Guerra del Santo Grial: {@code /grailwar start} reparte un servant distinto (si hay bastantes) a cada jugador
 * conectado, con su equipo, el maná lleno y tres Sellos de Comando. Quien muere queda como espectador hasta el final.
 * El último en pie gana el Santo Grial. {@code /grailwar stop} la termina; {@code /grailwar status} dice quién sigue.
 * Solo vive en memoria: si el servidor se reinicia, la guerra termina.
 */
public final class GrailWar {
    // Participantes que siguen en pie, con su clase
    private static final Map<UUID, String> ALIVE = new LinkedHashMap<>();
    // Eliminados y el modo de juego que tenían, para devolvérselo al terminar
    private static final Map<UUID, GameMode> FALLEN = new HashMap<>();
    private static boolean active;

    private GrailWar() {
    }

    public static void register() {
        CommandRegistrationCallback.EVENT.register((dispatcher, registries, environment) -> dispatcher.register(
                CommandManager.literal("grailwar").requires(source -> source.hasPermissionLevel(2))
                        .then(CommandManager.literal("start").executes(GrailWar::start))
                        .then(CommandManager.literal("stop").executes(ctx -> {
                            if (!active) return fail(ctx, "command.fate_ubw.grailwar.none");
                            finish(ctx.getSource().getServer(), null);
                            return 1;
                        }))
                        .then(CommandManager.literal("status").executes(GrailWar::status))));

        ServerLivingEntityEvents.AFTER_DEATH.register((entity, source) -> {
            if (active && entity instanceof ServerPlayerEntity player) eliminate(player, true);
        });
        ServerPlayerEvents.AFTER_RESPAWN.register((oldPlayer, newPlayer, alive) -> {
            if (active && FALLEN.containsKey(newPlayer.getUuid())) newPlayer.changeGameMode(GameMode.SPECTATOR);
        });
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
            if (active) eliminate(handler.getPlayer(), false);
        });
        ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
            ALIVE.clear();
            FALLEN.clear();
            active = false;
        });
    }

    private static int fail(CommandContext<ServerCommandSource> ctx, String key) {
        ctx.getSource().sendError(Text.translatable(key));
        return 0;
    }

    private static int start(CommandContext<ServerCommandSource> ctx) {
        MinecraftServer server = ctx.getSource().getServer();
        if (active) return fail(ctx, "command.fate_ubw.grailwar.running");
        List<ServerPlayerEntity> players = new ArrayList<>(server.getPlayerManager().getPlayerList().stream()
                .filter(p -> !p.isSpectator()).toList());
        if (players.size() < 2) return fail(ctx, "command.fate_ubw.grailwar.players");

        List<Servants.Servant> classes = new ArrayList<>(Servants.all());
        Collections.shuffle(classes);
        active = true;
        ALIVE.clear();
        FALLEN.clear();
        for (int i = 0; i < players.size(); i++) {
            ServerPlayerEntity player = players.get(i);
            Servants.Servant servant = classes.get(i % classes.size());
            servant.equip(player);
            Mana.fill(player);
            CommandSeals.set(player, CommandSeals.MAX);
            ALIVE.put(player.getUuid(), servant.id());
            player.networkHandler.sendPacket(new TitleS2CPacket(Text.translatable("title.fate_ubw.grailwar").formatted(Formatting.GOLD)));
            player.networkHandler.sendPacket(new SubtitleS2CPacket(Text.translatable("title.fate_ubw.grailwar.class",
                    Text.translatable("servant.fate_ubw." + servant.id())).formatted(Formatting.RED)));
            player.playSoundToPlayer(SoundEvents.EVENT_RAID_HORN.value(), SoundCategory.PLAYERS, 1.0F, 1.0F);
        }
        server.getPlayerManager().broadcast(Text.translatable("message.fate_ubw.grailwar.start", players.size()).formatted(Formatting.GOLD), false);
        return 1;
    }

    private static int status(CommandContext<ServerCommandSource> ctx) {
        if (!active) return fail(ctx, "command.fate_ubw.grailwar.none");
        MinecraftServer server = ctx.getSource().getServer();
        for (Map.Entry<UUID, String> entry : ALIVE.entrySet()) {
            ServerPlayerEntity player = server.getPlayerManager().getPlayer(entry.getKey());
            String name = player != null ? player.getName().getString() : "?";
            ctx.getSource().sendFeedback(() -> Text.literal(name + " — ").append(Text.translatable("servant.fate_ubw." + entry.getValue())), false);
        }
        return ALIVE.size();
    }

    private static void eliminate(ServerPlayerEntity player, boolean died) {
        String servant = ALIVE.remove(player.getUuid());
        if (servant == null) return;
        if (died) FALLEN.put(player.getUuid(), player.interactionManager.getGameMode());
        MinecraftServer server = player.getServer();
        if (server == null) return;
        server.getPlayerManager().broadcast(Text.translatable("message.fate_ubw.grailwar.fallen", player.getName(),
                Text.translatable("servant.fate_ubw." + servant), ALIVE.size()).formatted(Formatting.RED), false);
        if (ALIVE.size() <= 1) {
            ServerPlayerEntity winner = ALIVE.isEmpty() ? null : server.getPlayerManager().getPlayer(ALIVE.keySet().iterator().next());
            finish(server, winner);
        }
    }

    // Fin: el ganador recibe el Santo Grial y los eliminados vuelven a su modo de juego
    private static void finish(MinecraftServer server, ServerPlayerEntity winner) {
        active = false;
        if (winner != null) {
            winner.getInventory().offerOrDrop(new ItemStack(FateUBW.HOLY_GRAIL));
            winner.getServerWorld().spawnParticles(ParticleTypes.END_ROD, winner.getX(), winner.getY() + 1.0, winner.getZ(),
                    200, 0.5, 4.0, 0.5, 0.1);
            for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
                player.networkHandler.sendPacket(new TitleS2CPacket(Text.translatable("title.fate_ubw.grailwar.winner", winner.getName())
                        .formatted(Formatting.GOLD)));
                player.playSoundToPlayer(SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, SoundCategory.PLAYERS, 1.0F, 1.0F);
            }
        } else {
            server.getPlayerManager().broadcast(Text.translatable("message.fate_ubw.grailwar.stopped").formatted(Formatting.GRAY), false);
        }
        for (Map.Entry<UUID, GameMode> entry : FALLEN.entrySet()) {
            ServerPlayerEntity player = server.getPlayerManager().getPlayer(entry.getKey());
            if (player != null) player.changeGameMode(entry.getValue() == GameMode.SPECTATOR ? GameMode.SURVIVAL : entry.getValue());
        }
        ALIVE.clear();
        FALLEN.clear();
    }

    public static boolean active() {
        return active;
    }
}
