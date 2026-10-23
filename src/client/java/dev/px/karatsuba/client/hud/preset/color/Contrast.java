package dev.px.karatsuba.client.hud.preset.color;

import dev.px.core.render.Color;

/**
 * WCAG contrast, for presets lifting colours until they pass ({@code hud/DESIGN.md}
 * §2.2, §2.11, §5b): adjusted until they pass, never rejected.
 */
public final class Contrast {

    /** Increase Contrast's target, for every part of the HUD (§2.11). */
    public static final double HIGH = 7.0d;

    private static final int STEPS = 20;
    private static final float STEP = 0.1f;

    private Contrast() {
    }

    /** @return the contrast ratio between two opaque colours, 1 to 21 */
    public static double ratio(Color a, Color b) {
        double first = luminance(a);
        double second = luminance(b);
        return (Math.max(first, second) + 0.05d) / (Math.min(first, second) + 0.05d);
    }

    /**
     * @return {@code colour} moved toward white on a dark {@code background} (or black
     *         on a light one) until it reaches {@code target} against it, keeping its
     *         own alpha; unchanged if it already does
     */
    public static Color lift(Color colour, Color background, double target) {
        Color opaque = colour.withAlpha(255);
        Color toward = luminance(background) < 0.5d ? Color.WHITE : Color.BLACK;
        for (int step = 0; step < STEPS && ratio(opaque, background) < target; step++) {
            opaque = opaque.lerp(toward, STEP);
        }
        return opaque.withAlpha(colour.getAlpha());
    }

    private static double luminance(Color color) {
        return 0.2126d * linear(color.redF()) + 0.7152d * linear(color.greenF()) + 0.0722d * linear(color.blueF());
    }

    private static double linear(float channel) {
        return channel <= 0.03928f ? channel / 12.92d : Math.pow((channel + 0.055d) / 1.055d, 2.4d);
    }
}
