package dev.px.karatsuba.client.gui.shell.setting;

import dev.px.core.input.MouseButton;
import dev.px.gui.Widget;
import dev.px.karatsuba.client.gui.shell.style.Motion;

import java.util.function.Supplier;

/**
 * One control as one row: its name on the left, the control on the right.
 *
 * <p>Every row works the same two ways, so nothing has to be learnt per type:
 * the left button uses the control, and the right button puts it back to its
 * default. Most rows edit a {@code Setting} ({@link SettingRow}); a few edit a
 * plain value, such as a HUD element's anchor or scale, and look and behave
 * exactly the same.
 *
 * <p>Rows come in two densities. In a module's or a HUD element's settings they
 * are compact, with the description as a tooltip, so a long list stays short
 * enough to scan. In the Settings section they are {@link #detailed()}: the
 * description sits under the name where it can be read and searched, a row that
 * differs from its default has a bar in the accent at its edge, and a reset
 * button appears beside the control.
 */
public abstract class ControlRow extends Widget {

    private final Motion hover = Motion.hover();
    private boolean detailed;
    private Supplier<String> note = () -> "";

    /** @return the name on the left */
    public abstract String name();

    /** @return what the control does, in a sentence; empty for none */
    public String description() {
        return "";
    }

    /** @return whether the value differs from its default */
    public boolean isModified() {
        return false;
    }

    /** @return the hover tint, 0 to 1 */
    public float hover() {
        return hover.toward(isHovered() || isPressed());
    }

    /** Shows the description under the name, and marks a changed value. For the Settings section. */
    public ControlRow detailed() {
        this.detailed = true;
        // The description is on the row now; a tooltip would only repeat it.
        tooltip((String) null);
        return this;
    }

    public boolean isDetailed() {
        return detailed;
    }

    /** A warning under the name, read live: a keybind that clashes with another, say. Empty for none. */
    public ControlRow note(Supplier<String> source) {
        this.note = source == null ? () -> "" : source;
        return this;
    }

    public String note() {
        return note.get();
    }

    /** @return whether the reset button shows: on a detailed row whose value is not its default */
    public boolean showsReset() {
        return detailed && isModified();
    }

    @Override
    protected boolean mousePressed(float x, float y, MouseButton button) {
        if (button == MouseButton.RIGHT) {
            reset();
            return true;
        }
        if (button == MouseButton.LEFT && showsReset() && inside("reset", x, y)) {
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
