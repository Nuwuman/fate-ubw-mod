package com.nuwuman.fateubw.ability;

import com.nuwuman.fateubw.FateUBW;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.codec.PacketCodecs;
import net.minecraft.network.packet.CustomPayload;

/** Cliente → servidor: usar la habilidad número {@code index} del conjunto que lleva puesto. */
public record UseAbilityPayload(int index) implements CustomPayload {
    public static final Id<UseAbilityPayload> ID = new Id<>(FateUBW.id("use_ability"));
    public static final PacketCodec<RegistryByteBuf, UseAbilityPayload> CODEC =
            PacketCodec.tuple(PacketCodecs.VAR_INT, UseAbilityPayload::index, UseAbilityPayload::new);

    @Override
    public Id<? extends CustomPayload> getId() {
        return ID;
    }
}
