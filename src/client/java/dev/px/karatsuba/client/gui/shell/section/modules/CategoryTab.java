package dev.px.karatsuba.client.gui.shell.section.modules;

import dev.px.core.input.MouseButton;
import dev.px.core.module.category.Category;
import dev.px.gui.Widget;
import dev.px.karatsuba.client.gui.shell.style.Motion;

/** A category in the sidebar: shows its modules when pressed, with a count of those switched on. */
public final class CategoryTab extends Widget {

    private final Category category;
    private final Navigator navigator;

    private final Motion hover = Motion.hover();
    private final Motion selected;

    public CategoryTab(Category category, Navigator navigator) {
        this.category = category;
        this.navigator = navigator;
        this.selected = Motion.toggle(navigator.isShowing(category));
    }

    public Category category() {
        return category;
    }

    public boolean isSelected() {
        return navigator.isShowing(category);
    }

    public int enabledCount() {
        return navigator.enabledIn(category);
    }

    /** @return the hover tint, 0 to 1; none on the selected tab */
    public float hover() {
        return hover.toward(isHovered() && !isSelected());
    }

    /** @return how selected it looks, 0 to 1, easing as the selection moves */
    public float selection() {
        return selected.toward(isSelected());
    }

    @Override
    protected boolean mousePressed(float x, float y, MouseButton button) {
        if (button != MouseButton.LEFT) {
            return false;
        }
        navigator.showCategory(category);
        return true;
    }

    @Override
    protected boolean activated() {
        navigator.showCategory(category);
        return true;
    }

    @Override
    public boolean isFocusable() {
        return true;
    }
}
