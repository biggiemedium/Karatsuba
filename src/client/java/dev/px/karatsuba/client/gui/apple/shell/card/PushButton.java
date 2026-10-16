package dev.px.karatsuba.client.gui.apple.shell.card;

import dev.px.core.input.MouseButton;
import dev.px.gui.Widget;

import java.util.function.Supplier;

/**
 * A push button: a bright filled face with a hairline edge and a soft shadow,
 * shorter than a row ({@code gui/apple/DESIGN.md}, PLAN.md step 8b). A button
 * that opens something says so with "…" at the end of its label.
 */
public final class PushButton extends Widget {

    private final Supplier<String> label;
    private final Runnable action;

    public PushButton(String label, Runnable action) {
        this(() -> label, action);
    }

    /** A button whose words change: one asking to confirm, say. */
    public PushButton(Supplier<String> label, Runnable action) {
        this.label = label;
        this.action = action;
    }

    public String label() {
        return label.get();
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
