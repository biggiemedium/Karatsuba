package dev.px.karatsuba.client.gui.apple.shell.look;

import dev.px.core.render.Color;
import dev.px.core.render.Render;
import dev.px.karatsuba.client.gui.apple.shell.rail.Glyph;

import static dev.px.karatsuba.client.gui.apple.shell.style.Palette.*;

/**
 * A sidebar icon as macOS Tahoe draws one ({@code gui/apple/DESIGN.md} §4.7): a
 * tile in a system colour, shaded faintly from top to bottom, with a 1px highlight
 * inside its top edge and, in Dark, a faint outline; on it a white glyph in filled
 * shapes, one stroke weight, sized to {@link #ICON_GLYPH_SIZE}.
 *
 * <p>The glyphs here are the tile set, drawn filled where {@link Glyphs} draws the
 * same icons in outline for buttons and rows. Where a glyph needs a gap in it (a
 * cube's edges, an eye's iris), the gap is cut in the tile's own colour at that
 * height, so it reads as the tile showing through.
 */
final class IconTile {

    /** Room past a shape's edge that its seams overlap by, so filled pieces join without a hairline. */
    private static final float OVERLAP = 0.5f;

    private IconTile() {
    }

    /** Draws the tile in {@code base} filling the square at {@code x, y}, and {@code glyph} centred on it. */
    static void draw(Glyph glyph, Color base, float x, float y, float size) {
        Color top = base.lerp(Color.WHITE, ICON_SHADE);
        Color bottom = base.lerp(Color.BLACK, ICON_SHADE);
        Render.roundGradient(x, y, size, size, R_ICON, top, top, bottom, bottom);

        // The highlight: the tile in white, then the tile again 1px lower over it, which
        // leaves a 1px crescent along the top edge that thins out round the corners.
        Render.pushClip(x, y, size, size / 2f);
        Render.roundRect(x, y, size, size, R_ICON, ICON_HIGHLIGHT);
        Color below = top.lerp(bottom, 1f / size);
        Render.roundGradient(x, y + 1f, size, size - 1f, R_ICON, below, below, bottom, bottom);
        Render.popClip();

        Color edge = ICON_EDGE.get();
        if (edge.getAlpha() > 0) {
            Render.roundRectOutline(x, y, size, size, R_ICON, 1f, edge);
        }

        float g = ICON_GLYPH_SIZE * size / ICON_SIZE;
        new Ink(x + (size - g) / 2f, y + (size - g) / 2f, g, y, size, top, bottom).glyph(glyph);
    }

    /**
     * One glyph's drawing: positions as shares of the glyph's square, white for the
     * shape and the tile's colour for the cuts in it.
     */
    private record Ink(float gx, float gy, float g, float tileY, float tileSize, Color top, Color bottom) {

        float x(float share) {
            return gx + g * share;
        }

        float y(float share) {
            return gy + g * share;
        }

        float stroke() {
            return g * ICON_STROKE;
        }

        /** A cut through a shape: thinner than the stroke, so the shape still reads as one. */
        float cut() {
            return stroke() * 0.6f;
        }

        /** @return the tile's colour at screen height {@code at}, for a cut there */
        Color tile(float at) {
            float t = Math.max(0f, Math.min(1f, (at - tileY) / tileSize));
            return top.lerp(bottom, t);
        }

        /** A stroke with round ends, from one share point to another. */
        void bar(float x1, float y1, float x2, float y2, float width, Color colour) {
            Render.line(x(x1), y(y1), x(x2), y(y2), width, colour);
            Render.circle(x(x1), y(y1), width / 2f, colour);
            Render.circle(x(x2), y(y2), width / 2f, colour);
        }

        /** A cut line between two share points, in the tile's colour at its middle. */
        void cutLine(float x1, float y1, float x2, float y2) {
            Render.line(x(x1), y(y1), x(x2), y(y2), cut(), tile((y(y1) + y(y2)) / 2f));
        }

        void glyph(Glyph glyph) {
            Color white = ICON_GLYPH;
            float w = stroke();
            float cx = x(0.5f);
            float cy = y(0.5f);
            switch (glyph) {
                case SWORD -> {
                    // A blade up to the top right, a crossguard across it, a grip and pommel below.
                    bar(0.32f, 0.68f, 0.86f, 0.14f, w, white);
                    bar(0.16f, 0.56f, 0.44f, 0.84f, w, white);
                    bar(0.30f, 0.70f, 0.16f, 0.84f, w, white);
                    Render.circle(x(0.13f), y(0.87f), w * 0.8f, white);
                }
                case MOVEMENT -> {
                    // Two arrowheads running on: fast forward.
                    Render.triangle(x(0.08f), y(0.22f), x(0.08f), y(0.78f), x(0.50f), cy, white);
                    Render.triangle(x(0.50f), y(0.22f), x(0.50f), y(0.78f), x(0.92f), cy, white);
                }
                case CUBE -> {
                    // A solid cube seen corner on: a hexagon, its three faces parted by cuts.
                    Render.rect(x(0.10f), y(0.28f) - OVERLAP, g * 0.80f, g * 0.44f + OVERLAP * 2f, white);
                    Render.triangle(x(0.10f), y(0.28f), x(0.90f), y(0.28f), cx, y(0.06f), white);
                    Render.triangle(x(0.10f), y(0.72f), x(0.90f), y(0.72f), cx, y(0.94f), white);
                    cutLine(0.50f, 0.50f, 0.06f, 0.26f);
                    cutLine(0.50f, 0.50f, 0.94f, 0.26f);
                    cutLine(0.50f, 0.50f, 0.50f, 1.00f);
                    Render.circle(cx, cy, cut() / 2f, tile(cy));
                }
                case PICKAXE -> {
                    // A curved head across the top right, square to a handle from the bottom left.
                    float hx = x(0.40f);
                    float hy = y(0.60f);
                    float r = g * 0.46f;
                    Render.pushClip(hx, hy - r - w, r + w, r + w);
                    Render.circleOutline(hx, hy, r + w / 2f, w, white);
                    Render.popClip();
                    Render.circle(hx, hy - r, w / 2f, white);
                    Render.circle(hx + r, hy, w / 2f, white);
                    bar(0.14f, 0.86f, 0.70f, 0.30f, w, white);
                }
                case CHEST -> {
                    // A box, its lid parted from it, and the latch where they meet.
                    Render.roundRect(x(0.04f), y(0.16f), g * 0.92f, g * 0.72f, g * 0.14f, white);
                    Render.rect(x(0.04f) - OVERLAP, y(0.44f) - cut() / 2f, g * 0.92f + OVERLAP * 2f, cut(),
                            tile(y(0.44f)));
                    Render.roundRect(cx - g * 0.12f, y(0.32f), g * 0.24f, g * 0.26f, g * 0.06f, tile(y(0.45f)));
                    Render.roundRect(cx - g * 0.05f, y(0.38f), g * 0.10f, g * 0.12f, g * 0.03f, white);
                }
                case EYE -> {
                    // An almond, the meeting of two large circles, with the iris cut out of it.
                    float r = g * 0.58f;
                    float lid = g * 0.30f;
                    Render.pushClip(gx - 1f, gy, g + 2f, cy - gy + OVERLAP);
                    Render.circle(cx, cy + lid, r, white);
                    Render.popClip();
                    Render.pushClip(gx - 1f, cy - OVERLAP, g + 2f, g / 2f + 1f);
                    Render.circle(cx, cy - lid, r, white);
                    Render.popClip();
                    Render.circle(cx, cy, g * 0.22f, tile(cy));
                    Render.circle(cx, cy, g * 0.12f, white);
                }
                case WRENCH -> {
                    // A handle at an angle, and a round head with its jaw open to the top right.
                    bar(0.16f, 0.84f, 0.60f, 0.40f, w, white);
                    float hx = x(0.66f);
                    float hy = y(0.34f);
                    Render.circle(hx, hy, g * 0.26f, white);
                    Render.line(hx, hy, hx + g * 0.4f, hy - g * 0.4f, g * 0.18f, tile(y(0.2f)));
                    Render.circle(hx, hy, g * 0.09f, tile(hy));
                }
                case ROBOT -> {
                    // A head with ears, two eyes cut out, and an antenna.
                    Render.roundRect(x(0.0f), y(0.52f), g, g * 0.20f, g * 0.06f, white);
                    Render.roundRect(x(0.10f), y(0.32f), g * 0.80f, g * 0.58f, g * 0.18f, white);
                    Render.circle(x(0.34f), y(0.60f), g * 0.09f, tile(y(0.60f)));
                    Render.circle(x(0.66f), y(0.60f), g * 0.09f, tile(y(0.60f)));
                    bar(0.50f, 0.32f, 0.50f, 0.18f, w, white);
                    Render.circle(cx, y(0.12f), w * 0.8f, white);
                }
                case HUD -> {
                    // A screen with readouts in its corners, cut out of it.
                    Render.roundRect(x(0.0f), y(0.14f), g, g * 0.72f, g * 0.16f, white);
                    float bar = cut() * 1.4f;
                    Render.roundRect(x(0.16f), y(0.30f), g * 0.34f, bar, bar / 2f, tile(y(0.30f)));
                    Render.roundRect(x(0.16f), y(0.46f), g * 0.22f, bar, bar / 2f, tile(y(0.46f)));
                    Render.roundRect(x(0.52f), y(0.66f), g * 0.32f, bar, bar / 2f, tile(y(0.66f)));
                }
                case PEOPLE -> {
                    // Two people, one a step behind the other, parted by a cut round the front one.
                    Render.circle(x(0.68f), y(0.30f), g * 0.15f, white);
                    Render.pushClip(gx - 1f, gy, g + 2f, g * 0.84f);
                    Render.circle(x(0.70f), y(0.98f), g * 0.34f, white);
                    Render.popClip();
                    float gap = g * 0.08f;
                    Render.pushClip(gx - 1f, gy, g + 2f, g * 0.94f);
                    Render.circle(x(0.36f), y(0.34f), g * 0.18f + gap, tile(y(0.34f)));
                    Render.circle(x(0.36f), y(1.06f), g * 0.40f + gap, tile(y(0.80f)));
                    Render.circle(x(0.36f), y(0.34f), g * 0.18f, white);
                    Render.circle(x(0.36f), y(1.06f), g * 0.40f, white);
                    Render.popClip();
                }
                case PERSON -> {
                    // One person: a head over shoulders, the shoulders a large circle cut at the foot.
                    Render.circle(cx, y(0.28f), g * 0.20f, white);
                    Render.pushClip(gx - 1f, gy, g + 2f, g * 0.94f);
                    Render.circle(cx, y(1.02f), g * 0.42f, white);
                    Render.popClip();
                }
                case LAYERS -> {
                    // Three sheets stacked, the top one whole and the two under it showing
                    // as bands below it, each parted from the one above by a cut.
                    float sheet = g * 0.40f;
                    float r = g * 0.10f;
                    for (int i = 2; i >= 0; i--) {
                        float top = gy + g * 0.08f + i * g * 0.22f;
                        if (i < 2) {
                            Render.roundRect(gx - OVERLAP, top - cut(), g + OVERLAP * 2f, sheet + cut() * 2f, r + cut(),
                                    tile(top + sheet / 2f));
                        }
                        Render.roundRect(gx, top, g, sheet, r, white);
                    }
                }
                case THEMES -> {
                    // A circle, light and dark: its left half solid, its right half a ring.
                    float r = g * 0.48f;
                    Render.circle(cx, cy, r, white);
                    Render.pushClip(cx, gy - 1f, g / 2f + 1f, g + 2f);
                    Render.circle(cx, cy, r - w, tile(cy));
                    Render.popClip();
                }
                case SETTINGS -> {
                    // A gear: eight square teeth round a solid body, and a hole in the middle.
                    float tooth = g * 0.18f;
                    for (int i = 0; i < 8; i++) {
                        double a = Math.PI / 4d * i;
                        float cos = (float) Math.cos(a);
                        float sin = (float) Math.sin(a);
                        Render.line(cx + cos * g * 0.30f, cy + sin * g * 0.30f,
                                cx + cos * g * 0.50f, cy + sin * g * 0.50f, tooth, white);
                    }
                    Render.circle(cx, cy, g * 0.36f, white);
                    Render.circle(cx, cy, g * 0.14f, tile(cy));
                }
                default -> {
                    // Four tiles: the modules, for a category whose icon is not known.
                    float gap = g * 0.14f;
                    float tile = (g - gap) / 2f;
                    float r = tile * 0.25f;
                    Render.roundRect(gx, gy, tile, tile, r, white);
                    Render.roundRect(gx + tile + gap, gy, tile, tile, r, white);
                    Render.roundRect(gx, gy + tile + gap, tile, tile, r, white);
                    Render.roundRect(gx + tile + gap, gy + tile + gap, tile, tile, r, white);
                }
            }
        }
    }
}
