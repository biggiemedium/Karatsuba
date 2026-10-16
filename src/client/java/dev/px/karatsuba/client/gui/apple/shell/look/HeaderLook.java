package dev.px.karatsuba.client.gui.apple.shell.look;

import dev.px.core.layout.Align;
import dev.px.core.render.Color;
import dev.px.core.render.Render;
import dev.px.gui.render.WidgetRenderer;
import dev.px.gui.render.WidgetRendererRegistry;
import dev.px.karatsuba.client.gui.apple.shell.frame.GlassSurface;
import dev.px.karatsuba.client.gui.apple.shell.header.HeaderBar;
import dev.px.karatsuba.client.gui.apple.shell.header.HeaderTitle;
import dev.px.karatsuba.client.gui.apple.shell.header.NavCapsule;
import dev.px.karatsuba.client.gui.apple.shell.header.NavButton;

import static dev.px.karatsuba.client.gui.apple.shell.style.Palette.*;

/**
 * The header ({@code gui/apple/DESIGN.md} §2.3): no bar, only the window's top
 * area; ‹ › as plain chevrons in a small glass capsule (vibrant, being on glass),
 * dimmed when there is nowhere to go; the title in the heading face on the window.
 */
final class HeaderLook {

    /** The chevron inside a nav button. */
    private static final float CHEVRON = 11f;

    private HeaderLook() {
    }

    static void register(WidgetRendererRegistry look) {
        // No bar: the header is the window's top area (step 8d).
        look.register(WidgetRenderer.of(HeaderBar.class, (bar, c) -> c.align(Align.STRETCH).slot()));

        // A small capsule of glass round ‹ ›; the host draws the glass where it is named.
        look.register(WidgetRenderer.of(NavCapsule.class, (capsule, c) -> c
                .row(glass -> glass.name(GlassSurface.PART).padding(3f, 0f).align(Align.CENTER).slot())));

        // Plain chevrons, no chrome (step 8b): brighter under the cursor, dimmed with nowhere to go.
        look.register(WidgetRenderer.of(NavButton.class, (button, c) -> {
            float hover = button.hover();
            Color ink = button.isEnabled()
                    ? VIBRANT_SECONDARY.get().lerp(VIBRANT_LABEL.get(), hover)
                    : LABEL_TERTIARY.get();
            c.custom(NAV_BUTTON, NAV_BUTTON, (x, y, w, h) -> {
                Glyphs.draw(button.glyph(), x + (w - CHEVRON) / 2f, y + (h - CHEVRON) / 2f, CHEVRON, ink);
            });
        }));

        look.register(WidgetRenderer.of(HeaderTitle.class, (title, c) -> c.row(r -> r
                .text(heading(), title.text(), LABEL.get()))));
    }
}
