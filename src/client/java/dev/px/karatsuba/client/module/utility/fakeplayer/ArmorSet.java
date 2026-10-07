package dev.px.karatsuba.client.module.utility.fakeplayer;

import dev.px.karatsuba.adapter.game.item.Equipment;
import dev.px.karatsuba.adapter.game.item.ItemSpec;

/**
 * The armour a fake player can wear, fully enchanted the way a PvP kit would be.
 *
 * <p>Data, not behaviour: each set names its material and every piece takes the
 * same enchantments, plus the helmet and boots extras. Adding a set is a constant.
 */
public enum ArmorSet {

    NETHERITE("netherite"),
    DIAMOND("diamond"),
    NONE(null);

    private final String material;

    ArmorSet(String material) {
        this.material = material;
    }

    /** @return the piece for an armour slot, or {@link ItemSpec#NONE} for a bare set or a hand slot */
    public ItemSpec pieceFor(Equipment.Slot slot) {
        if (material == null) {
            return ItemSpec.NONE;
        }
        return switch (slot) {
            case HEAD -> enchanted(material + "_helmet")
                    .enchant("respiration", 3)
                    .enchant("aqua_affinity", 1);
            case CHEST -> enchanted(material + "_chestplate");
            case LEGS -> enchanted(material + "_leggings");
            case FEET -> enchanted(material + "_boots")
                    .enchant("feather_falling", 4)
                    .enchant("depth_strider", 3)
                    .enchant("soul_speed", 3);
            case MAIN_HAND, OFF_HAND -> ItemSpec.NONE;
        };
    }

    /** @return the material weapons should match: diamond for diamond armour, otherwise netherite */
    public String weaponMaterial() {
        return this == DIAMOND ? "diamond" : "netherite";
    }

    private static ItemSpec enchanted(String itemId) {
        return ItemSpec.of(itemId)
                .enchant("protection", 4)
                .enchant("unbreaking", 3)
                .enchant("mending", 1);
    }
}
