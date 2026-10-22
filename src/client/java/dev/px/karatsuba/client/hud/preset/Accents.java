package dev.px.karatsuba.client.hud.preset;

import dev.px.core.Core;
import dev.px.core.render.Color;
import dev.px.core.render.theme.Theme;
import dev.px.karatsuba.client.gui.apple.shell.style.Palette;

/**
 * The accent's checked variants ({@code hud/DESIGN.md} §5b.2): the active theme's
 * first colour, moved toward black or white until it reaches 3:1 against what it
 * sits on. Adjusted, never rejected, and unchanged where it already passes, so a
 * near-white or low-saturation theme still works (state never rests on colour
 * alone, §5b.3).
 */
public final class Accents {

    /** What every variant reaches against what it sits on. */
    public static final double TARGET = 3.0d;

    /** What an accent reaches where it is text (§2.2). */
    public static final double TEXT = 4.5d;

    /** Clear glass's tint (ApplePreset), for the glass's brightest: Clear over a white world. */
    private static final float CLEAR_TINT = 0.10f;

    private Accents() {
    }

    /** @return the theme's first colour, opaque; the one Apple uses (§9, Theme shape) */
    public static Color theme() {
        Theme active = Core.themes().getActive();
        return active.accent(0f).withAlpha(255);
    }

    /** @return the accent on glass, 3:1 at the glass's brightest: Clear glass over a white world */
    public static Color onGlass(boolean light) {
        Palette.Tone tone = Palette.GLASS_TONE;
        Color brightest = Color.WHITE.lerp(light ? tone.light() : tone.dark(), CLEAR_TINT).withAlpha(255);
        return Contrast.lift(theme(), brightest, TARGET);
    }

    /** @return the accent on the white toggleOn well: 3:1 against white, the same pair as a white knob on it */
    public static Color onToggle() {
        return Contrast.lift(theme(), Color.WHITE, TARGET);
    }

    /** @return {@code accent} on a Legacy plate: 3:1 against the plate made opaque */
    public static Color onPlate(Color accent, Color plate) {
        return onPlate(accent, plate, TARGET);
    }

    /** @return {@code accent} on a Legacy plate made opaque, reaching {@code target}: {@link #TEXT} where it is text */
    public static Color onPlate(Color accent, Color plate, double target) {
        return Contrast.lift(accent, plate.withAlpha(255), target);
    }
}
