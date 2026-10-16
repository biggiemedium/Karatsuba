package dev.px.karatsuba.client.gui.stable.shell.style;

import dev.px.core.Core;
import dev.px.core.render.Color;
import dev.px.core.render.font.Font;
import dev.px.core.render.theme.Theme;
import dev.px.karatsuba.adapter.asset.Assets;

/**
 * The module GUI's design tokens: every colour, size and font it uses.
 *
 * <p>Dark and quiet on purpose, following Material's dark theme: a near-black
 * base rather than black, elevation shown by surfaces getting lighter rather
 * than by shadows, and text as white at three opacities (87% for what you read,
 * 60% for labels, 38% for what is disabled or merely informative) rather than
 * pure white, which "vibrates" on dark.
 *
 * <p>The client theme is the one accent, used only for state: a module that is
 * on, a switch that is on, a slider's fill, the selected category, focus. So the
 * eye goes straight to what is active. The theme's colours are lifted until they
 * read against the panel (WCAG's 3:1 for controls), so a dark theme such as
 * "Midnight" still shows its switches. Choosing another theme crossfades every
 * accent in the GUI to the new one.
 *
 * <p>Spacing is on a 4-point grid (4, 8, 12, 16), with more room between groups
 * than inside them. Motion lives in {@link Animations}.
 */
public final class Palette {

    // ------------------------------------------------------------- surfaces

    /** Dims the game behind the GUI. */
    public static final Color SCRIM = Color.of(0, 0, 0, 110);

    /** The panel: the lowest surface. */
    public static final Color BASE = Color.of(18, 18, 21, 248);

    /** The category sidebar, one step up. */
    public static final Color SIDEBAR = Color.of(24, 24, 28, 255);

    /** A hovered row. */
    public static final Color HOVER = Color.of(255, 255, 255, 10);

    /** A selected row, a chip, a text field. */
    public static final Color RAISED = Color.of(255, 255, 255, 18);

    /** A pressed or open control. */
    public static final Color RAISED_HIGH = Color.of(255, 255, 255, 28);

    /** Popups, menus and tooltips: the highest surface. */
    public static final Color POPUP = Color.of(36, 36, 42, 252);

    /** Hairlines: the panel's edge, the divider under a header. */
    public static final Color LINE = Color.of(255, 255, 255, 16);

    /** A switch's track while off, a slider's empty track. */
    public static final Color TRACK = Color.of(255, 255, 255, 34);

    // ------------------------------------------------------------------ text

    /** What you read: names, values. 87%. */
    public static final Color TEXT = Color.of(255, 255, 255, 222);

    /** Labels and descriptions. 60%. */
    public static final Color TEXT_MUTED = Color.of(255, 255, 255, 153);

    /** Disabled, placeholders and captions. 38%. */
    public static final Color TEXT_FAINT = Color.of(255, 255, 255, 97);

    /** A warning: a keybind that clashes with another. Amber, the one colour besides the accent. */
    public static final Color WARN = Color.of(242, 184, 88, 235);

    /** Someone online: the dot on a friend's initial. Always beside the word "Online" or a distance. */
    public static final Color ONLINE = Color.of(98, 204, 132, 255);

    /** The knob of a switch that is on, and text on the accent. */
    public static final Color ON_ACCENT = Color.of(255, 255, 255, 245);

    // --------------------------------------------------------------- geometry

    public static final float RADIUS_PANEL = 8f;
    public static final float RADIUS_CONTROL = 4f;

    public static final float ROW_HEIGHT = 22f;
    public static final float GAP_ROW = 2f;
    public static final float GAP_SECTION = 12f;
    public static final float INSET = 8f;

    public static final float SIDEBAR_WIDTH = 116f;
    public static final float LIST_WIDTH = 156f;

    /** The navigation rail, with its labels and with icons alone. */
    public static final float RAIL_WIDTH = 54f;
    public static final float RAIL_WIDTH_COMPACT = 38f;

    /** Where every section's title row sits, so switching sections never moves it. */
    public static final float TITLE_HEIGHT = 22f;

    /** The window's default size, and the least it can be resized to. */
    public static final float FRAME_WIDTH = 700f;
    public static final float FRAME_HEIGHT = 380f;
    public static final float FRAME_MIN_WIDTH = 420f;
    public static final float FRAME_MIN_HEIGHT = 220f;

    /** The accent never gets fainter than this against {@link #BASE}. */
    private static final double MIN_CONTRAST = 3.0d;

    // ------------------------------------------------- the theme crossfade

    private static Theme shown;
    private static Color fromPrimary;
    private static Color fromSecondary;
    private static long changedAt;

    private Palette() {
    }

    // ----------------------------------------------------------------- accent

    /** The theme's primary colour, lifted until it reads against the panel. */
    public static Color accent() {
        return accent(0f);
    }

    /** A point on the theme's gradient, 0 primary to 1 secondary, lifted the same way. */
    public static Color accent(float progress) {
        Theme active = Core.themes().getActive();
        if (active != shown) {
            // Start the crossfade from whatever is on screen now, even mid-fade.
            if (shown != null) {
                fromPrimary = blended(0f);
                fromSecondary = blended(1f);
                changedAt = System.nanoTime();
            }
            shown = active;
        }
        Color now = blended(progress);
        return legible(now);
    }

    /** The accent at {@code alpha}, for a tint behind something selected. */
    public static Color accentTint(int alpha) {
        return accent().withAlpha(alpha);
    }

    /** {@code theme}'s own accent, lifted: for drawing a theme that is not the active one. */
    public static Color accentOf(Theme theme, float progress) {
        return legible(theme.accent(progress));
    }

    private static Color blended(float progress) {
        Color target = shown.accent(progress);
        if (fromPrimary == null) {
            return target;
        }
        float t = Easing.LINEAR.apply(Animations.progress(changedAt, Animations.THEME));
        if (t >= 1f) {
            fromPrimary = null;
            fromSecondary = null;
            return target;
        }
        return fromPrimary.lerp(fromSecondary, progress).lerp(target, t);
    }

    private static Color legible(Color color) {
        Color lifted = color.withAlpha(255);
        for (int step = 0; step < 10 && contrast(lifted, BASE) < MIN_CONTRAST; step++) {
            lifted = lifted.lerp(Color.WHITE, 0.15f);
        }
        return lifted;
    }

    private static double contrast(Color a, Color b) {
        double first = luminance(a);
        double second = luminance(b);
        return (Math.max(first, second) + 0.05d) / (Math.min(first, second) + 0.05d);
    }

    private static double luminance(Color color) {
        return 0.2126d * linear(color.redF()) + 0.7152d * linear(color.greenF()) + 0.0722d * linear(color.blueF());
    }

    private static double linear(float channel) {
        return channel <= 0.03928f ? channel / 12.92d : Math.pow((channel + 0.055d) / 1.055d, 2.4d);
    }

    // ------------------------------------------------------------------ fonts

    /** Body text: the client's default font. */
    public static Font body() {
        return Core.fonts().getDefault();
    }

    /** Section headers and the selected tab: the body size, semibold where the system has it. */
    public static Font strong() {
        return Assets.systemFont("gui-strong", body().getSize(), "seguisb", "Inter-SemiBold", "DejaVuSans-Bold");
    }

    /** The module's name over its settings, and the brand. */
    public static Font title() {
        return Assets.systemFont("gui-title", body().getSize() + 3f, "seguisb", "Inter-SemiBold", "DejaVuSans-Bold");
    }

    /** Captions: counts, the category beside a search result, hints. */
    public static Font small() {
        return Assets.systemFont("gui-small", body().getSize() - 1f);
    }

    // ---------------------------------------------------------------- colour

    /**
     * What to draw on top of {@code accent}: white, unless the accent is so light
     * (the White theme, say) that white would vanish into it, then near-black.
     */
    public static Color onAccent(Color accent) {
        return luminance(accent) > 0.6d ? Color.of(24, 24, 28, 235) : ON_ACCENT;
    }

    /** {@code colour} with its opacity scaled by {@code amount}, 0 to 1. */
    public static Color faded(Color colour, float amount) {
        return colour.withAlpha(Math.round(colour.getAlpha() * Math.max(0f, Math.min(1f, amount))));
    }
}
