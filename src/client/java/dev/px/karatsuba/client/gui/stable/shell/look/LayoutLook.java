package dev.px.karatsuba.client.gui.stable.shell.look;

import dev.px.core.layout.Align;
import dev.px.core.render.Render;
import dev.px.gui.render.WidgetRenderer;
import dev.px.gui.render.WidgetRendererRegistry;
import dev.px.karatsuba.client.gui.stable.shell.frame.ToolbarDock;
import dev.px.karatsuba.client.gui.stable.shell.rail.Glyph;
import dev.px.karatsuba.client.gui.stable.shell.section.layout.EditorToolbar;

import static dev.px.karatsuba.client.gui.stable.shell.style.Palette.*;

/** The layout editor's floating toolbar. What it draws over the game is the editor's own. */
final class LayoutLook {

    private LayoutLook() {
    }

    static void register(WidgetRendererRegistry look) {
        look.register(WidgetRenderer.of(ToolbarDock.class, (dock, c) -> c
                .padding(0f, ToolbarDock.MARGIN, 0f, 0f).slot()));

        // A floating bar: the highest surface, fully rounded at its ends.
        look.register(WidgetRenderer.of(EditorToolbar.class, (bar, c) -> c
                .backdrop((x, y, w, h) -> {
                    Render.shadow(x, y, w, h, 14f, dev.px.core.render.Color.of(0, 0, 0, 120));
                    Render.roundRect(x, y, w, h, h / 2f, POPUP);
                    Render.roundRectOutline(x, y, w, h, h / 2f, 1f, LINE);
                })
                .padding(5f, 4f)
                .slot()));

        look.register(WidgetRenderer.of(EditorToolbar.Status.class, (status, c) -> c.row(r -> r
                .height(ROW_HEIGHT).width(160f).padding(6f, 0f).align(Align.CENTER).clip()
                .text(small(), status.getText(), TEXT_MUTED))));

        look.register(WidgetRenderer.of(EditorToolbar.Divider.class, (divider, c) -> c
                .custom(9f, 14f, (x, y, w, h) -> Render.rect(x + w / 2f, y, 1f, h, LINE))));

        // The mode's name, with a pencil in the accent: always say which mode this is.
        look.register(WidgetRenderer.of(EditorToolbar.ModeLabel.class, (label, c) -> c.row(r -> r
                .height(ROW_HEIGHT).padding(6f, 0f).gap(5f).align(Align.CENTER)
                .custom(10f, 10f, (x, y, w, h) -> Glyphs.draw(Glyph.EDIT, x, y, w, accent()))
                .text(strong(), label.text(), TEXT))));
    }
}
