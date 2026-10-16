package dev.px.karatsuba.client.gui.stable.shell.look;

import dev.px.core.layout.Align;
import dev.px.core.layout.Content;
import dev.px.core.render.Color;
import dev.px.core.render.Render;

import java.util.Locale;

import static dev.px.karatsuba.client.gui.stable.shell.style.Palette.*;

/** The small drawings every part of the look shares: switches, chevrons, ticks, swatches. */
final class Shapes {

    private Shapes() {
    }

    /** A switch: a pill track that fills with the accent and a knob that slides across, at {@code on} 0 to 1. */
    static void toggle(float x, float y, float w, float h, float on) {
        toggle(x, y, w, h, on, accent());
    }

    static void toggle(float x, float y, float w, float h, float on, Color accent) {
        Render.roundRect(x, y, w, h, h / 2f, TRACK.lerp(accent, on));
        float radius = h / 2f - 1.5f;
        float centre = x + h / 2f + (w - h) * on;
        Render.circle(centre, y + h / 2f, radius, TEXT_MUTED.lerp(onAccent(accent), on));
    }

    /** A slider's knob, growing a little while it is hovered or held ({@code grip} 0 to 1). */
    static void knob(float x, float y, float grip) {
        float radius = 3.5f + grip;
        Render.circle(x, y, radius + 1f, accent());
        Render.circle(x, y, radius, onAccent(accent()));
    }

    /** A chevron pointing down when {@code open} is 1 and right when it is 0, turning between. */
    static void chevron(float x, float y, float w, float h, float open, Color colour) {
        float cx = x + w / 2f;
        float cy = y + h / 2f;
        float size = Math.min(w, h) / 2f;
        double angle = Math.toRadians(-90d * (1f - open));
        float[][] points = {{-size, -size / 2f}, {0f, size / 2f}, {size, -size / 2f}};
        float[][] turned = new float[3][2];
        for (int i = 0; i < 3; i++) {
            float px = points[i][0];
            float py = points[i][1];
            turned[i][0] = cx + (float) (px * Math.cos(angle) - py * Math.sin(angle));
            turned[i][1] = cy + (float) (px * Math.sin(angle) + py * Math.cos(angle));
        }
        Render.line(turned[0][0], turned[0][1], turned[1][0], turned[1][1], 1.2f, colour);
        Render.line(turned[1][0], turned[1][1], turned[2][0], turned[2][1], 1.2f, colour);
    }

    /** A chevron pointing left: "back". */
    static void back(float x, float y, float w, float h, Color colour) {
        float cx = x + w / 2f;
        float cy = y + h / 2f;
        float size = Math.min(w, h) / 2f;
        Render.line(cx + size / 2f, cy - size, cx - size / 2f, cy, 1.2f, colour);
        Render.line(cx - size / 2f, cy, cx + size / 2f, cy + size, 1.2f, colour);
    }

    static void tick(float x, float y, float w, float h, Color colour) {
        Render.line(x, y + h * 0.5f, x + w * 0.38f, y + h, 1.4f, colour);
        Render.line(x + w * 0.38f, y + h, x + w, y, 1.4f, colour);
    }

    /** A checkbox, filling with the accent and ticking as {@code on} goes 0 to 1. */
    static void checkbox(float x, float y, float w, float h, float on) {
        Render.roundRectOutline(x, y, w, h, 2f, 1f, faded(TEXT_FAINT, 1f - on));
        if (on > 0f) {
            Render.roundRect(x, y, w, h, 2f, faded(accent(), on));
            tick(x + 2f, y + 2.5f, w - 4f, h - 5f, faded(onAccent(accent()), on));
        }
    }

    static void magnifier(float x, float y, float w, float h, Color colour) {
        float radius = w * 0.34f;
        float cx = x + radius + 0.5f;
        float cy = y + radius + 0.5f;
        Render.circleOutline(cx, cy, radius, 1.2f, colour);
        Render.line(cx + radius * 0.7f, cy + radius * 0.7f, x + w, y + h, 1.2f, colour);
    }

    /**
     * A colour: opaque on the left so its hue reads at a glance, and as it really
     * is over a checkerboard on the right, so a translucent one reads as such.
     */
    static void swatch(float x, float y, float w, float h, Color colour) {
        float half = Math.round(w / 2f);
        Render.pushClip(x, y, w, h);
        Render.roundRect(x, y, w, h, 3f, colour.withAlpha(255));
        if (colour.getAlpha() < 255) {
            checker(x + half, y, w - half, h);
            Render.rect(x + half, y, w - half, h, colour);
        }
        Render.popClip();
        Render.roundRectOutline(x, y, w, h, 3f, 1f, LINE);
    }

    static void checker(float x, float y, float w, float h) {
        float cell = 3f;
        Render.rect(x, y, w, h, Color.of(90, 90, 96));
        Color light = Color.of(150, 150, 156);
        for (float cy = 0f; cy < h; cy += cell) {
            for (float cx = ((int) (cy / cell) % 2) * cell; cx < w; cx += cell * 2f) {
                Render.rect(x + cx, y + cy, Math.min(cell, w - cx), Math.min(cell, h - cy), light);
            }
        }
    }

    /** The surface every popup sits on: the highest one, with a soft shadow. */
    static void popupSurface(float x, float y, float w, float h) {
        Render.shadow(x, y, w, h, 10f, Color.of(0, 0, 0, 110));
        Render.roundRect(x, y, w, h, 6f, POPUP);
        Render.roundRectOutline(x, y, w, h, 6f, 1f, LINE);
    }

    /** A value on a raised chip with a chevron: the face of a dropdown. */
    static void chip(Content r, String text, Color colour) {
        r.row(chip -> chip.padding(6f, 2f).gap(5f).align(Align.CENTER)
                .background(RAISED, RADIUS_CONTROL)
                .text(body(), text, colour)
                .custom(6f, 6f, (x, y, w, h) -> chevron(x, y, w, h, 1f, TEXT_MUTED)));
    }

    /** "4.50" as "4.5" and "10.00" as "10": the digits that say something. */
    static String trimmed(String number) {
        if (number.indexOf('.') < 0) {
            return number;
        }
        String cut = number.replaceAll("0+$", "");
        return cut.endsWith(".") ? cut.substring(0, cut.length() - 1) : cut;
    }

    static String number(double value) {
        return trimmed(String.format(Locale.ROOT, "%.2f", value));
    }

    /** {@code #RRGGBB}, with the opacity after it when it is not opaque. */
    static String hex(Color colour) {
        String rgb = String.format("#%06X", colour.toARGB() & 0xFFFFFF);
        return colour.getAlpha() == 255 ? rgb : rgb + "  " + Math.round(colour.getAlpha() / 2.55f) + "%";
    }

    /** @return {@code text} broken between words into lines no wider than {@code width} */
    static java.util.List<String> wrap(String text, dev.px.core.render.font.Font font, float width) {
        java.util.List<String> lines = new java.util.ArrayList<>();
        StringBuilder line = new StringBuilder();
        for (String word : text.split(" ")) {
            String tried = line.length() == 0 ? word : line + " " + word;
            if (line.length() > 0 && font.widthOf(tried) > width) {
                lines.add(line.toString());
                line = new StringBuilder(word);
            } else {
                line = new StringBuilder(tried);
            }
        }
        if (line.length() > 0) {
            lines.add(line.toString());
        }
        return lines;
    }

    static float lerp(float from, float to, float t) {
        return from + (to - from) * t;
    }
}
