package dev.px.karatsuba.client.gui.shell.section.friends;

import dev.px.core.input.MouseButton;
import dev.px.gui.Widget;
import dev.px.karatsuba.client.gui.shell.style.Animations;

import java.util.function.Supplier;

/**
 * "Removed Steve · Undo", at the foot of the list for a few seconds after a
 * removal, and held while the cursor rests on it. One at a time: a second
 * removal replaces the first. Ctrl+Z does the same.
 */
public final class UndoBar extends Widget {

    /** How long the bar stays, unless the cursor is on it. */
    private static final float SHOWN_MS = 6000f;

    private final Supplier<String> message;
    private final Runnable undo;
    private long shownAt;

    UndoBar(Supplier<String> message, Runnable undo) {
        this.message = message;
        this.undo = undo;
        visibleWhen(this::showing);
    }

    void show() {
        shownAt = System.nanoTime();
    }

    void hide() {
        shownAt = 0L;
    }

    boolean showing() {
        if (shownAt == 0L) {
            return false;
        }
        if (isHovered()) {
            // Resting on it keeps it: reading it should not lose the chance to act on it.
            shownAt = System.nanoTime();
        }
        return Animations.progress(shownAt, SHOWN_MS) < 1f;
    }

    public String message() {
        return message.get();
    }

    /** @return how far it has slid in, eased, 0 to 1 */
    public float arrival() {
        if (!Animations.enabled()) {
            return 1f;
        }
        return Math.min(1f, Animations.progress(shownAt, Animations.POPUP));
    }

    @Override
    protected boolean mousePressed(float x, float y, MouseButton button) {
        var action = part("undo");
        if (button == MouseButton.LEFT && action != null && action.contains(x, y)) {
            undo.run();
        }
        return true;
    }
}
