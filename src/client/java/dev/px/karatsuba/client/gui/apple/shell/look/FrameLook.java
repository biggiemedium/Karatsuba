package dev.px.karatsuba.client.gui.apple.shell.look;

import dev.px.core.layout.Align;
import dev.px.core.layout.Bounds;
import dev.px.core.layout.Shape;
import dev.px.core.render.Color;
import dev.px.core.render.Render;
import dev.px.gui.container.Scroll;
import dev.px.gui.render.WidgetRenderer;
import dev.px.gui.render.WidgetRendererRegistry;
import dev.px.gui.widget.Tooltip;
import dev.px.karatsuba.client.gui.apple.shell.frame.Fold;
import dev.px.karatsuba.client.gui.apple.shell.frame.ModuleColumn;
import dev.px.karatsuba.client.gui.apple.shell.frame.Panel;
import dev.px.karatsuba.client.gui.apple.shell.frame.ResizeGrip;
import dev.px.karatsuba.client.gui.apple.shell.frame.SettingsColumn;
import dev.px.karatsuba.client.gui.apple.shell.frame.Sidebar;
import dev.px.karatsuba.client.gui.apple.shell.frame.SmoothScroll;
import dev.px.karatsuba.client.gui.apple.shell.frame.FixedColumn;
import dev.px.karatsuba.client.gui.apple.shell.frame.Spacer;
import dev.px.karatsuba.client.gui.apple.shell.frame.Gutter;
import dev.px.karatsuba.client.gui.apple.shell.frame.Veil;
import dev.px.karatsuba.client.gui.apple.shell.widget.Caption;
import dev.px.karatsuba.client.gui.apple.shell.style.Animations;

import static dev.px.karatsuba.client.gui.apple.shell.style.Palette.*;

/** The window, its columns, its resize handles, scrolling lists, captions and tooltips. */
final class FrameLook {

    /** The tooltip last drawn, and when it appeared, for its fade. */
    private static String tooltipText;
    private static long tooltipShown;
    private static long tooltipDrawn;

    private FrameLook() {
    }

    static void register(WidgetRendererRegistry look) {
        // The window's material is the host's pass (rule 1.4); the panel adds only its edge.
        look.register(WidgetRenderer.of(Panel.class, (panel, c) -> c.backdrop((x, y, w, h) ->
                Render.roundRectOutline(x, y, w, h, R_WINDOW, 1f, SEPARATOR.get())).slot()));

        look.register(WidgetRenderer.of(Sidebar.class, (sidebar, c) -> c
                .min(sidebar.width(), 0f)
                .padding(INSET, 12f, INSET, INSET)
                .backdrop((x, y, w, h) -> Render.rect(x + w - 1f, y, 1f, h, SEPARATOR.get()))
                .slot()));

        look.register(WidgetRenderer.of(ModuleColumn.class, (column, c) -> c
                .min(column.width(), 0f)
                // Flush with the content column's left edge and top, where the header lines up.
                .padding(0f, 0f, INSET, INSET)
                .backdrop((x, y, w, h) -> Render.rect(x + w - 1f, y, 1f, h, SEPARATOR.get()))
                .slot()));

        look.register(WidgetRenderer.of(SettingsColumn.class, (column, c) -> c
                .padding(12f, 0f, 0f, INSET)
                .slot()));

        // Group children sit a step in, along a faint guide, so it is plain which heading they are under.
        look.register(WidgetRenderer.of(Fold.class, (fold, c) -> c
                .padding(6f, 0f, 0f, 0f)
                .backdrop((x, y, w, h) -> Render.rect(x + 1f, y + 2f, 1f, Math.max(0f, h - 4f),
                        faded(SEPARATOR.get(), fold.openness())))
                .slot()));

        look.register(WidgetRenderer.of(Spacer.class, (spacer, c) -> c.min(0f, spacer.height()).slot()));
        look.register(WidgetRenderer.of(FixedColumn.class,
                (column, c) -> c.min(column.width(), 0f).slot()));
        look.register(WidgetRenderer.of(Gutter.class, (gutter, c) -> c.min(gutter.width(), 0f).slot()));

        // A list with a thin bar beside it, shown only when there is more than fits.
        look.register(WidgetRenderer.of(Scroll.class, (scroll, c) -> {
            if (scroll instanceof SmoothScroll smooth) {
                smooth.tick();
            }
            // An overlay bar: hidden at rest, shown while the list moves (step 8b).
            float shown = scroll instanceof SmoothScroll smooth ? smooth.scrollbar() : 1f;
            c.row(r -> r.gap(2f).align(Align.STRETCH)
                    .slot()
                    .custom("track", SCROLLBAR_WIDTH, 0f, (x, y, w, h) -> {
                        Bounds thumb = scroll.thumbIn(Bounds.of(x, y, w, h), 16f);
                        if (thumb != null && shown > 0.01f) {
                            Render.roundRect(thumb.getX(), thumb.getY(), thumb.getWidth(), thumb.getHeight(),
                                    thumb.getWidth() / 2f, faded(SCROLL_THUMB.get(), shown));
                        }
                    }));
        }));

        look.register(WidgetRenderer.of(Caption.class, (caption, c) -> c
                .padding(INSET, 6f)
                .paragraph(small(), caption.getText(), LABEL_TERTIARY.get())));

        // Tooltips fade in over 120ms after the hover delay, rather than popping.
        look.register(WidgetRenderer.of(Tooltip.class, (tip, c) -> {
            float shown = tooltipFade(tip.getText());
            c.backdrop((x, y, w, h) -> {
                Render.roundRect(x, y, w, h, R_BUTTON, faded(POPOVER.get(), shown));
                Render.roundRectOutline(x, y, w, h, R_BUTTON, 1f, faded(BUTTON_EDGE.get(), shown));
            }).padding(6f, 4f).paragraph(small(), tip.getText(), faded(LABEL.get(), shown));
        }));

        // The corner shows two short diagonal strokes; the edges show nothing until hovered.
        look.register(WidgetRenderer.of(ResizeGrip.class, (grip, c) -> {
            float lit = grip.lit();
            c.backdrop((x, y, w, h) -> {
                if (grip.edge() == ResizeGrip.Edge.CORNER) {
                    Color colour = LABEL_TERTIARY.get().lerp(LABEL_SECONDARY.get(), lit);
                    float right = x + w - 3f;
                    float bottom = y + h - 3f;
                    Render.line(right - 6f, bottom, right, bottom - 6f, 1f, colour);
                    Render.line(right - 2.5f, bottom, right, bottom - 2.5f, 1f, colour);
                } else if (lit > 0f) {
                    boolean vertical = grip.edge() == ResizeGrip.Edge.RIGHT;
                    Color colour = faded(accent(), lit * 0.7f);
                    if (vertical) {
                        Render.roundRect(x + w - 2f, y, 2f, h, 1f, colour);
                    } else {
                        Render.roundRect(x, y + h - 2f, w, 2f, 1f, colour);
                    }
                }
            });
        }));

        // Never hit: clicks go through to the content it is uncovering.
        look.register(WidgetRenderer.of(Veil.class,
                (veil, c) -> c.backdrop((x, y, w, h) -> Render.rect(x, y, w, h, faded(PANE_SOLID.get(), veil.cover()))),
                (veil, bounds) -> Shape.rect(0f, 0f, 0f, 0f)));
    }

    /** @return the tooltip's opacity: rising over 120ms from when this text first showed */
    private static float tooltipFade(String text) {
        long now = System.nanoTime();
        boolean fresh = !text.equals(tooltipText) || now - tooltipDrawn > 100_000_000L;
        if (fresh) {
            tooltipText = text;
            tooltipShown = now;
        }
        tooltipDrawn = now;
        return Animations.enabled() ? Animations.progress(tooltipShown, Animations.HOVER_IN) : 1f;
    }
}
