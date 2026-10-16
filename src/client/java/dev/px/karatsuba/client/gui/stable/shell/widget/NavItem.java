package dev.px.karatsuba.client.gui.stable.shell.widget;

import dev.px.core.input.MouseButton;
import dev.px.gui.Widget;
import dev.px.karatsuba.client.gui.stable.shell.style.Motion;

import java.util.function.BooleanSupplier;
import java.util.function.Supplier;

/**
 * An entry in a section's own list of pages: a category of modules, a page of
 * settings. Its name, a small caption on the right (a count), and when chosen a
 * raised background with a bar in the accent, easing in as the choice moves.
 */
public final class NavItem extends Widget {

    private final String name;
    private final Supplier<String> caption;
    private final BooleanSupplier selected;
    private final Runnable select;

    private final Motion hover = Motion.hover();
    private final Motion chosen;

    public NavItem(String name, Supplier<String> caption, BooleanSupplier selected, Runnable select) {
        this.name = name;
        this.caption = caption == null ? () -> "" : caption;
        this.selected = selected;
        this.select = select;
        this.chosen = Motion.toggle(selected.getAsBoolean());
    }

    public String name() {
        return name;
    }

    public String caption() {
        return caption.get();
    }

    public boolean isSelected() {
        return selected.getAsBoolean();
    }

    public float hover() {
        return hover.toward(isHovered() && !isSelected());
    }

    public float chosen() {
        return chosen.toward(isSelected());
    }

    @Override
    protected boolean mousePressed(float x, float y, MouseButton button) {
        if (button != MouseButton.LEFT) {
            return false;
        }
        select.run();
        return true;
    }

    @Override
    protected boolean activated() {
        select.run();
        return true;
    }

    @Override
    public boolean isFocusable() {
        return true;
    }
}
