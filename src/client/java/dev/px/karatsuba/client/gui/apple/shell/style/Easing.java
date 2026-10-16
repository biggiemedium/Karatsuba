package dev.px.karatsuba.client.gui.apple.shell.style;

/**
 * A timing curve: how far along an animation is, for how far along its time is.
 *
 * <p>The three in use are Material 3's. Things arriving decelerate (start fast,
 * settle gently), which feels responsive; things leaving accelerate away; things
 * moving from one place on screen to another use the standard curve. Nothing
 * overshoots: a settings panel is a tool, not a toy.
 */
public final class Easing {

    /** Moving within the screen: hover, switches, folding. Material 3 standard. */
    public static final Easing STANDARD = new Easing(0.2f, 0f, 0f, 1f);

    /** Arriving: a window opening, a menu unrolling, new content. Material 3 emphasized decelerate. */
    public static final Easing DECELERATE = new Easing(0.05f, 0.7f, 0.1f, 1f);

    /** Leaving: a window closing. Material 3 emphasized accelerate. */
    public static final Easing ACCELERATE = new Easing(0.3f, 0f, 0.8f, 0.15f);

    /**
     * A page pushed or popped: Apple's quick ease-out, most of the way in the first
     * tenth of a second and then settling, so a push feels instant yet still slides.
     */
    public static final Easing PUSH = new Easing(0.32f, 0.72f, 0f, 1f);

    /** Colour crossfades, where any curve reads as a flicker. */
    public static final Easing LINEAR = new Easing(0f, 0f, 1f, 1f);

    private final float x1;
    private final float y1;
    private final float x2;
    private final float y2;

    private Easing(float x1, float y1, float x2, float y2) {
        this.x1 = x1;
        this.y1 = y1;
        this.x2 = x2;
        this.y2 = y2;
    }

    /** @return the curve's value at {@code time}, both 0 to 1 */
    public float apply(float time) {
        if (time <= 0f) {
            return 0f;
        }
        if (time >= 1f) {
            return 1f;
        }
        if (this == LINEAR) {
            return time;
        }
        // The curve is given as x(t), y(t); find the t whose x is the time asked for.
        float t = time;
        for (int i = 0; i < 8; i++) {
            float x = bezier(t, x1, x2) - time;
            if (Math.abs(x) < 1.0e-4f) {
                break;
            }
            float slope = slope(t, x1, x2);
            if (Math.abs(slope) < 1.0e-6f) {
                break;
            }
            t -= x / slope;
        }
        t = Math.max(0f, Math.min(1f, t));
        return bezier(t, y1, y2);
    }

    private static float bezier(float t, float p1, float p2) {
        float u = 1f - t;
        return 3f * u * u * t * p1 + 3f * u * t * t * p2 + t * t * t;
    }

    private static float slope(float t, float p1, float p2) {
        float u = 1f - t;
        return 3f * u * u * p1 + 6f * u * t * (p2 - p1) + 3f * t * t * (1f - p2);
    }
}
