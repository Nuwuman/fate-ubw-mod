package com.nuwuman.fateubw.ability;

import com.nuwuman.fateubw.FateUBW;
import com.nuwuman.fateubw.ServantArmorItem;
import com.nuwuman.fateubw.archer.TraceOn;
import com.nuwuman.fateubw.archer.UnlimitedBladeWorks;
import com.nuwuman.fateubw.assassin.AssassinArmorItem;
import com.nuwuman.fateubw.berserker.BerserkerArmorItem;
import com.nuwuman.fateubw.caster.CasterArmorItem;
import com.nuwuman.fateubw.gilgamesh.GilgameshArmorItem;
import com.nuwuman.fateubw.ishtar.IshtarArmorItem;
import com.nuwuman.fateubw.lancer.LancerArmorItem;
import com.nuwuman.fateubw.rider.BellerophonItem;
import com.nuwuman.fateubw.rider.PegasusEntity;
import com.nuwuman.fateubw.rider.RiderDaggerItem;
import com.nuwuman.fateubw.saber.SaberArmorItem;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.Items;
import net.minecraft.server.network.ServerPlayerEntity;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Las habilidades que no son de un arma salen del conjunto de ropa del servant (peto, grebas y botas; Rider también
 * la venda). El HUD las muestra en una esquina; una tecla usa la seleccionada y otra cambia de habilidad.
 */
public final class Abilities {
    private record ServantSet(Item chest, Item legs, Item feet, @Nullable Item head, List<Ability> abilities) {
        boolean worn(PlayerEntity player) {
            return ServantArmorItem.wearsSet(player, chest, legs, feet)
                    && (head == null || player.getEquippedStack(EquipmentSlot.HEAD).isOf(head));
        }
    }

    // Duración de cada cooldown, para el aviso y para los segundos del HUD
    private static final Map<Item, Integer> COOLDOWNS = new HashMap<>();
    @Nullable
    private static List<ServantSet> sets;

    private Abilities() {
    }

    public static void register() {
        cooldown(FateUBW.TRACE_ON, TraceOn.COOLDOWN);
        cooldown(FateUBW.UBW_COOLDOWN, UnlimitedBladeWorks.MARBLE_COOLDOWN);
        cooldown(FateUBW.UBW_BARRAGE, UnlimitedBladeWorks.BARRAGE_COOLDOWN);
        cooldown(FateUBW.AVALON, SaberArmorItem.AVALON_COOLDOWN);
        cooldown(FateUBW.MANA_BURST, SaberArmorItem.MANA_BURST_COOLDOWN);
        cooldown(FateUBW.ANSUZ, LancerArmorItem.ANSUZ_COOLDOWN);
        cooldown(FateUBW.MYSTIC_EYES, RiderDaggerItem.MYSTIC_EYES_COOLDOWN);
        cooldown(FateUBW.BELLEROPHON, BellerophonItem.SUMMON_COOLDOWN);
        cooldown(FateUBW.BELLEROPHON_CHARGE, BellerophonItem.CHARGE_COOLDOWN);
        cooldown(FateUBW.GATE_OF_BABYLON, GilgameshArmorItem.GATE_COOLDOWN);
        cooldown(FateUBW.ENKIDU, GilgameshArmorItem.ENKIDU_COOLDOWN);
        cooldown(FateUBW.DIVINE_WORDS, CasterArmorItem.DIVINE_WORDS_COOLDOWN);
        cooldown(FateUBW.SPATIAL_TRANSFER, CasterArmorItem.SPATIAL_TRANSFER_COOLDOWN);
        cooldown(FateUBW.PRESENCE_CONCEALMENT, AssassinArmorItem.PRESENCE_CONCEALMENT_COOLDOWN);
        cooldown(FateUBW.MAD_ENHANCEMENT, BerserkerArmorItem.MAD_ENHANCEMENT_COOLDOWN);
        cooldown(FateUBW.HRUNTING, 20 * 20);
        cooldown(FateUBW.BLOOD_FORT_ANDROMEDA, com.nuwuman.fateubw.rider.RiderArmorItem.ANDROMEDA_COOLDOWN);
        cooldown(FateUBW.JEWEL_BURST, IshtarArmorItem.JEWEL_BURST_COOLDOWN);
        cooldown(FateUBW.MANIFESTATION_OF_BEAUTY, IshtarArmorItem.BEAUTY_COOLDOWN);
        cooldown(FateUBW.SKY_BOAT, IshtarArmorItem.SKY_BOAT_COOLDOWN);
        cooldown(FateUBW.LORD_CAMELOT_NP, com.nuwuman.fateubw.mash.MashShieldItem.NP_COOLDOWN);
        cooldown(FateUBW.BUNKER_BOLT, com.nuwuman.fateubw.mash.MashArmorItem.BUNKER_BOLT_COOLDOWN);
        cooldown(FateUBW.WALL_OF_SNOWFLAKES, com.nuwuman.fateubw.mash.MashArmorItem.SNOWFLAKES_COOLDOWN);
        cooldown(FateUBW.WALL_OF_CHALK, com.nuwuman.fateubw.mash.MashArmorItem.CHALK_COOLDOWN);

        PayloadTypeRegistry.playC2S().register(UseAbilityPayload.ID, UseAbilityPayload.CODEC);
        ServerPlayNetworking.registerGlobalReceiver(UseAbilityPayload.ID, (payload, context) -> {
            ServerPlayerEntity player = context.player();
            List<Ability> list = of(player);
            if (player.isSpectator() || payload.index() < 0 || payload.index() >= list.size()) return;
            use(player, list.get(payload.index()));
        });
    }

    private static void cooldown(Item key, int ticks) {
        COOLDOWNS.put(key, ticks);
    }

    public static int cooldownOf(Item key) {
        return COOLDOWNS.getOrDefault(key, 0);
    }

    private static List<ServantSet> sets() {
        if (sets == null) {
            sets = List.of(
                    new ServantSet(FateUBW.SABER_CHESTPLATE, FateUBW.SABER_LEGGINGS, FateUBW.SABER_BOOTS, null, List.of(
                            new Ability("avalon", Items.ENCHANTED_GOLDEN_APPLE, FateUBW.AVALON, SaberArmorItem::avalon),
                            new Ability("mana_burst", Items.WIND_CHARGE, FateUBW.MANA_BURST, SaberArmorItem::manaBurst))),
                    new ServantSet(FateUBW.ARCHER_CHESTPLATE, FateUBW.ARCHER_LEGGINGS, FateUBW.ARCHER_BOOTS, null, List.of(
                            new Ability("trace_on", FateUBW.TRACE_ON, FateUBW.TRACE_ON, TraceOn::use),
                            new Ability("unlimited_blade_works", FateUBW.UNLIMITED_BLADE_WORKS,
                                    player -> UnlimitedBladeWorks.isInsideAny(player) ? FateUBW.UBW_BARRAGE : FateUBW.UBW_COOLDOWN,
                                    UnlimitedBladeWorks::useAbility),
                            new Ability("hrunting", FateUBW.HRUNTING, FateUBW.HRUNTING, com.nuwuman.fateubw.archer.HruntingEntity::fire))),
                    new ServantSet(FateUBW.LANCER_CHESTPLATE, FateUBW.LANCER_LEGGINGS, FateUBW.LANCER_BOOTS, null, List.of(
                            new Ability("ansuz", Items.FIRE_CHARGE, FateUBW.ANSUZ, LancerArmorItem::ansuz))),
                    new ServantSet(FateUBW.RIDER_CHESTPLATE, FateUBW.RIDER_LEGGINGS, FateUBW.RIDER_BOOTS, FateUBW.RIDER_HELMET, List.of(
                            new Ability("mystic_eyes", Items.ENDER_EYE, FateUBW.MYSTIC_EYES, RiderDaggerItem::mysticEyes),
                            new Ability("bellerophon", FateUBW.BELLEROPHON,
                                    player -> player.getVehicle() instanceof PegasusEntity ? FateUBW.BELLEROPHON_CHARGE : FateUBW.BELLEROPHON,
                                    BellerophonItem::ability),
                            new Ability("blood_fort_andromeda", Items.REDSTONE, FateUBW.BLOOD_FORT_ANDROMEDA,
                                    com.nuwuman.fateubw.rider.RiderArmorItem::bloodFortAndromeda))),
                    new ServantSet(FateUBW.GILGAMESH_CHESTPLATE, FateUBW.GILGAMESH_LEGGINGS, FateUBW.GILGAMESH_BOOTS, null, List.of(
                            new Ability("gate_of_babylon", FateUBW.GATE_OF_BABYLON, FateUBW.GATE_OF_BABYLON, GilgameshArmorItem::gateOfBabylon),
                            new Ability("enkidu", Items.CHAIN, FateUBW.ENKIDU, GilgameshArmorItem::enkidu))),
                    new ServantSet(FateUBW.CASTER_CHESTPLATE, FateUBW.CASTER_LEGGINGS, FateUBW.CASTER_BOOTS, null, List.of(
                            new Ability("divine_words", Items.AMETHYST_SHARD, FateUBW.DIVINE_WORDS, CasterArmorItem::divineWords),
                            new Ability("spatial_transfer", Items.ENDER_PEARL, FateUBW.SPATIAL_TRANSFER, CasterArmorItem::spatialTransfer))),
                    new ServantSet(FateUBW.ASSASSIN_CHESTPLATE, FateUBW.ASSASSIN_LEGGINGS, FateUBW.ASSASSIN_BOOTS, null, List.of(
                            new Ability("presence_concealment", Items.PHANTOM_MEMBRANE, FateUBW.PRESENCE_CONCEALMENT,
                                    AssassinArmorItem::presenceConcealment))),
                    new ServantSet(FateUBW.BERSERKER_CHESTPLATE, FateUBW.BERSERKER_LEGGINGS, FateUBW.BERSERKER_BOOTS, null, List.of(
                            new Ability("mad_enhancement", Items.BLAZE_POWDER, FateUBW.MAD_ENHANCEMENT, BerserkerArmorItem::madEnhancement))),
                    new ServantSet(FateUBW.ISHTAR_CHESTPLATE, FateUBW.ISHTAR_LEGGINGS, FateUBW.ISHTAR_BOOTS, null, List.of(
                            new Ability("jewel_burst", FateUBW.RIN_JEWEL, FateUBW.JEWEL_BURST, IshtarArmorItem::jewelBurst),
                            new Ability("manifestation_of_beauty", Items.PINK_PETALS, FateUBW.MANIFESTATION_OF_BEAUTY,
                                    IshtarArmorItem::manifestationOfBeauty),
                            new Ability("sky_boat", Items.FEATHER, FateUBW.SKY_BOAT, IshtarArmorItem::skyBoat))),
                    new ServantSet(FateUBW.MASH_CHESTPLATE, FateUBW.MASH_LEGGINGS, FateUBW.MASH_BOOTS, null, List.of(
                            new Ability("bunker_bolt", Items.IRON_CHESTPLATE, FateUBW.BUNKER_BOLT, com.nuwuman.fateubw.mash.MashArmorItem::bunkerBolt),
                            new Ability("wall_of_snowflakes", Items.SNOWBALL, FateUBW.WALL_OF_SNOWFLAKES,
                                    com.nuwuman.fateubw.mash.MashArmorItem::wallOfSnowflakes),
                            new Ability("wall_of_chalk", Items.QUARTZ, FateUBW.WALL_OF_CHALK, com.nuwuman.fateubw.mash.MashArmorItem::wallOfChalk))));
        }
        return sets;
    }

    /** Las habilidades del conjunto que lleva puesto (vacío si no lleva ninguno completo). */
    public static List<Ability> of(PlayerEntity player) {
        for (ServantSet set : sets()) {
            if (set.worn(player)) return set.abilities();
        }
        return List.of();
    }

    /** Usa una habilidad respetando su cooldown y su coste de maná, con aviso si falta alguno. */
    public static void use(ServerPlayerEntity player, Ability ability) {
        Item key = ability.key().apply(player);
        if (player.getItemCooldownManager().isCoolingDown(key)) {
            FateUBW.cooldownMessage(player, key, cooldownOf(key), ability.id());
            return;
        }
        if (!Mana.has(player, key)) return;
        if (ability.action().use(player)) {
            com.nuwuman.fateubw.Rules.cooldown(player, key, cooldownOf(key));
            Mana.spend(player, key);
            // Postura de la habilidad, si tiene (player_animations/<id>.json); si no, no pasa nada
            com.nuwuman.fateubw.PlayerAnims.playAll(player, ability.id());
        }
    }
}
