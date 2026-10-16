package dev.px.karatsuba.client.gui.apple.shell.card;

import dev.px.core.input.MouseButton;
import dev.px.gui.Widget;
import dev.px.karatsuba.client.gui.apple.shell.style.Motion;

import java.util.function.BooleanSupplier;
import java.util.function.Supplier;

/**
 * The few words above a card, saying what is in it ("Behaviour", "Targets"),
 * and optionally a trailing action at the far end, as Tahoe's "Show All (36)":
 * secondary text that brightens and underlines while the cursor is on it.
 */
public final class CardHeader extends Widget {

    private final String text;
    private Supplier<String> action;
    private Runnable run;
    private final Motion hover = Motion.hover();

    public CardHeader(String text) {
        this.text = text;
    }

    /** Shows only while {@code condition} holds, as its card does. */
    public CardHeader when(BooleanSupplier condition) {
        visibleWhen(condition);
        return this;
    }

    /** Adds a trailing action, its words read live: "Show All (9)". */
    public CardHeader action(Supplier<String> label, Runnable action) {
        this.action = label;
        this.run = action;
        return this;
    }

    public String text() {
        return text;
    }

    /** @return the trailing action's words, or null for none */
    public String action() {
        return action == null ? null : action.get();
    }

    /** @return how lit the action is, 0 to 1: the cursor on it */
    public float actionHover() {
        var part = part("action");
        boolean over = part != null && isHovered()
                && part.contains(dev.px.core.Core.platform().getMouseX(), dev.px.core.Core.platform().getMouseY());
        return hover.toward(over);
    }

    @Override
    protected boolean mousePressed(float x, float y, MouseButton button) {
        var part = part("action");
        if (run == null || button != MouseButton.LEFT || part == null || !part.contains(x, y)) {
            return false;
        }
        run.run();
        return true;
    }
}
