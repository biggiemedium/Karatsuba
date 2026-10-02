package dev.px.karatsuba.client.module.misc.fakeplayer;

import dev.px.karatsuba.adapter.game.item.ItemSpec;

/** What a fake player holds in its main hand. */
public enum HeldItem {

    ENCHANTED_GAPPLE,
    GAPPLE,
    END_CRYSTAL,
    SWORD,
    BOW,
    CROSSBOW,
    SHIELD,
    CHORUS_FRUIT,
    NOTHING;

    /**
     * @param armor the set being worn, so a sword matches it
     * @return the item to hold
     */
    public ItemSpec spec(ArmorSet armor) {
        return switch (this) {
            case ENCHANTED_GAPPLE -> ItemSpec.of("enchanted_golden_apple").count(64);
            case GAPPLE -> ItemSpec.of("golden_apple").count(64);
            case END_CRYSTAL -> ItemSpec.of("end_crystal").count(64);
            case SWORD -> ItemSpec.of(armor.weaponMaterial() + "_sword")
                    .enchant("sharpness", 5)
                    .enchant("unbreaking", 3)
                    .enchant("mending", 1)
                    .enchant("sweeping_edge", 3)
                    .enchant("looting", 3);
            case BOW -> ItemSpec.of("bow")
                    .enchant("power", 5)
                    .enchant("punch", 2)
                    .enchant("flame", 1)
                    .enchant("infinity", 1)
                    .enchant("unbreaking", 3);
            case CROSSBOW -> ItemSpec.of("crossbow")
                    .enchant("quick_charge", 3)
                    .enchant("multishot", 1)
                    .enchant("piercing", 4)
                    .enchant("unbreaking", 3);
            case SHIELD -> ItemSpec.of("shield");
            case CHORUS_FRUIT -> ItemSpec.of("chorus_fruit").count(64);
            case NOTHING -> ItemSpec.NONE;
        };
    }
}
