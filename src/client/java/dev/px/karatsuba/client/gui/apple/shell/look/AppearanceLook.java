package dev.px.karatsuba.client.gui.apple.shell.look;

import dev.px.core.Core;
import dev.px.core.layout.Align;
import dev.px.core.layout.Bounds;
import dev.px.core.render.Color;
import dev.px.core.render.Render;
import dev.px.core.render.theme.Theme;
import dev.px.gui.render.WidgetRenderer;
import dev.px.gui.render.WidgetRendererRegistry;
import dev.px.karatsuba.client.gui.apple.shell.section.appearance.ThemeCircles;

import java.util.List;

import static dev.px.karatsuba.client.gui.apple.shell.style.Palette.*;

/**
 * The Appearance page's own pieces ({@code gui/apple/DESIGN.md} §4.6, §7): the theme
 * circles, each split diagonally into its theme's two colours, the chosen one ringed
 * in the label colour 2 off its edge; a circle under the cursor lifts its ring a
 * little. The only gradient-like thing in the UI, and only because it shows a theme.
 */
final class AppearanceLook {

    /** Wedges per half circle: enough that the diagonal half reads as round. */
    private static final int WEDGES = 18;

    private AppearanceLook() {
    }

    static void register(WidgetRendererRegistry look) {
        look.register(WidgetRenderer.of(ThemeCircles.class, (row, c) -> {
            List<Theme> themes = row.themes();
            int perLine = row.perLine();
            String chosen = Core.themes().getActive().getName();
            c.align(Align.STRETCH).column(column -> {
                column.align(Align.STRETCH).padding(CELL_INSET, 0f, CELL_INSET, 8f);
                column.row(top -> top.height(CELL_HEIGHT).align(Align.CENTER)
                        .text(text(), row.name(), LABEL.get())
                        .fill()
                        .text(text(), chosen, LABEL_SECONDARY.get()));
                for (int start = 0; start < themes.size(); start += perLine) {
                    int from = start;
                    int to = Math.min(themes.size(), start + perLine);
                    column.row(line -> {
                        line.gap(GAP_THEME).padding(0f, 3f);
                        for (int i = from; i < to; i++) {
                            Theme theme = themes.get(i);
                            boolean on = row.isChosen(theme);
                            String part = "theme" + i;
                            line.custom(part, THEME_CIRCLE, THEME_CIRCLE, (x, y, w, h) -> circle(row, part,
                                    x + w / 2f, y + h / 2f, w / 2f, theme, on));
                        }
                    });
                }
            });
        }));
    }

    /** One theme: secondary underneath, primary over the top-left half, a hairline round it, a ring if chosen. */
    private static void circle(ThemeCircles row, String part, float cx, float cy, float r, Theme theme, boolean chosen) {
        Color primary = theme.accent(0f).withAlpha(255);
        Color secondary = theme.accent(1f).withAlpha(255);
        Render.circle(cx, cy, r, secondary);
        // The top-left half, a fan of wedges from the centre, between 135 and 315 degrees.
        double from = Math.PI * 0.75d;
        double step = Math.PI / WEDGES;
        for (int i = 0; i < WEDGES; i++) {
            double a = from + step * i;
            double b = a + step;
            Render.triangle(cx, cy,
                    cx + (float) Math.cos(a) * r, cy + (float) Math.sin(a) * r,
                    cx + (float) Math.cos(b) * r, cy + (float) Math.sin(b) * r, primary);
        }
        Render.circleOutline(cx, cy, r, 1f, SEPARATOR.get());
        if (chosen) {
            Render.circleOutline(cx, cy, r + 3f, 1.5f, LABEL.get());
        } else if (over(row, part)) {
            Render.circleOutline(cx, cy, r + 3f, 1f, LABEL_TERTIARY.get());
        }
    }

    private static boolean over(ThemeCircles row, String part) {
        Bounds bounds = row.part(part);
        return bounds != null && row.isHovered()
                && bounds.contains(Core.platform().getMouseX(), Core.platform().getMouseY());
    }
}
