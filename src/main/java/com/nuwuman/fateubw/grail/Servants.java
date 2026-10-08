package com.nuwuman.fateubw.grail;

import com.nuwuman.fateubw.FateUBW;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.util.math.random.Random;

import java.util.List;

/** Los ocho servants: su equipo completo y el catalizador que los atrae en la invocación. */
public final class Servants {
    public record Servant(String id, Item catalyst, List<Item> armor, List<Item> weapons) {
        public void equip(PlayerEntity player) {
            for (Item piece : armor) {
                ItemStack stack = new ItemStack(piece);
                EquipmentSlot slot = player.getPreferredEquipmentSlot(stack);
                if (player.getEquippedStack(slot).isEmpty()) player.equipStack(slot, stack);
                else player.getInventory().offerOrDrop(stack);
            }
            for (Item weapon : weapons) player.getInventory().offerOrDrop(new ItemStack(weapon));
        }
    }

    private static List<Servant> all;

    private Servants() {
    }

    public static List<Servant> all() {
        if (all == null) {
            all = List.of(
                    new Servant("saber", Items.GOLDEN_APPLE,
                            List.of(FateUBW.SABER_CHESTPLATE, FateUBW.SABER_LEGGINGS, FateUBW.SABER_BOOTS), List.of(FateUBW.EXCALIBUR)),
                    new Servant("archer", Items.RED_DYE,
                            List.of(FateUBW.ARCHER_CHESTPLATE, FateUBW.ARCHER_LEGGINGS, FateUBW.ARCHER_BOOTS),
                            List.of(FateUBW.KANSHOU, FateUBW.BAKUYA, FateUBW.ARCHER_BOW)),
                    new Servant("lancer", Items.PRISMARINE_SHARD,
                            List.of(FateUBW.LANCER_CHESTPLATE, FateUBW.LANCER_LEGGINGS, FateUBW.LANCER_BOOTS), List.of(FateUBW.GAE_BOLG)),
                    new Servant("rider", Items.ENDER_EYE,
                            List.of(FateUBW.RIDER_HELMET, FateUBW.RIDER_CHESTPLATE, FateUBW.RIDER_LEGGINGS, FateUBW.RIDER_BOOTS),
                            List.of(FateUBW.RIDER_DAGGER)),
                    new Servant("gilgamesh", Items.GOLD_BLOCK,
                            List.of(FateUBW.GILGAMESH_CHESTPLATE, FateUBW.GILGAMESH_LEGGINGS, FateUBW.GILGAMESH_BOOTS), List.of(FateUBW.EA)),
                    new Servant("caster", Items.AMETHYST_SHARD,
                            List.of(FateUBW.CASTER_CHESTPLATE, FateUBW.CASTER_LEGGINGS, FateUBW.CASTER_BOOTS), List.of(FateUBW.RULE_BREAKER)),
                    new Servant("assassin", Items.FEATHER,
                            List.of(FateUBW.ASSASSIN_CHESTPLATE, FateUBW.ASSASSIN_LEGGINGS, FateUBW.ASSASSIN_BOOTS), List.of(FateUBW.MONOHOSHIZAO)),
                    new Servant("berserker", Items.LEATHER,
                            List.of(FateUBW.BERSERKER_CHESTPLATE, FateUBW.BERSERKER_LEGGINGS, FateUBW.BERSERKER_BOOTS),
                            List.of(FateUBW.BERSERKER_AXE_SWORD)));
        }
        return all;
    }

    /** El servant que atrae ese catalizador, o uno al azar si no es ninguno. */
    public static Servant byCatalyst(ItemStack catalyst, Random random) {
        for (Servant servant : all()) {
            if (catalyst.isOf(servant.catalyst())) return servant;
        }
        return all().get(random.nextInt(all().size()));
    }

    public static boolean isCatalyst(ItemStack stack) {
        return all().stream().anyMatch(s -> stack.isOf(s.catalyst()));
    }
}
