package dev.px.karatsuba.client.gui.apple.shell.header;

import dev.px.core.input.MouseButton;
import dev.px.gui.Widget;
import dev.px.karatsuba.client.gui.apple.shell.rail.Glyph;
import dev.px.karatsuba.client.gui.apple.shell.style.Motion;

import java.util.function.BooleanSupplier;

/** ‹ or › in the header: one step back or forward in the history, dimmed when there is nowhere to go. */
public final class NavButton extends Widget {

    private final Glyph glyph;
    private final Runnable action;
    private final Motion hover = Motion.hover();

    public NavButton(Glyph glyph, String label, BooleanSupplier possible, Runnable action) {
        this.glyph = glyph;
        this.action = action;
        enabledWhen(possible);
        tooltip(label);
    }

    public Glyph glyph() {
        return glyph;
    }

    public float hover() {
        return hover.toward(isHovered() && isEnabled());
    }

    @Override
    protected boolean mousePressed(float x, float y, MouseButton button) {
        if (button != MouseButton.LEFT) {
            return false;
        }
        action.run();
        return true;
    }

    @Override
    protected boolean activated() {
        action.run();
        return true;
    }

    @Override
    public boolean isFocusable() {
        return true;
    }
}
