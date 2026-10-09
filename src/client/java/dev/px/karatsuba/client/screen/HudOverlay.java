package dev.px.karatsuba.client.screen;

import dev.px.core.Core;
import dev.px.karatsuba.adapter.screen.Overlay;
import dev.px.karatsuba.client.gui.shell.section.layout.LayoutSection;

/**
 * The HUD on screen.
 *
 * <p>Draws every shown element each frame, under everything else. While the
 * HUD's layout is being edited it draws nothing: the editor, the Layout section
 * of the client GUI, draws the HUD itself then, hidden elements faint and the
 * chrome over the top, so exactly one thing decides how an element looks each
 * frame.
 *
 * <p>Takes no input. Editing is the client GUI's; {@link #toggleEditor} is kept
 * as the way into it for anything that still asks the HUD.
 */
public final class HudOverlay implements Overlay {

    /** Opens the client GUI on its Layout editor, or closes it if it is already there. */
    public void toggleEditor() {
        Overlays.gui().open(LayoutSection.ID);
    }

    @Override
    public boolean isCapturing() {
        return false;
    }

    @Override
    public void render(float screenWidth, float screenHeight) {
        if (!Core.hud().getEditor().isActive()) {
            Core.hud().drawAll();
        }
    }
}
