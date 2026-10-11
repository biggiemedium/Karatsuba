package dev.px.karatsuba.client.module;

import dev.px.core.Core;
import dev.px.core.module.category.Category;

/**
 * Karatsuba's module categories.
 *
 * <p>Core declares {@code Category} as an interface rather than an enum precisely
 * so this list lives here and not in the library. An enum keeps the ergonomics,
 * and registering the whole thing is one call.
 *
 * <p>Grouped by what the player is doing, not the old cheat-client drawers; see
 * {@code MODULES.md} beside this file for what belongs where. A category is added
 * here in the same change as its first module, so the click GUI never shows an
 * empty window: Travel and Farming arrive with theirs.
 *
 * <p>The order decides where a category's window sits in the click GUI, and leaves
 * gaps for the categories still to come. The icon key is never interpreted by Core;
 * it is whatever a GUI of your own resolves.
 */
public enum Categories implements Category {

    COMBAT("Combat", 0, "sword"),
    MOVEMENT("Movement", 1, "movement"),
    // Travel (2) is added with its first module.
    BUILDING("Building", 3, "cube"),
    MINING("Mining", 4, "pickaxe"),
    INVENTORY("Inventory", 5, "chest"),
    RENDER("Render", 6, "eye"),
    UTILITY("Utility", 7, "wrench"),
    AUTOMATION("Automation", 8, "robot");

    /**
     * Registers every category.
     *
     * <p>Same signature as every other catalogue, so the boot sequence reads as a
     * list of identical calls and the only thing it still encodes is their order.
     */
    public static void registerAll(Core core) {
        core.getCategories().registerAll(Categories.class);
    }

    private final String name;
    private final int order;
    private final String icon;

    Categories(String name, int order, String icon) {
        this.name = name;
        this.order = order;
        this.icon = icon;
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public int getOrder() {
        return order;
    }

    @Override
    public String getIcon() {
        return icon;
    }
}
