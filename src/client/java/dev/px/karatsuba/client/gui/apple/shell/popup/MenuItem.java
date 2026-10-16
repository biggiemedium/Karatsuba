package dev.px.karatsuba.client.gui.apple.shell.popup;

import dev.px.core.input.MouseButton;
import dev.px.gui.Widget;
import dev.px.karatsuba.client.gui.apple.shell.style.Motion;

import java.util.function.BooleanSupplier;

/**
 * One choice in a {@link Menu}.
 *
 * <p>A single choice shows a tick beside the current one; a multiple choice shows
 * a checkbox on each, and stays open while you tick several.
 */
public final class MenuItem extends Widget {

    private final String label;
    private final BooleanSupplier selected;
    private final boolean checkbox;
    private final Runnable pick;
    private final Motion hover = Motion.hover();
    private final Motion check;

    public MenuItem(String label, BooleanSupplier selected, boolean checkbox, Runnable pick) {
        this.label = label;
        this.selected = selected;
        this.checkbox = checkbox;
        this.pick = pick;
        this.check = Motion.toggle(selected.getAsBoolean());
    }

    public String label() {
        return label;
    }

    public boolean isSelected() {
        return selected.getAsBoolean();
    }

    public boolean isCheckbox() {
        return checkbox;
    }

    public float hover() {
        return hover.toward(isHovered() || isFocused());
    }

    /** @return the checkbox's fill, 0 to 1 */
    public float check() {
        return check.toward(isSelected());
    }

    @Override
    protected boolean mousePressed(float x, float y, MouseButton button) {
        if (button != MouseButton.LEFT) {
            return false;
        }
        pick.run();
        return true;
    }

    @Override
    protected boolean activated() {
        pick.run();
        return true;
    }

    @Override
    public boolean isFocusable() {
        return true;
    }
}
