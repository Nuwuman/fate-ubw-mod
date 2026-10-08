package com.nuwuman.fateubw.npc;

import com.nuwuman.fateubw.FateUBW;
import net.fabricmc.fabric.api.biome.v1.BiomeModifications;
import net.fabricmc.fabric.api.biome.v1.BiomeSelectors;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.gamerule.v1.GameRuleFactory;
import net.fabricmc.fabric.api.gamerule.v1.GameRuleRegistry;
import net.fabricmc.fabric.api.object.builder.v1.entity.FabricDefaultAttributeRegistry;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.SpawnGroup;
import net.minecraft.entity.SpawnLocationTypes;
import net.minecraft.entity.SpawnReason;
import net.minecraft.entity.SpawnRestriction;
import net.minecraft.entity.mob.HostileEntity;
import net.minecraft.item.Item;
import net.minecraft.item.SpawnEggItem;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.world.GameRules;
import net.minecraft.world.Heightmap;

/** Los servants enemigos: tipos de entidad, atributos, huevos y aparición natural. */
public final class ServantNpcs {
    /** /gamerule fateServantSpawns false: Lancer y Assassin dejan de aparecer solos de noche. */
    public static final GameRules.Key<GameRules.BooleanRule> SPAWNS = GameRuleRegistry.register(
            "fateServantSpawns", GameRules.Category.SPAWNING, GameRuleFactory.createBooleanRule(true));

    public static final EntityType<HostileServantEntity> BERSERKER = type("berserker_servant", HostileServantEntity.Kind.BERSERKER, 0.75F, 2.4F);
    public static final EntityType<HostileServantEntity> LANCER = type("lancer_servant", HostileServantEntity.Kind.LANCER, 0.6F, 1.8F);
    public static final EntityType<HostileServantEntity> ASSASSIN = type("assassin_servant", HostileServantEntity.Kind.ASSASSIN, 0.6F, 1.8F);

    public static final Item BERSERKER_EGG = egg("berserker_servant_spawn_egg", BERSERKER, 0x3c3d42, 0x7a5530);
    public static final Item LANCER_EGG = egg("lancer_servant_spawn_egg", LANCER, 0x22337a, 0xc0182a);
    public static final Item ASSASSIN_EGG = egg("assassin_servant_spawn_egg", ASSASSIN, 0x5a3d8a, 0xd8c9a0);

    private ServantNpcs() {
    }

    private static EntityType<HostileServantEntity> type(String name, HostileServantEntity.Kind kind, float width, float height) {
        return Registry.register(Registries.ENTITY_TYPE, FateUBW.id(name),
                EntityType.Builder.<HostileServantEntity>create((type, world) -> new HostileServantEntity(type, world, kind), SpawnGroup.MONSTER)
                        .dimensions(width, height).maxTrackingRange(10).build(name));
    }

    private static Item egg(String name, EntityType<HostileServantEntity> type, int primary, int secondary) {
        return Registry.register(Registries.ITEM, FateUBW.id(name), new SpawnEggItem(type, primary, secondary, new Item.Settings()));
    }

    public static void register() {
        FabricDefaultAttributeRegistry.register(BERSERKER, HostileServantEntity.attributes(HostileServantEntity.Kind.BERSERKER));
        FabricDefaultAttributeRegistry.register(LANCER, HostileServantEntity.attributes(HostileServantEntity.Kind.LANCER));
        FabricDefaultAttributeRegistry.register(ASSASSIN, HostileServantEntity.attributes(HostileServantEntity.Kind.ASSASSIN));

        for (EntityType<HostileServantEntity> type : new EntityType[]{LANCER, ASSASSIN}) {
            SpawnRestriction.register(type, SpawnLocationTypes.ON_GROUND, Heightmap.Type.MOTION_BLOCKING_NO_LEAVES,
                    (t, world, reason, pos, random) -> HostileEntity.canSpawnInDark(t, world, reason, pos, random)
                            && (reason != SpawnReason.NATURAL || world.toServerWorld().getGameRules().getBoolean(SPAWNS)));
            BiomeModifications.addSpawn(BiomeSelectors.foundInOverworld(), SpawnGroup.MONSTER, type, 2, 1, 1);
        }

        // God Hand del Berserker jefe
        ServerLivingEntityEvents.ALLOW_DEATH.register((entity, source, amount) ->
                !(entity instanceof HostileServantEntity servant && servant.tryRevive()));
    }
}
