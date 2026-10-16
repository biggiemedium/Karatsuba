package dev.px.karatsuba.client.gui.stable.shell.look;

import dev.px.core.layout.Align;
import dev.px.core.render.Color;
import dev.px.core.render.Render;
import dev.px.gui.render.WidgetRenderer;
import dev.px.gui.render.WidgetRendererRegistry;
import dev.px.karatsuba.client.gui.stable.shell.rail.Brand;
import dev.px.karatsuba.client.gui.stable.shell.rail.Rail;
import dev.px.karatsuba.client.gui.stable.shell.rail.RailItem;

import static dev.px.karatsuba.client.gui.stable.shell.style.Palette.*;

/** The navigation rail: its surface, the client's mark, and an item per section. */
final class RailLook {

    /** The chosen item's pill, behind its icon. */
    private static final float PILL_WIDTH = 32f;
    private static final float PILL_HEIGHT = 18f;
    private static final float ICON = 12f;

    private RailLook() {
    }

    static void register(WidgetRendererRegistry look) {
        // One step up from the panel, rounded on the window's side, a hairline where it meets the section.
        look.register(WidgetRenderer.of(Rail.class, (rail, c) -> c
                .min(rail.width(), 0f)
                .padding(4f, 12f, 4f, INSET)
                .backdrop((x, y, w, h) -> {
                    Render.roundRect(x, y, w, h, RADIUS_PANEL, SIDEBAR);
                    Render.rect(x + w - RADIUS_PANEL, y, RADIUS_PANEL, h, SIDEBAR);
                    Render.rect(x + w - 1f, y, 1f, h, LINE);
                })
                .slot()));

        // The client's initial on a tile of the theme's gradient.
        look.register(WidgetRenderer.of(Brand.class, (brand, c) -> c.align(Align.STRETCH)
                .row(r -> r.align(Align.CENTER).fill()
                        .custom(22f, 22f, (x, y, w, h) -> {
                            Render.roundGradient(x, y, w, h, 6f, accent(0f), accent(1f), accent(1f), accent(0f));
                            String initial = brand.initial();
                            Render.text(title(), initial, x + (w - title().widthOf(initial)) / 2f,
                                    y + (h - title().getHeight()) / 2f, onAccent(accent()));
                        })
                        .fill())));

        look.register(WidgetRenderer.of(RailItem.class, (item, c) -> {
            float hover = item.hover();
            float chosen = item.chosen();
            boolean labelled = item.labelled();
            Color ink = TEXT_MUTED.lerp(TEXT, Math.max(hover, chosen));
            Color icon = TEXT_MUTED.lerp(accent(), chosen).lerp(TEXT, hover * (1f - chosen));
            c.align(Align.STRETCH).column(column -> {
                column.align(Align.CENTER).padding(0f, 4f, 0f, labelled ? 4f : 2f).gap(3f)
                        .custom(PILL_WIDTH, PILL_HEIGHT, (x, y, w, h) -> {
                            // The pill grows out from the middle as the item is chosen.
                            float grow = 0.6f + 0.4f * chosen;
                            float pw = w * grow;
                            float ph = h * grow;
                            Render.roundRect(x + (w - pw) / 2f, y + (h - ph) / 2f, pw, ph, ph / 2f,
                                    faded(accentTint(56), chosen));
                            Render.roundRect(x, y, w, h, h / 2f, faded(HOVER, hover * 1.5f));
                            Glyphs.draw(item.section().glyph(), x + (w - ICON) / 2f, y + (h - ICON) / 2f, ICON, icon);
                        });
                if (labelled) {
                    column.text(small(), item.section().title(), ink);
                }
            });
        }));
    }
}
