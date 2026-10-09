package dev.px.karatsuba.client.gui.shell.widget;

import dev.px.core.input.MouseButton;
import dev.px.gui.Widget;

import java.util.function.BooleanSupplier;

/** A small text-only action, lit in the accent while {@code active}: a filter, a "Clear". */
public final class TextButton extends Widget {

    private final String label;
    private final BooleanSupplier active;
    private final Runnable action;

    public TextButton(String label, BooleanSupplier active, Runnable action) {
        this.label = label;
        this.active = active;
        this.action = action;
    }

    public String label() {
        return label;
    }

    public boolean isActive() {
        return active.getAsBoolean();
    }

    @Override
    protected boolean mousePressed(float x, float y, MouseButton button) {
        if (button != MouseButton.LEFT) {
            return false;
        }
        action.run();
        return true;
    }
}
