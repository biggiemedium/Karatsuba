package dev.px.karatsuba.client.gui.apple.shell.look;

import dev.px.core.layout.Align;
import dev.px.core.layout.Bounds;
import dev.px.core.render.Color;
import dev.px.core.render.Render;
import dev.px.gui.Widget;
import dev.px.gui.render.WidgetRenderer;
import dev.px.gui.render.WidgetRendererRegistry;
import dev.px.karatsuba.client.gui.apple.shell.card.Card;
import dev.px.karatsuba.client.gui.apple.shell.card.CardActions;
import dev.px.karatsuba.client.gui.apple.shell.card.CardHeader;
import dev.px.karatsuba.client.gui.apple.shell.card.CardPage;
import dev.px.karatsuba.client.gui.apple.shell.card.PushButton;

import java.util.List;

import static dev.px.karatsuba.client.gui.apple.shell.look.Shapes.raised;
import static dev.px.karatsuba.client.gui.apple.shell.look.Shapes.trailing;
import static dev.px.karatsuba.client.gui.apple.shell.style.Palette.*;

/**
 * Inset grouped lists, after Tahoe's System Settings ({@code gui/apple/DESIGN.md}
 * §2.4, §3.4; PLAN.md step 8b): borderless cards a shade off the window, rows
 * edge to edge with low-contrast hairlines from the label's edge, a hover
 * highlight whose corners follow the card's on its first and last rows, a
 * semibold header outside each card flush with its edge, push buttons, and a page
 * whose scroll bar shows only while it moves.
 */
final class CardLook {

    private CardLook() {
    }

    static void register(WidgetRendererRegistry look) {
        look.register(WidgetRenderer.of(Card.class, (card, c) -> c.align(Align.STRETCH).backdrop((x, y, w, h) -> {
            Render.roundRect(x, y, w, h, R_CARD, CARD.get());
            List<Widget> rows = card.rows();
            for (int i = 0; i < rows.size(); i++) {
                Bounds b = rows.get(i).getBounds();
                float lit = Card.highlight(rows.get(i));
                if (rows.get(i) instanceof dev.px.karatsuba.client.gui.apple.shell.setting.ControlRow row
                        && row.isSelected()) {
                    // The row open beside the list: a neutral highlight, never the accent.
                    highlight(x, b.getY(), w, b.getHeight(), i == 0, i == rows.size() - 1, SELECTED.get());
                } else if (lit > 0.01f) {
                    highlight(x, b.getY(), w, b.getHeight(), i == 0, i == rows.size() - 1,
                            faded(HOVER_TINT.get(), lit));
                }
                if (i > 0) {
                    // Between this row and the one above, from the label's edge to the card's.
                    Render.rect(x + CELL_INSET, b.getY(), w - CELL_INSET, 1f, SEPARATOR.get());
                }
            }
        }).slot()));

        // Semibold, outside the card and flush with its edge; a trailing action at the far end.
        look.register(WidgetRenderer.of(CardHeader.class, (header, c) -> {
            String action = header.action();
            float hover = action == null ? 0f : header.actionHover();
            c.align(Align.STRETCH).row(r -> {
                r.align(Align.CENTER).text(textStrong(), header.text(), LABEL.get()).fill();
                if (action != null) {
                    trailing(r, "action", action, hover);
                }
            });
        }));

        look.register(WidgetRenderer.of(PushButton.class, (button, c) -> {
            boolean pressed = button.isPressed();
            boolean focused = button.isFocused();
            c.row(r -> r.height(BUTTON_HEIGHT).padding(10f, 0f).align(Align.CENTER)
                    .backdrop((x, y, w, h) -> {
                        raised(x, y, w, h, R_BUTTON, pressed ? BUTTON_PRESSED.get() : BUTTON.get());
                        if (focused) {
                            Render.roundRectOutline(x - 1.5f, y - 1.5f, w + 3f, h + 3f, R_BUTTON + 1.5f, 2f,
                                    accent().withAlpha(150));
                        }
                    })
                    // A button with nothing to do keeps its face and dims its words.
                    .text(text(), button.label(), button.isEnabled() ? LABEL.get() : LABEL_TERTIARY.get()));
        }));

        // Right-aligned under their card, with a little room above.
        look.register(WidgetRenderer.of(CardActions.class, (actions, c) -> c.align(Align.STRETCH)
                .row(r -> r.padding(0f, GAP_ACTIONS - GAP_CARD_HEADER, 0f, 0f).fill().slot())));

        // The page: cards across the content column, a thin overlay bar at its right edge.
        look.register(WidgetRenderer.of(CardPage.class, (page, c) -> {
            page.tick();
            float shown = page.scrollbar();
            c.row(r -> r.align(Align.STRETCH).gap(3f)
                    .slot()
                    .custom("track", SCROLLBAR_WIDTH, 0f, (x, y, w, h) -> {
                        Bounds thumb = page.thumbIn(Bounds.of(x, y, w, Math.max(0f, h - INSET_WINDOW)), 16f);
                        if (thumb != null && shown > 0.01f) {
                            Render.roundRect(thumb.getX(), thumb.getY(), thumb.getWidth(), thumb.getHeight(),
                                    thumb.getWidth() / 2f, faded(SCROLL_THUMB.get(), shown));
                        }
                    }));
        }));
    }

    /** A row's highlight: square, except where it meets the card's own rounded corners. */
    private static void highlight(float x, float y, float w, float h, boolean first, boolean last, Color colour) {
        if (!first && !last) {
            Render.rect(x, y, w, h, colour);
            return;
        }
        if (first && last) {
            Render.roundRect(x, y, w, h, R_CARD, colour);
            return;
        }
        // A rounded rect run past the side away from the card's edge, cut back to the
        // row: rounded where the card is, square where the next row is. One shape,
        // so a translucent tint is never laid twice.
        float r = R_CARD;
        Render.pushClip(x, y, w, h);
        try {
            Render.roundRect(x, first ? y : y - r, w, h + r, r, colour);
        } finally {
            Render.popClip();
        }
    }
}
