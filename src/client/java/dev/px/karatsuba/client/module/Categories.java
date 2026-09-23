package dev.px.karatsuba.client.module;

import dev.px.core.Core;
import dev.px.core.module.Category;

/**
 * Karatsuba's module categories.
 *
 * <p>Core declares {@code Category} as an interface rather than an enum precisely
 * so this list lives here and not in the library. An enum keeps the ergonomics,
 * and registering the whole thing is one call.
 *
 * <p>The order decides where a category's window sits in the click GUI. The icon
 * key is never interpreted by Core; it is whatever a GUI of your own resolves.
 */
public enum Categories implements Category {

    COMBAT("Combat", 0, "sword"),
    MOVEMENT("Movement", 1, "movement"),
    RENDER("Render", 2, "eye"),
    PLAYER("Player", 3, "player"),
    UTILITY("Utility", 4, "wrench"),
    EXPLOIT("Exploit", 5, "bug"),
    MISC("Misc", 6, "gear");

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
