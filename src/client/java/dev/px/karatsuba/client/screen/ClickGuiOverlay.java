package dev.px.karatsuba.client.screen;

import dev.px.karatsuba.adapter.screen.Gui;
import dev.px.core.Core;
import dev.px.gui.legacy.Screen;
import dev.px.core.input.Key;
import dev.px.core.input.Modifier;
import dev.px.core.input.MouseButton;
import dev.px.karatsuba.adapter.screen.CoreScreen;
import dev.px.karatsuba.adapter.screen.Overlay;
import dev.px.karatsuba.client.config.AutoSave;

import java.util.Set;

/**
 * The legacy GUI's screens on screen: the click GUI, and anything else opened
 * through {@code Gui.service()}.
 *
 * <p>Thin on purpose. The gui library's {@code GuiService} ({@code dev.px.gui.legacy},
 * Core's own until 2.3) already owns the screen stack, the layout, the hit routing
 * and the focus; this only says <em>when</em> it is showing, hands it the frame and
 * the input, and answers the one question it deliberately leaves open &mdash; what
 * Escape means.
 *
 * <p>Every call here is one the library already documents as the host's job. If this file
 * grows past that, the thing being added probably belongs in a
 * {@code dev.px.gui.legacy.Screen} instead.
 */
public final class ClickGuiOverlay implements Overlay {

    // ------------------------------------------------------------- lifecycle

    /** Opens the click GUI, or closes it if it is already showing. */
    public void toggle() {
        // The game screen goes up first: the frame that draws overlays is only
        // reached once a screen is showing.
        CoreScreen.open();
        Gui.service().toggleClickGui();
    }

    /** Opens any screen of ours, replacing whatever was showing. */
    public void open(Screen screen) {
        CoreScreen.open();
        Gui.service().open(screen);
    }

    @Override
    public void dismiss() {
        Gui.service().close();
    }

    @Override
    public boolean isCapturing() {
        return Gui.service().isOpen();
    }

    // --------------------------------------------------------------- drawing

    @Override
    public void render(float screenWidth, float screenHeight) {
        // A no-op with nothing open, so there is no point guarding it: Core sizes
        // the screen from the platform each frame, which is what makes a window
        // resize free.
        Gui.service().renderFrame();
    }

    // ----------------------------------------------------------------- input

    /**
     * @return whether a component took the key
     *
     * <p>Core reports Escape rather than acting on it, because whether it closes
     * is the host's decision. Here it is the usual one &mdash; but only once
     * nothing focused wants it, so Escape leaves a text field before it leaves the
     * screen.
     */
    @Override
    public boolean keyPressed(Key key, Set<Modifier> modifiers) {
        if (Gui.service().keyPressed(key, modifiers)) {
            return true;
        }
        if (key == Key.ESCAPE) {
            Gui.service().close();
            return true;
        }
        return false;
    }

    @Override
    public boolean charTyped(char character) {
        return Gui.service().charTyped(character);
    }

    @Override
    public boolean mousePressed(float x, float y, MouseButton button) {
        Gui.service().mousePressed(x, y, button);
        // Consumed either way: a press that landed on nothing is still a press
        // inside our screen, and the game must not act on it.
        return true;
    }

    @Override
    public void mouseReleased(float x, float y) {
        Gui.service().mouseReleased(x, y);
        // Dragging a window and letting go of a slider are both state worth
        // keeping, and neither goes through anything Core's config would notice.
        AutoSave.markDirty();
    }

    @Override
    public boolean scrolled(float amount, float x, float y) {
        Gui.service().scrolled(amount, x, y);
        return true;
    }
}
