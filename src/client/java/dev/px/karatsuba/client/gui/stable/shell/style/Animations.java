package dev.px.karatsuba.client.gui.stable.shell.style;

import java.util.function.BooleanSupplier;

/**
 * The GUI's motion budget: how long each kind of change takes, and whether
 * anything moves at all.
 *
 * <p>Short on purpose. Things you do often (hovering, switching a module on,
 * changing category) take about a tenth of a second, so they read as a response
 * rather than a wait; only arrivals of something new (a window, a menu, a pane)
 * take a little longer, and leaving is quicker than arriving. Figures follow
 * Nielsen Norman Group (100-400ms, shorter is better) and Material 3's tokens.
 *
 * <p>With animations off (Client Settings, Interface) every motion is instant,
 * as WCAG 2.3.3 asks. A theme's colours still crossfade, since a colour change
 * is not motion.
 */
public final class Animations {

    /** A hover tint arriving, and leaving. */
    public static final float HOVER_IN = 120f;
    public static final float HOVER_OUT = 160f;

    /** A switch's knob and fill, a checkbox, a slider catching up. */
    public static final float SWITCH = 120f;

    /** A menu or picker unrolling. */
    public static final float POPUP = 180f;

    /** A group of settings folding open or shut. */
    public static final float FOLD = 220f;

    /** New content in a pane: another module, another category, the theme picker. */
    public static final float VIEW = 180f;

    /** How far new content rises as it fades in. */
    public static final float VIEW_RISE = 8f;

    /** The window opening, and closing. */
    public static final float OPEN = 220f;
    public static final float CLOSE = 160f;

    /** How small the window starts as it opens, and ends as it closes. */
    public static final float OPEN_SCALE = 0.97f;
    public static final float CLOSE_SCALE = 0.98f;

    /** One notch of the wheel. */
    public static final float SCROLL = 150f;

    /** One theme's colours crossfading into the next. */
    public static final float THEME = 200f;

    /** Between theme cards appearing, and the most the whole grid waits. */
    public static final float STAGGER = 20f;
    public static final float STAGGER_CAP = 160f;

    private static volatile BooleanSupplier enabled = () -> true;

    private Animations() {
    }

    /** @return whether anything should move, read live from the Interface settings */
    public static boolean enabled() {
        return enabled.getAsBoolean();
    }

    /** Where {@link #enabled} is read from. Set once at boot by the Interface settings. */
    public static void readFrom(BooleanSupplier source) {
        enabled = source == null ? () -> true : source;
    }

    /** @return the time since {@code startNanos} as a share of {@code durationMs}, 0 to 1 */
    public static float progress(long startNanos, float durationMs) {
        if (durationMs <= 0f) {
            return 1f;
        }
        float elapsed = (System.nanoTime() - startNanos) / 1.0e6f;
        return Math.max(0f, Math.min(1f, elapsed / durationMs));
    }
}
