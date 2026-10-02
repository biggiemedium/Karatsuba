package dev.px.karatsuba.adapter.event.game.render;

import dev.px.core.event.Event;
import lombok.Getter;

/**
 * The brightness the lightmap is about to be built with, with a chance to change it.
 *
 * <p>Posted once a frame, where the lightmap reads the player's gamma setting.
 * Changing it here changes what is drawn and nothing else: the option itself is
 * never written, so there is nothing to restore when a module stops and nothing
 * that can leak into {@code options.txt}. That is the difference from borrowing
 * the option through {@code adapter.game.Options}, which owes the value back.
 *
 * <p>The vanilla slider runs 0 to 1. Values above that extrapolate past "Bright",
 * which is what a fullbright is.
 *
 * <p>When two handlers both set it, the last to run wins, in the bus's ordinary
 * priority order.
 */
@Getter
public final class GammaEvent extends Event {

    /** The gamma the lightmap will use. Starts as the player's own setting. */
    private float gamma;

    public GammaEvent(float gamma) {
        this.gamma = gamma;
    }

    /** Replaces the gamma for this frame. Negative values are clamped to 0. */
    public void setGamma(float replacement) {
        this.gamma = Math.max(0f, replacement);
    }
}
