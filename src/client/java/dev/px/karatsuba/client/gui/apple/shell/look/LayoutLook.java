package dev.px.karatsuba.client.gui.apple.shell.look;

import dev.px.core.layout.Align;
import dev.px.core.render.Color;
import dev.px.core.render.Render;
import dev.px.gui.render.WidgetRenderer;
import dev.px.gui.render.WidgetRendererRegistry;
import dev.px.karatsuba.client.gui.apple.shell.frame.GlassSurface;
import dev.px.karatsuba.client.gui.apple.shell.frame.ToolbarDock;
import dev.px.karatsuba.client.gui.apple.shell.section.layout.EditorToolbar;
import dev.px.karatsuba.client.hud.preset.role.ColorRole;
import dev.px.karatsuba.client.hud.preset.HudTokens;
import dev.px.karatsuba.client.hud.preset.role.TextRole;

import static dev.px.karatsuba.client.gui.apple.shell.style.Palette.*;

/**
 * The layout editor's toolbar ({@code gui/apple/DESIGN.md} §10.2b): a capsule of the
 * HUD's glass at the bottom centre, its tools in that glass's colours. The glass is
 * drawn by the host where the part is named; what the editor draws over the game is
 * its own.
 */
final class LayoutLook {

    private LayoutLook() {
    }

    static void register(WidgetRendererRegistry look) {
        look.register(WidgetRenderer.of(ToolbarDock.class, (dock, c) -> c
                .padding(0f, 0f, 0f, TOOLBAR_MARGIN).slot()));

        // No fill of its own: the glass behind it is the host's.
        look.register(WidgetRenderer.of(EditorToolbar.class, (bar, c) -> c
                .row(glass -> glass.name(GlassSurface.PART).padding(TOOLBAR_PAD).align(Align.CENTER).slot())));

        look.register(WidgetRenderer.of(EditorToolbar.Divider.class, (divider, c) -> {
            Color line = divider.tokens().color(ColorRole.SEPARATOR);
            c.custom(9f, TOOL_SIZE * 0.55f, (x, y, w, h) -> Render.rect(x + w / 2f, y, 1f, h, line));
        }));

        look.register(WidgetRenderer.of(EditorToolbar.Tool.class, (tool, c) -> {
            HudTokens tokens = tool.tokens();
            float hover = tool.hover();
            float on = tool.on();
            boolean enabled = tool.isEnabled();
            Color text = tokens.text(TextRole.PRIMARY);
            Color fill;
            Color ink;
            if (tool.kind() == EditorToolbar.Tool.Kind.PRIMARY) {
                // Done: the one thing to do next, filled with the accent lifted for this glass.
                Color accent = tokens.color(ColorRole.ACCENT);
                fill = accent.lerp(Color.WHITE, hover * 0.12f);
                ink = onAccent(accent);
            } else {
                fill = text.fade(TOOL_HOVER * hover);
                ink = enabled ? text : text.fade(TOOL_DISABLED);
            }
            Color glyphInk = tool.kind() == EditorToolbar.Tool.Kind.TOGGLE
                    ? ink.lerp(tokens.color(ColorRole.ACCENT), on) : ink;
            c.row(r -> {
                r.height(TOOL_SIZE).padding(tool.iconOnly() ? (TOOL_SIZE - TOOL_GLYPH) / 2f : TOOL_PAD, 0f)
                        .gap(5f).align(Align.CENTER)
                        .backdrop((x, y, w, h) -> Render.roundRect(x, y, w, h, h / 2f, fill))
                        .custom(TOOL_GLYPH, TOOL_GLYPH, (x, y, w, h) -> Glyphs.draw(tool.glyph(), x, y, w, glyphInk));
                if (!tool.iconOnly()) {
                    r.text(tokens.font(TextRole.LABEL), tool.label(), ink);
                }
            });
        }));
    }
}
