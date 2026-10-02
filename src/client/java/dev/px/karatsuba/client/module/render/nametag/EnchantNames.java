package dev.px.karatsuba.client.module.render.nametag;

import dev.px.karatsuba.adapter.game.item.GameItem;

/**
 * The short enchantment labels a name tag has room for: "Pro4", "Sha5", "Mend1".
 *
 * <p>Leapfrog's rules, kept exactly: the first three letters and the level, or
 * the first two when the level is above 1 so the label stays the same width; and
 * the two curses spelled so they cannot be mistaken for anything else.
 */
public final class EnchantNames {

    private EnchantNames() {
    }

    public static String shortName(GameItem.Enchant enchant) {
        String name = enchant.getName();
        if (name.contains("Vanish")) {
            return "Van";
        }
        if (name.contains("Bind")) {
            return "Bind";
        }
        int keep = enchant.getLevel() > 1 ? 2 : 3;
        return (name.length() > keep ? name.substring(0, keep) : name) + enchant.getLevel();
    }
}
