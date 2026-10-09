package dev.px.karatsuba.client.gui.shell.setting;

import dev.px.core.input.MouseButton;
import dev.px.gui.Widget;
import dev.px.karatsuba.client.gui.shell.style.Motion;

/**
 * One control as one row: its name on the left, the control on the right.
 *
 * <p>Every row works the same two ways, so nothing has to be learnt per type:
 * the left button uses the control, and the right button puts it back to its
 * default. Most rows edit a {@code Setting} ({@link SettingRow}); a few edit a
 * plain value, such as a HUD element's anchor or scale, and look and behave
 * exactly the same.
 */
public abstract class ControlRow extends Widget {

    private final Motion hover = Motion.hover();

    /** @return the name on the left */
    public abstract String name();

    /** @return the hover tint, 0 to 1 */
    public float hover() {
        return hover.toward(isHovered() || isPressed());
    }

    @Override
    protected boolean mousePressed(float x, float y, MouseButton button) {
        if (button == MouseButton.RIGHT) {
            reset();
            return true;
        }
        return button == MouseButton.LEFT && pressed(x, y);
    }

    /** A left press on the row: use the control. */
    protected abstract boolean pressed(float x, float y);

    /** A right press: back to the default. */
    protected abstract void reset();

    /** @return whether {@code (x, y)} is inside the named part, as the last update laid it out */
    protected final boolean inside(String part, float x, float y) {
        var bounds = part(part);
        return bounds != null && bounds.contains(x, y);
    }
}
