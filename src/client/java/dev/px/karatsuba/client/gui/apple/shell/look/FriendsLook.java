package dev.px.karatsuba.client.gui.apple.shell.look;

import dev.px.core.Core;
import dev.px.core.layout.Align;
import dev.px.core.layout.Bounds;
import dev.px.core.render.Color;
import dev.px.core.render.Render;
import dev.px.core.render.font.Font;
import dev.px.gui.Widget;
import dev.px.gui.render.WidgetRenderer;
import dev.px.gui.render.WidgetRendererRegistry;
import dev.px.karatsuba.client.gui.apple.shell.rail.Glyph;
import dev.px.karatsuba.client.gui.apple.shell.section.friends.FriendHeader;
import dev.px.karatsuba.client.gui.apple.shell.section.friends.FriendRow;
import dev.px.karatsuba.client.gui.apple.shell.section.friends.SuggestionRow;
import dev.px.karatsuba.client.gui.apple.shell.section.friends.UndoBar;

import static dev.px.karatsuba.client.gui.apple.shell.look.Shapes.raised;
import static dev.px.karatsuba.client.gui.apple.shell.style.Palette.*;

/**
 * Friends ({@code gui/apple/DESIGN.md} §2.6b): friend rows and suggestions in their
 * cards, the first card of a friend's page, and the undo strip at the top of the
 * list. Rows sit in cards, so the card draws their highlight and hairlines.
 */
final class FriendsLook {

    private FriendsLook() {
    }

    static void register(WidgetRendererRegistry look) {
        // A friend: monogram, the name shown (their real name beside an alias), where they are, ›.
        look.register(WidgetRenderer.of(FriendRow.class, (row, c) -> {
            String real = row.realName();
            boolean online = row.isOnline();
            c.align(Align.STRETCH).row(r -> {
                r.height(CELL_HEIGHT).padding(CELL_INSET, 0f).gap(GAP_CONTROL).align(Align.CENTER)
                        .custom(AVATAR_SIZE, AVATAR_SIZE, (x, y, w, h) -> avatar(x, y, w, row.username(), online, caption()))
                        .text(text(), row.name(), LABEL.get());
                if (!real.isEmpty()) {
                    r.text(text(), real, LABEL_SECONDARY.get());
                }
                r.fill().text(text(), row.status(), LABEL_SECONDARY.get()).space(6f - GAP_CONTROL, 0f)
                        .custom(6f, 9f, (x, y, w, h) -> Glyphs.draw(Glyph.FORWARD, x - 1.5f, y, h, LABEL_TERTIARY.get()));
            });
        }));

        // A player to add: monogram, name, a few words, and an Add button.
        look.register(WidgetRenderer.of(SuggestionRow.class, (row, c) -> {
            boolean lit = lit(row, "add");
            c.align(Align.STRETCH).row(r -> {
                r.height(CELL_HEIGHT).padding(CELL_INSET, 0f).gap(GAP_CONTROL).align(Align.CENTER)
                        .custom(AVATAR_SIZE, AVATAR_SIZE, (x, y, w, h) -> avatar(x, y, w, row.name(), false, caption()))
                        .text(text(), row.name(), LABEL.get());
                if (!row.caption().isEmpty()) {
                    r.text(text(), row.caption(), LABEL_SECONDARY.get());
                }
                r.fill().row(button -> button.name("add").height(BUTTON_HEIGHT).padding(10f, 0f).align(Align.CENTER)
                        .backdrop((x, y, w, h) -> raised(x, y, w, h, R_BUTTON, lit ? BUTTON_PRESSED.get() : BUTTON.get()))
                        .text(text(), "Add", LABEL.get()));
            });
        }));

        // A friend's page leads with them, as an account card leads a System Settings page.
        look.register(WidgetRenderer.of(FriendHeader.class, (header, c) -> c.align(Align.STRETCH).row(r -> r
                .padding(CELL_INSET, 10f).gap(GAP_CONTROL + 4f).align(Align.CENTER)
                .custom(AVATAR_LARGE, AVATAR_LARGE, (x, y, w, h) ->
                        avatar(x, y, w, header.username(), header.isOnline(), heading()))
                .column(names -> {
                    names.gap(3f).text(textStrong(), header.name(), LABEL.get());
                    String under = header.hasAlias() ? header.username() + "  ·  " + header.status() : header.status();
                    names.text(caption(), under, LABEL_SECONDARY.get());
                }))));

        // "Removed Steve · Undo": a solid strip at the top of the list, rising into place.
        look.register(WidgetRenderer.of(UndoBar.class, (bar, c) -> {
            float arrival = bar.arrival();
            boolean lit = lit(bar, "undo");
            c.align(Align.STRETCH).row(r -> r.height(CELL_HEIGHT + 4f).padding(CELL_INSET, 0f, CELL_INSET - 4f, 0f)
                    .gap(GAP_CONTROL).align(Align.CENTER)
                    .backdrop((x, y, w, h) -> {
                        float lift = (1f - arrival) * 6f;
                        Render.shadow(x, y + lift + 1f, w, h, R_CARD, faded(SHADOW, arrival));
                        Render.roundRect(x, y + lift, w, h, R_CARD, faded(POPOVER.get(), arrival));
                    })
                    .text(text(), bar.message(), faded(LABEL.get(), arrival))
                    .fill()
                    .row(undo -> undo.name("undo").height(BUTTON_HEIGHT).padding(10f, 0f).align(Align.CENTER)
                            .backdrop((x, y, w, h) -> {
                                if (arrival > 0.99f) {
                                    raised(x, y, w, h, R_BUTTON, lit ? BUTTON_PRESSED.get() : BUTTON.get());
                                }
                            })
                            .text(text(), "Undo", faded(LABEL.get(), arrival))));
        }));
    }

    /**
     * A player's monogram: their initial in white on a neutral circle, as Contacts
     * draws one; a green dot at its corner while they are online.
     */
    private static void avatar(float x, float y, float size, String name, boolean online, Font font) {
        float r = size / 2f;
        Render.circle(x + r, y + r, r, AVATAR.get());
        String initial = name.isEmpty() ? "?" : name.substring(0, 1).toUpperCase(java.util.Locale.ROOT);
        Render.text(font, initial, x + r - font.widthOf(initial) / 2f, y + r - font.getHeight() / 2f, Color.WHITE);
        if (online) {
            float dot = Math.max(2.5f, size * 0.15f);
            float cx = x + size - dot;
            float cy = y + size - dot;
            Render.circle(cx, cy, dot + 1.2f, CARD.get());
            Render.circle(cx, cy, dot, PRESENCE);
        }
    }

    /** @return whether the cursor is over the widget's named part */
    private static boolean lit(Widget widget, String part) {
        Bounds bounds = widget.part(part);
        return bounds != null && widget.isHovered()
                && bounds.contains(Core.platform().getMouseX(), Core.platform().getMouseY());
    }
}
