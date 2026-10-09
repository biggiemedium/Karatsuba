package dev.px.karatsuba.client.gui.shell.frame;

import dev.px.karatsuba.client.gui.shell.style.Animations;
import dev.px.karatsuba.client.gui.shell.style.Easing;

/**
 * New content arriving in one region of the window: it fades in from the
 * panel's colour and rises a few pixels into place, decelerating.
 *
 * <p>Shared by the column that rises ({@link RisingColumn}) and the {@link Veil}
 * that fades off it, so the two stay in step. Restarting it mid-way starts the
 * arrival again, which is what changing module twice quickly should look like.
 */
public final class Reveal {

    private long started;

    /** Starts the arrival now. */
    public void start() {
        started = System.nanoTime();
    }

    /** @return how far the arrival is, eased, 0 just started to 1 done */
    public float progress() {
        if (started == 0L || !Animations.enabled()) {
            return 1f;
        }
        return Easing.DECELERATE.apply(Animations.progress(started, Animations.VIEW));
    }

    /** @return whether there is still anything to draw for it */
    public boolean active() {
        return progress() < 1f;
    }
}
