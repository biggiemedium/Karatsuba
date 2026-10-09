package dev.px.karatsuba.client.gui.shell.look;

import dev.px.core.layout.Align;
import dev.px.core.render.Render;
import dev.px.gui.render.WidgetRenderer;
import dev.px.gui.render.WidgetRendererRegistry;
import dev.px.karatsuba.client.gui.shell.section.modules.CategoryTab;

import static dev.px.karatsuba.client.gui.shell.style.Palette.*;

/** What only the Modules section has: its category tabs. */
final class ModulesLook {

    private ModulesLook() {
    }

    static void register(WidgetRendererRegistry look) {
        look.register(WidgetRenderer.of(CategoryTab.class, (tab, c) -> {
            boolean selected = tab.isSelected();
            float hover = tab.hover();
            float chosen = tab.selection();
            int count = tab.enabledCount();
            c.align(Align.STRETCH).row(r -> r.height(ROW_HEIGHT).padding(INSET, 0f).align(Align.CENTER)
                    .backdrop((x, y, w, h) -> {
                        Render.roundRect(x, y, w, h, RADIUS_CONTROL, faded(HOVER, hover));
                        Render.roundRect(x, y, w, h, RADIUS_CONTROL, faded(RAISED, chosen));
                        // The accent bar grows from the middle as the tab is chosen.
                        float bar = (h - 10f) * chosen;
                        if (bar > 0.5f) {
                            Render.roundRect(x, y + (h - bar) / 2f, 2f, bar, 1f, accent());
                        }
                    })
                    .text(selected ? strong() : body(), tab.category().getName(), TEXT_MUTED.lerp(TEXT, chosen))
                    .fill()
                    .text(small(), count > 0 ? String.valueOf(count) : "", selected ? accent() : TEXT_FAINT));
        }));
    }
}
