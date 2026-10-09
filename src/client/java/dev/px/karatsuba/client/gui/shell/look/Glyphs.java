package dev.px.karatsuba.client.gui.shell.look;

import dev.px.core.render.Color;
import dev.px.core.render.Render;
import dev.px.karatsuba.client.gui.shell.rail.Glyph;

/** Draws each {@link Glyph} in a square, in one colour, with one stroke weight. */
final class Glyphs {

    private static final float STROKE = 1.2f;

    private Glyphs() {
    }

    static void draw(Glyph glyph, float x, float y, float size, Color colour) {
        float s = size;
        float cx = x + s / 2f;
        float cy = y + s / 2f;
        switch (glyph) {
            case MODULES -> {
                float gap = s * 0.14f;
                float tile = (s - gap) / 2f;
                Render.roundRect(x, y, tile, tile, 1.5f, colour);
                Render.roundRect(x + tile + gap, y, tile, tile, 1.5f, colour);
                Render.roundRect(x, y + tile + gap, tile, tile, 1.5f, colour);
                Render.roundRectOutline(x + tile + gap, y + tile + gap, tile, tile, 1.5f, STROKE, colour);
            }
            case HUD -> {
                Render.roundRectOutline(x, y + s * 0.12f, s, s * 0.76f, 2f, STROKE, colour);
                Render.rect(x + s * 0.18f, y + s * 0.28f, s * 0.3f, STROKE, colour);
                Render.rect(x + s * 0.18f, y + s * 0.4f, s * 0.2f, STROKE, colour);
                Render.rect(x + s * 0.55f, y + s * 0.66f, s * 0.27f, STROKE, colour);
            }
            case LAYOUT -> {
                float arm = s * 0.28f;
                // Corner brackets.
                Render.line(x, y + arm, x, y, STROKE, colour);
                Render.line(x, y, x + arm, y, STROKE, colour);
                Render.line(x + s - arm, y, x + s, y, STROKE, colour);
                Render.line(x + s, y, x + s, y + arm, STROKE, colour);
                Render.line(x, y + s - arm, x, y + s, STROKE, colour);
                Render.line(x, y + s, x + arm, y + s, STROKE, colour);
                Render.line(x + s - arm, y + s, x + s, y + s, STROKE, colour);
                Render.line(x + s, y + s, x + s, y + s - arm, STROKE, colour);
                // A crosshair in the middle.
                Render.line(cx - s * 0.18f, cy, cx + s * 0.18f, cy, STROKE, colour);
                Render.line(cx, cy - s * 0.18f, cx, cy + s * 0.18f, STROKE, colour);
            }
            case THEMES -> {
                float r = s / 2f - 0.5f;
                Render.circleOutline(cx, cy, r, STROKE, colour);
                // The left half filled.
                Render.pushClip(x, y, s / 2f, s);
                Render.circle(cx, cy, r, colour);
                Render.popClip();
            }
            case NAVIGATION -> {
                float r = s / 2f - 0.5f;
                Render.circleOutline(cx, cy, r, STROKE, colour);
                Render.triangle(cx, cy - r * 0.7f, cx + r * 0.3f, cy, cx - r * 0.3f, cy, colour);
                Render.line(cx, cy, cx, cy + r * 0.6f, STROKE, colour);
            }
            case UNDO, REDO -> {
                // An arrow heading out, along the top, round a half circle and back underneath.
                boolean back = glyph == Glyph.UNDO;
                float r = s * 0.22f;
                float turnX = back ? x + s * 0.62f : x + s * 0.38f;
                float top = y + s * 0.34f;
                float bottom = top + r * 2f;
                float tip = back ? x + s * 0.12f : x + s * 0.88f;
                float tail = back ? x + s * 0.32f : x + s * 0.68f;
                float head = s * 0.18f * (back ? 1f : -1f);
                Render.line(tip, top, turnX, top, STROKE, colour);
                Render.pushClip(back ? turnX : turnX - r - STROKE, top - STROKE, r + STROKE, r * 2f + STROKE * 2f);
                Render.circleOutline(turnX, top + r, r, STROKE, colour);
                Render.popClip();
                Render.line(turnX, bottom, tail, bottom, STROKE, colour);
                Render.line(tip, top, tip + head, top - Math.abs(head), STROKE, colour);
                Render.line(tip, top, tip + head, top + Math.abs(head), STROKE, colour);
            }
            case SNAP -> {
                // A horseshoe magnet: two legs, the bend underneath, and the poles marked.
                float w = s * 0.7f;
                float r = w / 2f;
                float left = cx - r;
                float bend = y + s * 0.5f;
                Render.line(left, y + s * 0.12f, left, bend, STROKE, colour);
                Render.line(left + w, y + s * 0.12f, left + w, bend, STROKE, colour);
                Render.pushClip(x, bend, s, s);
                Render.circleOutline(cx, bend, r, STROKE, colour);
                Render.popClip();
                Render.rect(left - STROKE, y + s * 0.12f, STROKE * 2f, s * 0.14f, colour);
                Render.rect(left + w - STROKE, y + s * 0.12f, STROKE * 2f, s * 0.14f, colour);
            }
            case RESET -> {
                float r = s * 0.36f;
                Render.circleOutline(cx, cy, r, STROKE, colour);
                // An arrowhead at the top right.
                Render.triangle(cx + r * 0.2f, cy - r - s * 0.16f, cx + r * 0.2f, cy - r + s * 0.16f,
                        cx + r * 0.75f, cy - r, colour);
            }
            case EDIT -> {
                Render.line(x + s * 0.2f, y + s * 0.8f, x + s * 0.8f, y + s * 0.2f, STROKE * 1.6f, colour);
                Render.line(x + s * 0.12f, y + s * 0.88f, x + s * 0.22f, y + s * 0.85f, STROKE, colour);
                Render.line(x + s * 0.15f, y + s * 0.75f, x + s * 0.12f, y + s * 0.88f, STROKE, colour);
            }
            case EYE -> {
                // Two lids, each the edge of a large circle, meeting at the corners.
                float r = s * 0.6f;
                float lid = s * 0.42f;
                Render.pushClip(x, y, s, cy - y);
                Render.circleOutline(cx, cy + lid, r, STROKE, colour);
                Render.popClip();
                Render.pushClip(x, cy, s, s / 2f);
                Render.circleOutline(cx, cy - lid, r, STROKE, colour);
                Render.popClip();
                Render.circle(cx, cy, s * 0.14f, colour);
            }
            case SETTINGS -> {
                // A gear: a ring with eight teeth, and a hole in the middle.
                float r = s * 0.32f;
                for (int i = 0; i < 8; i++) {
                    double a = Math.PI / 4d * i;
                    float cos = (float) Math.cos(a);
                    float sin = (float) Math.sin(a);
                    Render.line(cx + cos * r, cy + sin * r, cx + cos * (s * 0.47f), cy + sin * (s * 0.47f),
                            STROKE * 2.2f, colour);
                }
                // The body is a thick ring, which leaves the hole open.
                Render.circleOutline(cx, cy, r * 0.72f, r * 0.62f, colour);
            }
            case PEOPLE -> {
                // Two people, one a step behind the other: a head and shoulders each.
                Render.circleOutline(x + s * 0.64f, y + s * 0.3f, s * 0.15f, STROKE, colour);
                Render.pushClip(x + s * 0.4f, y + s * 0.5f, s * 0.6f, s * 0.5f);
                Render.circleOutline(x + s * 0.68f, y + s * 0.95f, s * 0.3f, STROKE, colour);
                Render.popClip();
                Render.circle(x + s * 0.36f, y + s * 0.36f, s * 0.17f, colour);
                Render.pushClip(x, y + s * 0.58f, s * 0.75f, s * 0.42f);
                Render.circle(x + s * 0.36f, y + s * 1.02f, s * 0.36f, colour);
                Render.popClip();
            }
            case ADD -> {
                Render.line(cx, y + s * 0.15f, cx, y + s * 0.85f, STROKE * 1.2f, colour);
                Render.line(x + s * 0.15f, cy, x + s * 0.85f, cy, STROKE * 1.2f, colour);
            }
            case REMOVE -> {
                Render.line(x + s * 0.22f, y + s * 0.22f, x + s * 0.78f, y + s * 0.78f, STROKE * 1.2f, colour);
                Render.line(x + s * 0.78f, y + s * 0.22f, x + s * 0.22f, y + s * 0.78f, STROKE * 1.2f, colour);
            }
            case COPY -> {
                Render.roundRectOutline(x + s * 0.32f, y + s * 0.1f, s * 0.56f, s * 0.6f, 1.5f, STROKE, colour);
                Render.roundRectOutline(x + s * 0.12f, y + s * 0.3f, s * 0.56f, s * 0.6f, 1.5f, STROKE, colour);
            }
            case HOME -> {
                // A roof, the walls under it, and a door.
                float eaves = y + s * 0.45f;
                Render.line(x + s * 0.06f, eaves, cx, y + s * 0.08f, STROKE, colour);
                Render.line(cx, y + s * 0.08f, x + s * 0.94f, eaves, STROKE, colour);
                Render.line(x + s * 0.2f, y + s * 0.34f, x + s * 0.2f, y + s * 0.9f, STROKE, colour);
                Render.line(x + s * 0.8f, y + s * 0.34f, x + s * 0.8f, y + s * 0.9f, STROKE, colour);
                Render.line(x + s * 0.2f, y + s * 0.9f, x + s * 0.8f, y + s * 0.9f, STROKE, colour);
                Render.roundRect(cx - s * 0.1f, y + s * 0.6f, s * 0.2f, s * 0.3f, 1f, colour);
            }
            case CHECK -> {
                Render.line(x + s * 0.12f, y + s * 0.52f, x + s * 0.4f, y + s * 0.8f, STROKE * 1.3f, colour);
                Render.line(x + s * 0.4f, y + s * 0.8f, x + s * 0.9f, y + s * 0.22f, STROKE * 1.3f, colour);
            }
            default -> {
            }
        }
    }
}
