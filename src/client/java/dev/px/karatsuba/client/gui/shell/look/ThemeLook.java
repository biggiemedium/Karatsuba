package dev.px.karatsuba.client.gui.shell.look;

import dev.px.core.layout.Align;
import dev.px.core.render.Color;
import dev.px.core.render.Render;
import dev.px.gui.render.WidgetRenderer;
import dev.px.gui.render.WidgetRendererRegistry;
import dev.px.karatsuba.client.gui.shell.section.themes.ThemeCard;
import dev.px.karatsuba.client.gui.shell.section.themes.ThemeView;

import static dev.px.karatsuba.client.gui.shell.look.Shapes.tick;
import static dev.px.karatsuba.client.gui.shell.look.Shapes.toggle;
import static dev.px.karatsuba.client.gui.shell.style.Palette.*;

/** The theme picker: its header and its cards. */
final class ThemeLook {

    /** A card: its gradient, then a row with the name and a sample switch. */
    private static final float CARD_HEIGHT = 64f;
    private static final float PREVIEW_HEIGHT = 30f;
    private static final float RADIUS_CARD = 6f;

    private ThemeLook() {
    }

    static void register(WidgetRendererRegistry look) {
        look.register(WidgetRenderer.of(ThemeView.class, (view, c) -> c
                .padding(12f, 12f, 12f, INSET)
                .slot()));

        look.register(WidgetRenderer.of(ThemeCard.class, (card, c) -> c.align(Align.STRETCH)
                .custom(0f, CARD_HEIGHT, (x, y, w, h) -> card(card, x, y, w, h))));
    }

    /**
     * One card, drawn in one go so it can be faded and lifted as a whole: its
     * arrival rises 6px and fades in; hovering lifts it a pixel with a soft
     * shadow; the active one is outlined in its own accent with a tick.
     */
    private static void card(ThemeCard card, float x, float y, float w, float h) {
        float appear = card.appear();
        if (appear <= 0f) {
            return;
        }
        float hover = card.hover();
        float chosen = card.chosen();
        Color primary = accentOf(card.theme(), 0f);
        Color secondary = accentOf(card.theme(), 1f);

        Render.pushAlpha(appear);
        Render.pushTransform();
        Render.translate(0f, (1f - appear) * 6f - hover);
        try {
            if (hover > 0f) {
                Render.shadow(x, y, w, h, 8f, faded(Color.of(0, 0, 0, 110), hover));
            }
            Render.roundRect(x, y, w, h, RADIUS_CARD, RAISED.lerp(RAISED_HIGH, hover));

            // The theme's gradient, as the brand stroke and the selected states will wear it.
            float inset = 5f;
            Render.roundGradient(x + inset, y + inset, w - inset * 2f, PREVIEW_HEIGHT, RADIUS_CARD - 2f,
                    primary, secondary, secondary, primary);
            if (chosen > 0f) {
                float cx = x + w - inset - 9f;
                float cy = y + inset + 9f;
                Render.circle(cx, cy, 6f, faded(ON_ACCENT, chosen));
                tick(cx - 3f, cy - 2f, 6f, 4.5f, faded(Color.of(20, 20, 24), chosen));
            }

            // The name, and a switch that is on, in this theme's accent.
            float rowY = y + inset + PREVIEW_HEIGHT;
            float rowHeight = h - inset - PREVIEW_HEIGHT;
            float textY = rowY + (rowHeight - body().getHeight()) / 2f;
            Color name = TEXT_MUTED.lerp(TEXT, Math.max(hover, chosen));
            Render.pushClip(x, rowY, w - 30f, rowHeight);
            Render.text(body(), card.theme().getName(), x + 8f, textY, name);
            Render.popClip();
            toggle(x + w - 26f, rowY + (rowHeight - 9f) / 2f, 17f, 9f, 1f, primary);

            if (chosen > 0f) {
                Render.roundRectOutline(x, y, w, h, RADIUS_CARD, 1.5f, faded(primary, chosen));
            }
        } finally {
            Render.popTransform();
            Render.popAlpha();
        }
    }
}
