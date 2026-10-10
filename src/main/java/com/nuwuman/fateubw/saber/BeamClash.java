package com.nuwuman.fateubw.saber;

import com.nuwuman.fateubw.FateUBW;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;

/** Red del quick time event del choque de Noble Phantasms: el servidor manda las teclas, el cliente las que pulsa. */
public final class BeamClash {
    /** Empieza (ticks > 0) o termina (ticks = 0) el QTE del haz {@code beam} con la tira de teclas {@code keys}. */
    public record QtePayload(int beam, String keys, int ticks) implements CustomPayload {
        public static final Id<QtePayload> ID = new Id<>(FateUBW.id("clash_qte"));
        public static final PacketCodec<RegistryByteBuf, QtePayload> CODEC = PacketCodec.tuple(
                PacketCodecs.VAR_INT, QtePayload::beam, PacketCodecs.STRING, QtePayload::keys, PacketCodecs.VAR_INT, QtePayload::ticks,
                QtePayload::new);

        @Override
        public Id<? extends CustomPayload> getId() {
            return ID;
        }
    }

    /** Una tecla pulsada durante el QTE (W, A, S o D). */
    public record KeyPayload(int beam, String key) implements CustomPayload {
        public static final Id<KeyPayload> ID = new Id<>(FateUBW.id("clash_key"));
        public static final PacketCodec<RegistryByteBuf, KeyPayload> CODEC = PacketCodec.tuple(
                PacketCodecs.VAR_INT, KeyPayload::beam, PacketCodecs.STRING, KeyPayload::key, KeyPayload::new);

        @Override
        public Id<? extends CustomPayload> getId() {
            return ID;
        }
    }

    private BeamClash() {
    }

    public static void register() {
        PayloadTypeRegistry.playS2C().register(QtePayload.ID, QtePayload.CODEC);
        PayloadTypeRegistry.playC2S().register(KeyPayload.ID, KeyPayload.CODEC);
        ServerPlayNetworking.registerGlobalReceiver(KeyPayload.ID, (payload, context) -> {
            if (payload.key().length() == 1 && context.player().getServerWorld().getEntityById(payload.beam()) instanceof ExcaliburBeamEntity beam) {
                beam.press(context.player(), payload.key().charAt(0));
            }
        });
    }
}
