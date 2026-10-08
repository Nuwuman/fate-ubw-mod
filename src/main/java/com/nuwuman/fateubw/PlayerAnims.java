package com.nuwuman.fateubw;

import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.PlayerLookup;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.server.network.ServerPlayerEntity;

import java.util.function.BiConsumer;

/**
 * Animaciones de cuerpo de un golpe (Player Animator): el que la hace la ve desde su cliente y el servidor avisa a quien lo vea.
 * Las posturas mantenidas (cargar Excalibur, la guardia de Tsubame Gaeshi) las deduce cada cliente al ver el ítem en uso.
 */
public final class PlayerAnims {
    public record PlayPayload(int entity, String animation) implements CustomPayload {
        public static final Id<PlayPayload> ID = new Id<>(FateUBW.id("player_anim"));
        public static final PacketCodec<RegistryByteBuf, PlayPayload> CODEC = PacketCodec.tuple(
                PacketCodecs.VAR_INT, PlayPayload::entity, PacketCodecs.STRING, PlayPayload::animation, PlayPayload::new);

        @Override
        public Id<? extends CustomPayload> getId() {
            return ID;
        }
    }

    public static void register() {
        PayloadTypeRegistry.playS2C().register(PlayPayload.ID, PlayPayload.CODEC);
    }

    /** Lo pone el cliente: reproduce una animación en un jugador de su mundo. */
    public static BiConsumer<PlayerEntity, String> clientPlay = (player, animation) -> {};

    /**
     * Llamar en los dos lados. El propio jugador la ve al instante desde su cliente; el servidor solo avisa a los demás.
     * animation: nombre del archivo en assets/fate_ubw/player_animations.
     */
    public static void play(PlayerEntity player, String animation) {
        if (player.getWorld().isClient()) {
            clientPlay.accept(player, animation);
            return;
        }
        PlayPayload payload = new PlayPayload(player.getId(), animation);
        for (ServerPlayerEntity other : PlayerLookup.tracking(player)) {
            if (other != player) ServerPlayNetworking.send(other, payload);
        }
    }
}
