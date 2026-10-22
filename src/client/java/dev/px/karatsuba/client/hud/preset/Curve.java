package dev.px.karatsuba.client.hud.preset;

import dev.px.core.render.animation.Easing;

/**
 * How a motion moves from 0 to 1 over its time: one of Core's easings, or the
 * Apple preset's spring ({@code hud/DESIGN.md} §4.12), which Core does not have.
 */
@FunctionalInterface
public interface Curve {

    /** @return how far along, for {@code t} 0 to 1 of the time; may pass 1 a little for a spring */
    float apply(float t);

    /** @return Core's {@code easing} as a curve */
    static Curve of(Easing easing) {
        return easing::apply;
    }

    /**
     * A soft, gel-like spring: an under-damped oscillator (damping 0.78) that
     * overshoots by about 2%, the Apple rules' limit, and is settled by the end of
     * its time. Core's BACK_OUT overshoots about ten times as far.
     */
    Curve SPRING = t -> {
        if (t <= 0f) {
            return 0f;
        }
        if (t >= 1f) {
            return 1f;
        }
        double zeta = 0.78d;
        double omega = 7.7d;
        double damped = omega * Math.sqrt(1d - zeta * zeta);
        double decay = Math.exp(-zeta * omega * t);
        return (float) (1d - decay * (Math.cos(damped * t) + zeta / Math.sqrt(1d - zeta * zeta) * Math.sin(damped * t)));
    };
}
