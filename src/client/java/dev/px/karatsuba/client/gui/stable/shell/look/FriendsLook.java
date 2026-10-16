package dev.px.karatsuba.client.gui.stable.shell.look;

import dev.px.core.layout.Align;
import dev.px.core.render.Color;
import dev.px.core.render.Render;
import dev.px.core.render.font.Font;
import dev.px.gui.render.WidgetRenderer;
import dev.px.gui.render.WidgetRendererRegistry;
import dev.px.karatsuba.client.gui.stable.shell.rail.Glyph;
import dev.px.karatsuba.client.gui.stable.shell.section.friends.AddRow;
import dev.px.karatsuba.client.gui.stable.shell.section.friends.FriendHeader;
import dev.px.karatsuba.client.gui.stable.shell.section.friends.FriendRow;
import dev.px.karatsuba.client.gui.stable.shell.section.friends.SuggestionRow;
import dev.px.karatsuba.client.gui.stable.shell.section.friends.UndoBar;

import static dev.px.karatsuba.client.gui.stable.shell.style.Palette.*;

/** The Friends section: friend rows, suggestions, the add row, the undo bar and the inspector's heading. */
final class FriendsLook {

    private static final float AVATAR = 16f;
    private static final float AVATAR_LARGE = 28f;

    private FriendsLook() {
    }

    static void register(WidgetRendererRegistry look) {
        look.register(WidgetRenderer.of(FriendRow.class, (row, c) -> {
            boolean selected = row.isSelected();
            float hover = row.hover();
            float flash = row.flash();
            String real = row.realName();
            c.align(Align.STRETCH).row(r -> r.height(real.isEmpty() ? ROW_HEIGHT + 4f : ROW_HEIGHT + 10f)
                    .padding(6f, 0f, 6f, 0f).gap(7f).align(Align.CENTER)
                    .backdrop((x, y, w, h) -> {
                        Render.roundRect(x, y, w, h, RADIUS_CONTROL, selected ? RAISED : faded(HOVER, hover));
                        if (flash > 0f) {
                            Render.roundRect(x, y, w, h, RADIUS_CONTROL, accentTint(Math.round(60 * flash)));
                        }
                    })
                    .custom(AVATAR, AVATAR, (x, y, w, h) -> avatar(x, y, w, row.name(), row.isOnline(), body()))
                    .column(names -> {
                        names.gap(3f).text(body(), row.display(), selected || row.isOnline() ? TEXT : TEXT_MUTED);
                        if (!real.isEmpty()) {
                            names.text(small(), real, TEXT_FAINT);
                        }
                    })
                    .fill()
                    .text(small(), row.status(), row.isOnline() ? TEXT_MUTED : TEXT_FAINT)
                    // The cross to remove: only on hover, so the list stays calm.
                    .custom("remove", 9f, 9f, (x, y, w, h) -> {
                        if (hover > 0.01f) {
                            Glyphs.draw(Glyph.REMOVE, x, y, w, faded(TEXT_MUTED, hover));
                        }
                    }));
        }));

        look.register(WidgetRenderer.of(SuggestionRow.class, (row, c) -> {
            float hover = row.hover();
            c.align(Align.STRETCH).row(r -> r.height(ROW_HEIGHT + 4f).padding(6f, 0f, 8f, 0f).gap(7f).align(Align.CENTER)
                    .backdrop((x, y, w, h) -> Render.roundRect(x, y, w, h, RADIUS_CONTROL, faded(HOVER, hover)))
                    .custom(AVATAR, AVATAR, (x, y, w, h) -> avatar(x, y, w, row.name(), true, body()))
                    .text(body(), row.name(), TEXT_MUTED.lerp(TEXT, hover))
                    .fill()
                    .row(add -> add.padding(5f, 2f).gap(3f).align(Align.CENTER)
                            .background(RAISED.lerp(accentTint(70), hover), RADIUS_CONTROL)
                            .custom(7f, 7f, (x, y, w, h) -> Glyphs.draw(Glyph.ADD, x, y, w, TEXT_MUTED.lerp(TEXT, hover)))
                            .text(small(), "Add", TEXT_MUTED.lerp(TEXT, hover))));
        }));

        look.register(WidgetRenderer.of(AddRow.class, (row, c) -> {
            float hover = row.hover();
            c.align(Align.STRETCH).row(r -> r.height(ROW_HEIGHT + 4f).padding(8f, 0f).gap(7f).align(Align.CENTER)
                    .backdrop((x, y, w, h) -> {
                        Render.roundRect(x, y, w, h, RADIUS_CONTROL, accentTint(Math.round(28 + 30 * hover)));
                        Render.roundRectOutline(x, y, w, h, RADIUS_CONTROL, 1f, accentTint(90));
                    })
                    .custom(9f, 9f, (x, y, w, h) -> Glyphs.draw(Glyph.ADD, x, y, w, accent()))
                    .text(body(), "Add " + row.name(), TEXT)
                    .fill()
                    .text(small(), "Enter", TEXT_FAINT));
        }));

        look.register(WidgetRenderer.of(UndoBar.class, (bar, c) -> {
            float arrival = bar.arrival();
            c.align(Align.STRETCH).row(r -> r.height(ROW_HEIGHT + 4f).padding(8f, 0f, 4f, 0f).gap(6f).align(Align.CENTER)
                    .backdrop((x, y, w, h) -> {
                        float lift = (1f - arrival) * 6f;
                        Render.roundRect(x, y + lift, w, h, RADIUS_CONTROL, faded(POPUP, arrival));
                        Render.roundRectOutline(x, y + lift, w, h, RADIUS_CONTROL, 1f, faded(LINE, arrival));
                    })
                    .text(small(), bar.message(), faded(TEXT, arrival))
                    .fill()
                    .row(undo -> undo.name("undo").padding(6f, 2f).background(faded(RAISED_HIGH, arrival), RADIUS_CONTROL)
                            .text(small(), "Undo", faded(accent(), arrival))));
        }));

        look.register(WidgetRenderer.of(FriendHeader.class, (header, c) -> c.align(Align.STRETCH).row(r -> r
                .gap(10f).align(Align.CENTER).padding(0f, 0f, 0f, 2f)
                .custom(AVATAR_LARGE, AVATAR_LARGE, (x, y, w, h) -> avatar(x, y, w, header.name(), header.isOnline(), title()))
                .column(names -> {
                    names.gap(4f).text(title(), header.display(), TEXT);
                    String under = header.hasAlias() ? header.name() + "  ·  " + header.status() : header.status();
                    names.text(small(), under, header.isOnline() ? TEXT_MUTED : TEXT_FAINT);
                }))));
    }

    /**
     * A player's initial on a circle of their own colour, from their name, so the
     * same player always looks the same; a dot at its corner while they are online.
     */
    private static void avatar(float x, float y, float size, String name, boolean online, Font font) {
        float r = size / 2f;
        float hue = (name.toLowerCase().hashCode() & 0xFFFF) / 65535f;
        Render.circle(x + r, y + r, r, Color.hsb(hue, 0.32f, 0.46f));
        String initial = name.isEmpty() ? "?" : name.substring(0, 1).toUpperCase();
        Render.text(font, initial, x + r - font.widthOf(initial) / 2f, y + r - font.getHeight() / 2f, TEXT);
        if (online) {
            float dot = Math.max(2.5f, size * 0.16f);
            float cx = x + size - dot;
            float cy = y + size - dot;
            Render.circle(cx, cy, dot + 1.2f, BASE.withAlpha(255));
            Render.circle(cx, cy, dot, ONLINE);
        }
    }
}
