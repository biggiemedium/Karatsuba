package dev.px.karatsuba.client.gui.apple.shell.style;

/**
 * A number animating toward a target over a fixed time, along a curve.
 *
 * <p>Read once a frame with {@link #toward}. When the target changes the motion
 * starts again from wherever it is now, so a hover that ends half way through
 * fading in fades back out from there rather than jumping: every animation can be
 * interrupted. With animations off it is always at its target.
 *
 * <p>Rising and falling can take different times, so a highlight can arrive in
 * 120ms and leave in 160ms.
 */
public final class Motion {

    private final float riseMs;
    private final float fallMs;
    private final Easing easing;

    private float from;
    private float target;
    private long started;

    public Motion(float durationMs, float initial) {
        this(durationMs, durationMs, Easing.STANDARD, initial);
    }

    public Motion(float riseMs, float fallMs, Easing easing, float initial) {
        this.riseMs = riseMs;
        this.fallMs = fallMs;
        this.easing = easing;
        this.from = initial;
        this.target = initial;
    }

    /** A hover tint: quick in, a little slower out. */
    public static Motion hover() {
        return new Motion(Animations.HOVER_IN, Animations.HOVER_OUT, Easing.STANDARD, 0f);
    }

    /** A switch or checkbox, starting where its state is now: 120ms, easing out. */
    public static Motion toggle(boolean on) {
        return new Motion(Animations.SWITCH, Animations.SWITCH, Easing.DECELERATE, on ? 1f : 0f);
    }

    /** @return the value now, after retargeting to {@code wanted} if it changed */
    public float toward(float wanted) {
        if (wanted != target) {
            from = value();
            target = wanted;
            started = System.nanoTime();
        }
        return value();
    }

    public float toward(boolean on) {
        return toward(on ? 1f : 0f);
    }

    /** @return the value now, without changing the target */
    public float value() {
        if (!Animations.enabled() || started == 0L) {
            return target;
        }
        float duration = target >= from ? riseMs : fallMs;
        float progress = Animations.progress(started, duration);
        if (progress >= 1f) {
            from = target;
            return target;
        }
        return from + (target - from) * easing.apply(progress);
    }

    /** Jumps to {@code value} with no motion: a state set before anything was shown. */
    public void snap(float value) {
        from = value;
        target = value;
        started = 0L;
    }

    /** @return whether the motion has reached its target */
    public boolean settled() {
        return value() == target;
    }
}
