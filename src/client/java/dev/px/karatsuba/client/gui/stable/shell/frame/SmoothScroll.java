package dev.px.karatsuba.client.gui.stable.shell.frame;

import dev.px.karatsuba.client.gui.stable.shell.style.Animations;
import dev.px.karatsuba.client.gui.stable.shell.style.Easing;
import dev.px.gui.container.Scroll;

/**
 * A scrolling list whose wheel glides rather than jumps: each notch moves it
 * over 150ms, easing out, and a notch mid-glide carries on from wherever it is
 * rather than queueing. Dragging the bar is direct, as it should be.
 */
public final class SmoothScroll extends Scroll {

    /** How far one notch of the wheel goes. */
    private static final float STEP = 28f;

    private float from;
    private float target;
    private long started;

    @Override
    protected boolean mouseScrolled(float x, float y, float amount) {
        if (!canScroll()) {
            return false;
        }
        if (!Animations.enabled()) {
            scrollBy(-amount * STEP);
            return true;
        }
        float base = gliding() ? target : getScrollY();
        from = getScrollY();
        target = Math.max(0f, Math.min(getMaxScroll(), base - amount * STEP));
        started = System.nanoTime();
        return true;
    }

    /** Moves the glide on; once a frame, from the renderer. */
    public void tick() {
        if (!gliding()) {
            return;
        }
        float progress = Animations.progress(started, Animations.SCROLL);
        scrollTo(from + (target - from) * Easing.DECELERATE.apply(progress));
        if (progress >= 1f) {
            started = 0L;
        }
    }

    private boolean gliding() {
        return started != 0L;
    }
}
