package dev.px.karatsuba.client.hud.preset.apple;

import dev.px.core.render.Render;
import dev.px.karatsuba.client.hud.preset.role.HudCategory;
import dev.px.karatsuba.client.hud.preset.role.HudIcon;

/**
 * The HUD's own glyphs ({@code hud/DESIGN.md} §4.11): original, drawn from strokes
 * and dots so they take any colour and stay sharp at any scale, one per concept.
 * Each draws in a square of {@code size}, in one stroke weight.
 */
public final class HudGlyphs {

    /** The one stroke weight, as a share of the glyph's size. */
    private static final float STROKE = 0.16f;

    /** Information: a lowercase i, its dot above its stem. */
    public static final HudIcon INFO = (x, y, s, c) -> {
        float w = s * STROKE;
        float cx = x + s / 2f;
        Render.circle(cx, y + s * 0.2f, w * 0.75f, c);
        bar(cx, y + s * 0.42f, cx, y + s * 0.84f, w, c);
    };

    /** Success: a tick. */
    public static final HudIcon SUCCESS = (x, y, s, c) -> {
        float w = s * STROKE;
        bar(x + s * 0.16f, y + s * 0.52f, x + s * 0.42f, y + s * 0.78f, w, c);
        bar(x + s * 0.42f, y + s * 0.78f, x + s * 0.86f, y + s * 0.24f, w, c);
    };

    /** Warning: an exclamation mark, its stem above its dot. */
    public static final HudIcon WARNING = (x, y, s, c) -> {
        float w = s * STROKE;
        float cx = x + s / 2f;
        bar(cx, y + s * 0.16f, cx, y + s * 0.58f, w, c);
        Render.circle(cx, y + s * 0.82f, w * 0.75f, c);
    };

    /** Error: a cross. */
    public static final HudIcon ERROR = (x, y, s, c) -> {
        float w = s * STROKE;
        bar(x + s * 0.24f, y + s * 0.24f, x + s * 0.76f, y + s * 0.76f, w, c);
        bar(x + s * 0.76f, y + s * 0.24f, x + s * 0.24f, y + s * 0.76f, w, c);
    };

    private HudGlyphs() {
    }

    /** @return the glyph for {@code category} */
    public static HudIcon of(HudCategory category) {
        return switch (category) {
            case INFO -> INFO;
            case SUCCESS -> SUCCESS;
            case WARNING -> WARNING;
            case ERROR -> ERROR;
        };
    }

    /** A stroke with round ends. */
    private static void bar(float x1, float y1, float x2, float y2, float width, dev.px.core.render.Color c) {
        Render.line(x1, y1, x2, y2, width, c);
        Render.circle(x1, y1, width / 2f, c);
        Render.circle(x2, y2, width / 2f, c);
    }
}
