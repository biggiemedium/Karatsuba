package dev.px.karatsuba.client.gui.apple.shell.look;

import dev.px.core.layout.Align;
import dev.px.core.render.Color;
import dev.px.core.render.Render;
import dev.px.gui.render.WidgetRenderer;
import dev.px.gui.render.WidgetRendererRegistry;
import dev.px.karatsuba.client.gui.apple.shell.frame.GlassSurface;
import dev.px.karatsuba.client.gui.apple.shell.rail.Glyph;
import dev.px.karatsuba.client.gui.apple.shell.sidebar.SidebarEntry;
import dev.px.karatsuba.client.gui.apple.shell.sidebar.SidebarGap;
import dev.px.karatsuba.client.gui.apple.shell.sidebar.SidebarPane;
import dev.px.karatsuba.client.gui.apple.shell.sidebar.SidebarSearch;
import dev.px.karatsuba.client.gui.apple.shell.sidebar.SidebarScroll;

import static dev.px.karatsuba.client.gui.apple.shell.style.Palette.*;

/**
 * The sidebar ({@code gui/apple/DESIGN.md} §2.2). The pane names where its glass
 * goes and draws nothing itself, since the host draws the glass. Entries are a
 * coloured icon square and a name, filled with the accent while chosen and tinted
 * grey while hovered; the search is a pill. Text sits on glass, so it is vibrant.
 */
final class SidebarLook {

    private SidebarLook() {
    }

    static void register(WidgetRendererRegistry look) {
        look.register(WidgetRenderer.of(SidebarPane.class, (pane, c) -> c.align(Align.STRETCH)
                .column(glass -> glass.name(GlassSurface.PART)
                        .padding(INSET_SIDEBAR, SIDEBAR_PAD_TOP, INSET_SIDEBAR, INSET_SIDEBAR)
                        .slot())));

        // The entries' list: the general scroll look, with the room its bar keeps on the
        // right matched on the left while collapsed, so icons sit on the pane's centre line.
        look.register(WidgetRenderer.of(SidebarScroll.class, (scroll, c) -> {
            scroll.tick();
            float shown = scroll.scrollbar();
            float balance = scroll.isCollapsed() ? SCROLLBAR_WIDTH + 2f : 0f;
            c.row(r -> r.gap(2f).align(Align.STRETCH)
                    .column(list -> list.padding(balance, 0f, 0f, 0f).slot())
                    .custom("track", SCROLLBAR_WIDTH, 0f, (x, y, w, h) -> {
                        dev.px.core.layout.Bounds thumb = scroll.thumbIn(dev.px.core.layout.Bounds.of(x, y, w, h), 16f);
                        if (thumb != null && shown > 0.01f) {
                            Render.roundRect(thumb.getX(), thumb.getY(), thumb.getWidth(), thumb.getHeight(),
                                    thumb.getWidth() / 2f, faded(SCROLL_THUMB.get(), shown));
                        }
                    }));
        }));

        look.register(WidgetRenderer.of(SidebarGap.class, (gap, c) -> c.min(0f, SIDEBAR_GAP_GROUP).slot()));

        look.register(WidgetRenderer.of(SidebarEntry.class, (entry, c) -> {
            boolean selected = entry.isSelected();
            boolean collapsed = entry.isCollapsed();
            float hover = entry.hover();
            // The selection: a capsule in the control accent (darkened until white reaches
            // 3:1) with white text in the regular weight (step 8d). Never dark text here.
            Color accent = accentControl();
            Color ink = selected ? Color.WHITE : VIBRANT_LABEL.get();
            Color square = entry.iconColor().get();
            Glyph glyph = entry.glyph();
            dev.px.core.layout.Draw icon = (x, y, w, h) -> {
                Render.roundRect(x, y, w, h, R_ICON, square);
                Glyphs.draw(glyph, x + (w - ICON_GLYPH_SIZE) / 2f, y + (h - ICON_GLYPH_SIZE) / 2f,
                        ICON_GLYPH_SIZE, ICON_GLYPH);
            };
            if (collapsed) {
                // Icons alone: the icon centred in whatever width the row has (the list
                // keeps a little for its scroll bar), and the selection a pill centred on it.
                c.align(Align.STRETCH).row(r -> r
                        .height(SIDEBAR_ROW_HEIGHT).align(Align.CENTER)
                        .backdrop((x, y, w, h) -> {
                            float pill = Math.min(w, ICON_SIZE + SIDEBAR_ROW_INSET * 2f);
                            float px = x + (w - pill) / 2f;
                            if (selected) {
                                Render.roundRect(px, y, pill, h, h / 2f, accent);
                            } else if (hover > 0.01f) {
                                Render.roundRect(px, y, pill, h, h / 2f, faded(HOVER_TINT.get(), hover));
                            }
                        })
                        .fill()
                        .custom(ICON_SIZE, ICON_SIZE, icon)
                        .fill());
                return;
            }
            c.align(Align.STRETCH).row(r -> r
                    .height(SIDEBAR_ROW_HEIGHT).padding(SIDEBAR_ROW_INSET, 0f, SIDEBAR_ROW_INSET, 0f)
                    .gap(GAP_ICON_LABEL).align(Align.CENTER)
                    .backdrop((x, y, w, h) -> {
                        if (selected) {
                            Render.roundRect(x, y, w, h, h / 2f, accent);
                        } else if (hover > 0.01f) {
                            Render.roundRect(x, y, w, h, h / 2f, faded(HOVER_TINT.get(), hover));
                        }
                    })
                    .custom(ICON_SIZE, ICON_SIZE, icon)
                    .text(text(), entry.label(), ink)
                    .fill());
        }));

        look.register(WidgetRenderer.of(SidebarSearch.class, (field, c) -> {
            boolean focused = field.isFocused();
            Color accent = accent();
            c.align(Align.STRETCH).row(r -> r
                    .height(SEARCH_HEIGHT).padding(8f, 0f, 8f, 0f).gap(5f).align(Align.CENTER)
                    .backdrop((x, y, w, h) -> {
                        Render.roundRect(x, y, w, h, h / 2f, CONTROL_OFF.get());
                        if (focused) {
                            Render.roundRectOutline(x - 1f, y - 1f, w + 2f, h + 2f, h / 2f + 1f, 2f,
                                    accent.withAlpha(150));
                        }
                    })
                    .custom(9f, 9f, (x, y, w, h) -> Glyphs.draw(Glyph.SEARCH, x, y, w, VIBRANT_SECONDARY.get()))
                    .column(text -> text.name("text").grow().min(0f, text().getHeight()).backdrop((x, y, w, h) -> {
                        Render.pushClip(x, y, w, h);
                        boolean empty = field.getText().isEmpty();
                        float left = x - field.getScrollX();
                        if (field.hasSelection()) {
                            float from = field.offsetOf(field.getSelectionStart());
                            float to = field.offsetOf(field.getSelectionEnd());
                            Render.rect(left + from, y, to - from, h, accent.withAlpha(90));
                        }
                        Render.text(text(), empty ? field.getPlaceholder() : field.getText(), left, y,
                                empty ? VIBRANT_SECONDARY.get() : VIBRANT_LABEL.get());
                        if (focused && (System.currentTimeMillis() / 530L) % 2L == 0L) {
                            Render.rect(left + field.offsetOf(field.getCaret()), y, 1f, h, accent);
                        }
                        Render.popClip();
                    })));
        }));
    }
}
