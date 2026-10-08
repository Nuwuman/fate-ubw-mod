package com.nuwuman.fateubw;

import com.nuwuman.fateubw.archer.ArcherArmorItem;
import com.nuwuman.fateubw.archer.ArcherBowItem;
import com.nuwuman.fateubw.archer.FalchionItem;
import com.nuwuman.fateubw.archer.RhoAiasEntity;
import com.nuwuman.fateubw.archer.SwordArrowEntity;
import com.nuwuman.fateubw.archer.ThrownFalchionEntity;
import com.nuwuman.fateubw.gilgamesh.BabylonPortalEntity;
import com.nuwuman.fateubw.gilgamesh.BabylonWeaponEntity;
import com.nuwuman.fateubw.gilgamesh.EaItem;
import com.nuwuman.fateubw.gilgamesh.EnumaElishEntity;
import com.nuwuman.fateubw.gilgamesh.GateOfBabylonItem;
import com.nuwuman.fateubw.gilgamesh.GilgameshArmorItem;
import com.nuwuman.fateubw.lancer.GaeBolgItem;
import com.nuwuman.fateubw.lancer.GaeBolgSpearEntity;
import com.nuwuman.fateubw.lancer.LancerArmorItem;
import com.nuwuman.fateubw.rider.BellerophonItem;
import com.nuwuman.fateubw.rider.ChainDaggerEntity;
import com.nuwuman.fateubw.rider.PegasusEntity;
import com.nuwuman.fateubw.rider.RiderArmorItem;
import com.nuwuman.fateubw.rider.RiderDaggerItem;
import com.nuwuman.fateubw.saber.ExcaliburBeamEntity;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.object.builder.v1.entity.FabricDefaultAttributeRegistry;
import com.nuwuman.fateubw.saber.ExcaliburItem;
import com.nuwuman.fateubw.saber.SaberArmorItem;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.gamerule.v1.GameRuleFactory;
import net.fabricmc.fabric.api.gamerule.v1.GameRuleRegistry;
import net.fabricmc.fabric.api.itemgroup.v1.FabricItemGroup;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.SpawnGroup;
import net.minecraft.entity.damage.DamageType;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ArmorItem;
import net.minecraft.item.ArmorMaterial;
import net.minecraft.item.Item;
import net.minecraft.item.ItemGroup;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.recipe.Ingredient;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;
import net.minecraft.util.Rarity;
import net.minecraft.util.Util;
import net.minecraft.world.GameRules;
import net.minecraft.world.World;

import java.util.EnumMap;
import java.util.List;

public class FateUBW implements ModInitializer {
    public static final String MOD_ID = "fate_ubw";

    public static Identifier id(String path) {
        return Identifier.of(MOD_ID, path);
    }

    // ---------- Saber ----------
    public static final RegistryEntry<ArmorMaterial> SABER_MATERIAL = armorMaterial("saber");

    public static final Item EXCALIBUR = item("excalibur", new ExcaliburItem(new Item.Settings().rarity(Rarity.EPIC).fireproof()));
    public static final Item SABER_CHESTPLATE = item("saber_chestplate", new SaberArmorItem(ArmorItem.Type.CHESTPLATE, new Item.Settings().rarity(Rarity.EPIC)));
    public static final Item SABER_LEGGINGS = item("saber_leggings", new SaberArmorItem(ArmorItem.Type.LEGGINGS, new Item.Settings().rarity(Rarity.EPIC)));
    public static final Item SABER_BOOTS = item("saber_boots", new SaberArmorItem(ArmorItem.Type.BOOTS, new Item.Settings().rarity(Rarity.EPIC)));

    // ---------- Archer ----------
    public static final RegistryEntry<ArmorMaterial> ARCHER_MATERIAL = armorMaterial("archer");

    public static final Item KANSHOU = item("kanshou", new FalchionItem(true, new Item.Settings().rarity(Rarity.EPIC)));
    public static final Item BAKUYA = item("bakuya", new FalchionItem(false, new Item.Settings().rarity(Rarity.EPIC)));
    public static final Item ARCHER_BOW = item("archer_bow", new ArcherBowItem(new Item.Settings().maxDamage(1200).rarity(Rarity.EPIC)));
    public static final Item ARCHER_CHESTPLATE = item("archer_chestplate", new ArcherArmorItem(ArmorItem.Type.CHESTPLATE, new Item.Settings().rarity(Rarity.EPIC)));
    public static final Item ARCHER_LEGGINGS = item("archer_leggings", new ArcherArmorItem(ArmorItem.Type.LEGGINGS, new Item.Settings().rarity(Rarity.EPIC)));
    public static final Item ARCHER_BOOTS = item("archer_boots", new ArcherArmorItem(ArmorItem.Type.BOOTS, new Item.Settings().rarity(Rarity.EPIC)));

    // ---------- Lancer ----------
    public static final RegistryEntry<ArmorMaterial> LANCER_MATERIAL = armorMaterial("lancer");

    public static final Item GAE_BOLG = item("gae_bolg", new GaeBolgItem(new Item.Settings().rarity(Rarity.EPIC).fireproof()));
    public static final Item LANCER_CHESTPLATE = item("lancer_chestplate", new LancerArmorItem(ArmorItem.Type.CHESTPLATE, new Item.Settings().rarity(Rarity.EPIC)));
    public static final Item LANCER_LEGGINGS = item("lancer_leggings", new LancerArmorItem(ArmorItem.Type.LEGGINGS, new Item.Settings().rarity(Rarity.EPIC)));
    public static final Item LANCER_BOOTS = item("lancer_boots", new LancerArmorItem(ArmorItem.Type.BOOTS, new Item.Settings().rarity(Rarity.EPIC)));

    // ---------- Rider ----------
    public static final RegistryEntry<ArmorMaterial> RIDER_MATERIAL = armorMaterial("rider");

    public static final Item RIDER_DAGGER = item("rider_dagger", new RiderDaggerItem(new Item.Settings().rarity(Rarity.EPIC)));
    public static final Item BELLEROPHON = item("bellerophon", new BellerophonItem(new Item.Settings().maxCount(1).rarity(Rarity.EPIC)));
    public static final Item RIDER_HELMET = item("rider_helmet", new RiderArmorItem(ArmorItem.Type.HELMET, new Item.Settings().rarity(Rarity.EPIC)));
    public static final Item RIDER_CHESTPLATE = item("rider_chestplate", new RiderArmorItem(ArmorItem.Type.CHESTPLATE, new Item.Settings().rarity(Rarity.EPIC)));
    public static final Item RIDER_LEGGINGS = item("rider_leggings", new RiderArmorItem(ArmorItem.Type.LEGGINGS, new Item.Settings().rarity(Rarity.EPIC)));
    public static final Item RIDER_BOOTS = item("rider_boots", new RiderArmorItem(ArmorItem.Type.BOOTS, new Item.Settings().rarity(Rarity.EPIC)));

    // ---------- Gilgamesh ----------
    public static final RegistryEntry<ArmorMaterial> GILGAMESH_MATERIAL = armorMaterial("gilgamesh");

    public static final Item GATE_OF_BABYLON = item("gate_of_babylon", new GateOfBabylonItem(new Item.Settings().maxCount(1).rarity(Rarity.EPIC)));
    public static final Item EA = item("ea", new EaItem(new Item.Settings().rarity(Rarity.EPIC).fireproof()));
    public static final Item GILGAMESH_CHESTPLATE = item("gilgamesh_chestplate", new GilgameshArmorItem(ArmorItem.Type.CHESTPLATE, new Item.Settings().rarity(Rarity.EPIC)));
    public static final Item GILGAMESH_LEGGINGS = item("gilgamesh_leggings", new GilgameshArmorItem(ArmorItem.Type.LEGGINGS, new Item.Settings().rarity(Rarity.EPIC)));
    public static final Item GILGAMESH_BOOTS = item("gilgamesh_boots", new GilgameshArmorItem(ArmorItem.Type.BOOTS, new Item.Settings().rarity(Rarity.EPIC)));

    // Sin pestaña: modelos que usan los proyectiles y claves de cooldown de las habilidades
    public static final Item CALADBOLG = item("caladbolg", new Item(new Item.Settings().maxCount(1).rarity(Rarity.EPIC)));
    public static final Item SWORD_ARROW = item("sword_arrow", new Item(new Item.Settings()));
    public static final Item RHO_AIAS = item("rho_aias", new Item(new Item.Settings()));
    public static final Item GAE_BOLG_PIERCE = item("gae_bolg_pierce", new Item(new Item.Settings()));
    public static final Item GAE_BOLG_SOARING = item("gae_bolg_soaring", new Item(new Item.Settings()));
    public static final Item BATTLE_CONTINUATION = item("battle_continuation", new Item(new Item.Settings()));
    public static final Item MYSTIC_EYES = item("mystic_eyes", new Item(new Item.Settings()));
    public static final Item BELLEROPHON_CHARGE = item("bellerophon_charge", new Item(new Item.Settings()));

    // ---------- Entidades ----------
    public static final EntityType<ExcaliburBeamEntity> BEAM = entity("excalibur_beam", ExcaliburBeamEntity::new, 20);
    public static final EntityType<ThrownFalchionEntity> THROWN_FALCHION = entity("thrown_falchion", ThrownFalchionEntity::new, 1);
    public static final EntityType<SwordArrowEntity> SWORD_ARROW_ENTITY = entity("sword_arrow", SwordArrowEntity::new, 20);
    public static final EntityType<RhoAiasEntity> RHO_AIAS_ENTITY = entity("rho_aias", RhoAiasEntity::new, 1);
    public static final EntityType<GaeBolgSpearEntity> GAE_BOLG_SPEAR = entity("gae_bolg_spear", GaeBolgSpearEntity::new, 1);
    public static final EntityType<ChainDaggerEntity> CHAIN_DAGGER = entity("chain_dagger", ChainDaggerEntity::new, 1);
    public static final EntityType<EnumaElishEntity> ENUMA_ELISH = entity("enuma_elish", EnumaElishEntity::new, 20);
    public static final EntityType<BabylonPortalEntity> BABYLON_PORTAL = entity("babylon_portal", BabylonPortalEntity::new, 1);
    public static final EntityType<BabylonWeaponEntity> BABYLON_WEAPON = entity("babylon_weapon", BabylonWeaponEntity::new, 20);
    public static final EntityType<PegasusEntity> PEGASUS = Registry.register(Registries.ENTITY_TYPE, id("pegasus"),
            EntityType.Builder.create(PegasusEntity::new, SpawnGroup.MISC)
                    .dimensions(1.4F, 1.6F)
                    .passengerAttachments(1.35F)
                    .maxTrackingRange(10)
                    .disableSaving()
                    .build("pegasus"));

    // Tipos de daño propios (data/fate_ubw/damage_type), ignoran armadura
    public static final RegistryKey<DamageType> EXCALIBUR_DAMAGE = RegistryKey.of(RegistryKeys.DAMAGE_TYPE, id("excalibur"));
    public static final RegistryKey<DamageType> GAE_BOLG_DAMAGE = RegistryKey.of(RegistryKeys.DAMAGE_TYPE, id("gae_bolg"));
    public static final RegistryKey<DamageType> BELLEROPHON_DAMAGE = RegistryKey.of(RegistryKeys.DAMAGE_TYPE, id("bellerophon"));
    public static final RegistryKey<DamageType> ENUMA_ELISH_DAMAGE = RegistryKey.of(RegistryKeys.DAMAGE_TYPE, id("enuma_elish"));

    // /gamerule fateAbilitiesBreakBlocks true → Excalibur abre un túnel y Caladbolg explota como TNT
    public static final GameRules.Key<GameRules.BooleanRule> BREAK_BLOCKS = GameRuleRegistry.register(
            "fateAbilitiesBreakBlocks", GameRules.Category.MISC, GameRuleFactory.createBooleanRule(false));

    public static boolean breaksBlocks(World world) {
        return world.getGameRules().getBoolean(BREAK_BLOCKS);
    }

    public static final ItemGroup GROUP = Registry.register(Registries.ITEM_GROUP, id("main"), FabricItemGroup.builder()
            .icon(() -> new ItemStack(EXCALIBUR))
            .displayName(Text.translatable("itemGroup.fate_ubw"))
            .entries((context, entries) -> {
                entries.add(EXCALIBUR);
                entries.add(SABER_CHESTPLATE);
                entries.add(SABER_LEGGINGS);
                entries.add(SABER_BOOTS);
                entries.add(KANSHOU);
                entries.add(BAKUYA);
                entries.add(ARCHER_BOW);
                entries.add(ARCHER_CHESTPLATE);
                entries.add(ARCHER_LEGGINGS);
                entries.add(ARCHER_BOOTS);
                entries.add(GAE_BOLG);
                entries.add(LANCER_CHESTPLATE);
                entries.add(LANCER_LEGGINGS);
                entries.add(LANCER_BOOTS);
                entries.add(RIDER_DAGGER);
                entries.add(BELLEROPHON);
                entries.add(RIDER_HELMET);
                entries.add(RIDER_CHESTPLATE);
                entries.add(RIDER_LEGGINGS);
                entries.add(RIDER_BOOTS);
                entries.add(GATE_OF_BABYLON);
                entries.add(EA);
                entries.add(GILGAMESH_CHESTPLATE);
                entries.add(GILGAMESH_LEGGINGS);
                entries.add(GILGAMESH_BOOTS);
            })
            .build());

    private static Item item(String name, Item item) {
        return Registry.register(Registries.ITEM, id(name), item);
    }

    // Material de armadura de servant (protección de netherita); la textura real la pone GeckoLib
    private static RegistryEntry<ArmorMaterial> armorMaterial(String name) {
        return Registry.registerReference(Registries.ARMOR_MATERIAL, id(name),
                new ArmorMaterial(Util.make(new EnumMap<>(ArmorItem.Type.class), map -> {
                    map.put(ArmorItem.Type.BOOTS, 3);
                    map.put(ArmorItem.Type.LEGGINGS, 6);
                    map.put(ArmorItem.Type.CHESTPLATE, 8);
                    map.put(ArmorItem.Type.HELMET, 3);
                    map.put(ArmorItem.Type.BODY, 11);
                }), 15, SoundEvents.ITEM_ARMOR_EQUIP_NETHERITE, () -> Ingredient.ofItems(Items.NETHERITE_INGOT),
                        List.of(new ArmorMaterial.Layer(id(name))), 3.0F, 0.1F));
    }

    private static <T extends Entity> EntityType<T> entity(String name, EntityType.EntityFactory<T> factory, int trackingInterval) {
        return Registry.register(Registries.ENTITY_TYPE, id(name), EntityType.Builder.create(factory, SpawnGroup.MISC)
                .dimensions(0.5F, 0.5F)
                .maxTrackingRange(16)
                .trackingTickInterval(trackingInterval)
                .disableSaving()
                .build(name));
    }

    // Aviso en la barra de acción con los segundos que faltan
    public static void cooldownMessage(PlayerEntity player, Item key, int totalTicks, String ability) {
        float left = player.getItemCooldownManager().getCooldownProgress(key, 0.0F);
        int seconds = (int) Math.ceil(left * totalTicks / 20.0F);
        player.sendMessage(Text.translatable("message.fate_ubw.cooldown",
                Text.translatable("ability.fate_ubw." + ability), seconds).formatted(Formatting.RED), true);
    }

    @Override
    public void onInitialize() {
        // Los registros se hacen al cargar la clase (campos estáticos). Aquí solo los eventos.
        ServerLivingEntityEvents.ALLOW_DAMAGE.register(LancerArmorItem::allowDamage);
        ServerLivingEntityEvents.ALLOW_DAMAGE.register(SaberArmorItem::allowDamage);
        ServerLivingEntityEvents.ALLOW_DEATH.register(LancerArmorItem::allowDeath);
        FabricDefaultAttributeRegistry.register(PEGASUS, PegasusEntity.createAttributes());
    }
}
