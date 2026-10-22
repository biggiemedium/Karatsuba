package dev.px.karatsuba.client.hud.style;

import dev.px.karatsuba.client.hud.preset.Curve;

/**
 * A value that moves to wherever it is sent along a {@link Curve} over a time, from
 * wherever it is when sent, so it can be redirected mid-way without a jump. Like
 * Core's {@code Animation}, but for any curve: the Apple preset's spring.
 */
final class Tween {

    private float from;
    private float to;
    private long startedAt;
    private long millis;
    private Curve curve = Curve.of(dev.px.core.render.animation.Easing.LINEAR);

    Tween(float initial) {
        this.from = initial;
        this.to = initial;
    }

    /** Sends it to {@code target} over {@code durationMillis} along {@code along}; a target it is already going to changes nothing. */
    void target(float target, long durationMillis, Curve along) {
        if (target == to) {
            return;
        }
        this.from = get();
        this.to = target;
        this.millis = durationMillis;
        this.curve = along;
        this.startedAt = System.currentTimeMillis();
    }

    /** Puts it at {@code value} at once. */
    void snapTo(float value) {
        this.from = value;
        this.to = value;
        this.millis = 0L;
    }

    float get() {
        if (millis <= 0L) {
            return to;
        }
        float t = (System.currentTimeMillis() - startedAt) / (float) millis;
        if (t >= 1f) {
            millis = 0L;
            from = to;
            return to;
        }
        return from + (to - from) * curve.apply(Math.max(0f, t));
    }
}
