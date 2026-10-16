package dev.px.karatsuba.client.gui.stable.shell.section.modules;

import dev.px.core.module.category.Category;

/** What a category tab asks of the Modules section, and tells it. */
public interface Navigator {

    /** @return whether {@code category} is the one showing (and nothing is being searched) */
    boolean isShowing(Category category);

    int enabledIn(Category category);

    void showCategory(Category category);
}
