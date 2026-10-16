package dev.px.karatsuba.client.gui.apple.shell.frame;

import dev.px.karatsuba.client.gui.apple.shell.style.Animations;
import dev.px.karatsuba.client.gui.apple.shell.style.Easing;
import dev.px.karatsuba.client.gui.apple.shell.style.Motion;
import dev.px.karatsuba.client.gui.apple.shell.style.Palette;
import dev.px.gui.container.Scroll;

/**
 * A scrolling list whose wheel glides rather than jumps: each notch moves it
 * over 150ms, easing out, and a notch mid-glide carries on from wherever it is
 * rather than queueing. Dragging the bar is direct, as it should be.
 *
 * <p>Its bar is an overlay ({@code gui/apple/DESIGN.md}, PLAN.md step 8b): hidden at
 * rest, shown while the list moves however it moves (wheel, drag, a jump), and
 * fading a moment after it stops.
 */
public class SmoothScroll extends Scroll {

    /** How far one notch of the wheel goes, unless set. */
    private float step = Palette.SCROLL_STEP;

    private float from;
    private float target;
    private long started;

    private final Motion bar = new Motion(Animations.SCROLLBAR_IN, Animations.SCROLLBAR_OUT, Easing.STANDARD, 0f);
    private float lastY = Float.NaN;
    private long movedAt;

    /** Sets how far one notch of the wheel goes: {@link Palette#SCROLL_STEP_FINE} for long lists read row by row. */
    public SmoothScroll step(float notch) {
        this.step = notch;
        return this;
    }

    @Override
    protected boolean mouseScrolled(float x, float y, float amount) {
        if (!canScroll()) {
            return false;
        }
        if (!Animations.enabled()) {
            scrollBy(-amount * step);
            return true;
        }
        float base = gliding() ? target : getScrollY();
        from = getScrollY();
        target = Math.max(0f, Math.min(getMaxScroll(), base - amount * step));
        started = System.nanoTime();
        return true;
    }

    /** Moves the glide on, and notes whether the list moved; once a frame, from the renderer. */
    public void tick() {
        float y = getScrollY();
        if (y != lastY) {
            if (!Float.isNaN(lastY)) {
                movedAt = System.nanoTime();
            }
            lastY = y;
        }
        if (!gliding()) {
            return;
        }
        float progress = Animations.progress(started, Animations.SCROLL);
        scrollTo(from + (target - from) * Easing.DECELERATE.apply(progress));
        if (progress >= 1f) {
            started = 0L;
        }
    }

    /** @return how much the scroll bar shows, 0 hidden at rest to 1 while the list moves or the bar is held */
    public float scrollbar() {
        boolean moving = movedAt != 0L
                && (System.nanoTime() - movedAt) / 1_000_000L < Animations.SCROLLBAR_HOLD_MS;
        return bar.toward(moving || isPressed());
    }

    private boolean gliding() {
        return started != 0L;
    }
}
