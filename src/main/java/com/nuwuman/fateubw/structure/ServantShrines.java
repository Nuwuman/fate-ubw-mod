package com.nuwuman.fateubw.structure;

import com.nuwuman.fateubw.FateUBW;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.structure.StructurePieceType;
import net.minecraft.world.Heightmap;
import net.minecraft.world.gen.structure.Structure;
import net.minecraft.world.gen.structure.StructureType;

import java.util.Optional;

/**
 * Santuarios de los servants: una construcción pequeña por servant (Saber, Archer, Lancer, Rider, Gilgamesh, Caster,
 * Assassin y Berserker) con su servant enemigo de guardia y un cofre con botín. Salen por el mundo
 * (worldgen/structure_set/servant_shrines.json) y se buscan con /locate structure fate_ubw:saber_shrine, etc.
 */
public final class ServantShrines {
    public static final StructurePieceType PIECE = Registry.register(Registries.STRUCTURE_PIECE, FateUBW.id("servant_shrine"),
            (StructurePieceType.Simple) ServantShrinePiece::new);
    public static final StructureType<Shrine> TYPE = Registry.register(Registries.STRUCTURE_TYPE, FateUBW.id("servant_shrine"),
            () -> Shrine.CODEC);

    private ServantShrines() {
    }

    /** Carga la clase para que se registren el tipo de estructura y el de pieza. */
    public static void register() {
    }

    /** La estructura: un santuario del servant indicado en su JSON ("servant": "saber"...). */
    public static class Shrine extends Structure {
        public static final MapCodec<Shrine> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
                configCodecBuilder(instance),
                Codec.STRING.fieldOf("servant").forGetter(shrine -> shrine.servant)
        ).apply(instance, Shrine::new));

        private final String servant;

        public Shrine(Config config, String servant) {
            super(config);
            this.servant = servant;
        }

        @Override
        public Optional<StructurePosition> getStructurePosition(Context context) {
            return getStructurePosition(context, Heightmap.Type.WORLD_SURFACE_WG, collector -> collector.addPiece(
                    new ServantShrinePiece(servant, context.random(), context.chunkPos().getStartX(), context.chunkPos().getStartZ())));
        }

        @Override
        public StructureType<?> getType() {
            return TYPE;
        }
    }
}
