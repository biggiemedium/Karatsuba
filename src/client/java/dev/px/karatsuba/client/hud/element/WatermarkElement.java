package dev.px.karatsuba.client.hud.element;

import dev.px.core.Core;
import dev.px.core.hud.HudElement;
import dev.px.core.hud.HudLayout;
import dev.px.core.hud.Anchor;
import dev.px.core.layout.Content;
import dev.px.core.render.Color;
import dev.px.core.gui.GuiStyle;

/**
 * The client name and version, in a corner.
 *
 * <p>The whole contract a HUD element has: who am I, and what am I made of. Core
 * measures that description to get the element's size and then draws the same
 * description, so the two cannot disagree. Anchoring, scaling, clamping, z-order,
 * hit testing, dragging and persistence all happen above this class, which is why
 * it does not implement any of them.
 */
public final class WatermarkElement implements HudElement {

    private static final float PADDING = 4f;
    private static final float RADIUS = 3f;

    @Override
    public String getId() {
        return "watermark";
    }

    @Override
    public String getDisplayName() {
        return "Watermark";
    }

    @Override
    public HudLayout defaultLayout() {
        return HudLayout.at(Anchor.TOP_LEFT, 4f, 4f);
    }

    @Override
    public void content(Content content) {
        content.background(GuiStyle.surface(), RADIUS).padding(PADDING);
        // A floor, so the element is still grabbable in the editor before a font
        // has loaded and the text measures to nothing.
        content.min(48f, 12f);
        content.text(text(), Color.WHITE);
    }

    private String text() {
        return Core.get().getClientName() + " " + Core.get().getClientVersion();
    }
}
