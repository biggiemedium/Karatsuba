package dev.px.karatsuba.client.gui.apple.shell.look;

import dev.px.core.layout.Align;
import dev.px.core.render.Color;
import dev.px.core.render.Render;
import dev.px.gui.render.WidgetRenderer;
import dev.px.gui.render.WidgetRendererRegistry;
import dev.px.karatsuba.client.gui.apple.shell.section.home.HomeCard;
import dev.px.karatsuba.client.gui.apple.shell.section.home.HomeHeader;
import dev.px.karatsuba.client.gui.apple.shell.section.home.HomeSection;
import dev.px.karatsuba.client.gui.apple.shell.section.home.ResultRow;

import static dev.px.karatsuba.client.gui.apple.shell.look.Shapes.chevron;
import static dev.px.karatsuba.client.gui.apple.shell.style.Palette.*;

/** Home: its header of session chips, the section cards, and search results. */
final class HomeLook {

    /** Every card the same height, so the grid reads as one even block. */
    private static final float CARD_HEIGHT = 74f;
    private static final float RADIUS_CARD = 6f;

    private HomeLook() {
    }

    static void register(WidgetRendererRegistry look) {
        look.register(WidgetRenderer.of(HomeSection.HomeView.class, (view, c) -> c
                .padding(12f, 12f, 12f, INSET)
                .slot()));

        // The greeting at the title's height, and the session's chips on the right.
        look.register(WidgetRenderer.of(HomeHeader.class, (header, c) -> c.align(Align.STRETCH).row(r -> {
            r.height(TITLE_HEIGHT).padding(INSET, 0f, 0f, 0f).gap(4f).align(Align.CENTER)
                    .text(title(), header.greeting(), LABEL.get())
                    .fill();
            for (HomeHeader.Chip chip : header.chips()) {
                Color dot = switch (chip.tone()) {
                    case GOOD -> ONLINE;
                    case WARN -> WARN;
                    case BAD -> Color.of(232, 96, 96);
                    case NEUTRAL -> LABEL_TERTIARY.get();
                };
                r.row(pill -> pill.padding(7f, 3f).gap(5f).align(Align.CENTER)
                        .backdrop((x, y, w, h) -> Render.roundRect(x, y, w, h, h / 2f, SELECTED.get()))
                        .custom(5f, 5f, (x, y, w, h) -> Render.circle(x + w / 2f, y + h / 2f, w / 2f, dot))
                        .text(small(), chip.text(), LABEL_SECONDARY.get()));
            }
        })));

        look.register(WidgetRenderer.of(HomeCard.class, (card, c) -> {
            float hover = card.hover();
            Float bar = card.bar();
            String action = card.actionLabel();
            c.align(Align.STRETCH).min(0f, CARD_HEIGHT).clip().padding(10f, 9f)
                    .backdrop((x, y, w, h) -> {
                        Render.roundRect(x, y, w, h, RADIUS_CARD, CARD.get());
                        Render.roundRectOutline(x, y, w, h, RADIUS_CARD, 1f, faded(accentTint(110), hover));
                    })
                    .column(body -> {
                        body.align(Align.STRETCH).row(top -> {
                            top.align(Align.CENTER).gap(6f)
                                    .custom(10f, 10f, (x, y, w, h) ->
                                            Glyphs.draw(card.glyph(), x, y, w, LABEL_SECONDARY.get().lerp(accent(), hover)))
                                    .text(strong(), card.title(), LABEL_SECONDARY.get())
                                    .fill();
                            if (action != null) {
                                top.row(chip -> chip.name("action").padding(6f, 2f)
                                        .background(SELECTED.get(), RADIUS_CONTROL)
                                        .text(small(), action, LABEL_SECONDARY.get()));
                            } else {
                                top.custom(6f, 6f, (x, y, w, h) -> chevron(x, y, w, h, 0f, faded(LABEL_SECONDARY.get(), 0.4f + 0.6f * hover)));
                            }
                        });
                        body.space(0f, 7f).text(title(), card.metric(), LABEL.get());
                        // The same three rows on every card: the number, a strip, the detail.
                        float fill = bar == null ? 0f : Math.max(0f, Math.min(1f, bar));
                        body.space(0f, 5f).custom(0f, 3f, (x, y, w, h) -> {
                            if (card.isSwatch()) {
                                Render.roundGradient(x, y, w, h, 1.5f, accent(0f), accent(1f), accent(1f), accent(0f));
                            } else if (bar != null) {
                                Render.roundRect(x, y, w, h, 1.5f, CONTROL_OFF.get());
                                if (fill > 0f) {
                                    Render.roundRect(x, y, Math.max(h, w * fill), h, 1.5f, accent());
                                }
                            }
                        });
                        body.space(0f, 5f).text(small(), card.detail(), LABEL_TERTIARY.get());
                    });
        }));

        look.register(WidgetRenderer.of(ResultRow.class, (row, c) -> {
            float hover = row.hover();
            c.align(Align.STRETCH).row(r -> r.height(ROW_HEIGHT + 2f).padding(10f, 0f, INSET, 0f).gap(6f).align(Align.CENTER)
                    .backdrop((x, y, w, h) -> Render.roundRect(x, y, w, h, RADIUS_CONTROL, faded(HOVER_TINT.get(), hover)))
                    .text(body(), row.name(), LABEL_SECONDARY.get().lerp(LABEL.get(), hover))
                    .fill()
                    .text(small(), row.where(), LABEL_TERTIARY.get())
                    .custom(6f, 6f, (x, y, w, h) -> chevron(x, y, w, h, 0f, faded(LABEL_SECONDARY.get(), 0.5f + 0.5f * hover))));
        }));
    }
}
