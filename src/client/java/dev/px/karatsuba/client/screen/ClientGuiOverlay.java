package dev.px.karatsuba.client.screen;

import dev.px.core.Core;
import dev.px.core.input.Key;
import dev.px.core.input.Modifier;
import dev.px.core.input.MouseButton;
import dev.px.core.layout.Bounds;
import dev.px.core.render.Render;
import dev.px.karatsuba.adapter.screen.CoreScreen;
import dev.px.karatsuba.adapter.screen.Overlay;
import dev.px.karatsuba.client.config.AutoSave;
import dev.px.karatsuba.client.gui.shell.ClientGui;
import dev.px.karatsuba.client.gui.shell.style.Animations;
import dev.px.karatsuba.client.gui.shell.style.Easing;
import dev.px.karatsuba.client.gui.shell.style.Palette;

import java.util.Set;

/**
 * The client's GUI on screen: Modules, HUD, the Layout editor and Themes, one
 * {@link ClientGui} switched by its rail.
 *
 * <p>Passes the frame and the input through, and closes when Escape reaches it
 * with nothing left inside to back out of. Opens by fading in and growing from
 * 97% over 220ms; closes by fading out and shrinking to 98% over 160ms, ignoring
 * input meanwhile. When the game takes the screen away instead it closes at
 * once, since there is nothing left to draw on.
 */
public final class ClientGuiOverlay implements Overlay {

    private ClientGui gui;
    private long openedAt;
    private long closingAt;

    // ------------------------------------------------------------- lifecycle

    /** Opens the GUI on the section it was last on, or closes it if it is showing. */
    public void toggle() {
        if (gui != null && !closing()) {
            close();
        } else {
            open(null);
        }
    }

    /**
     * Opens the GUI on a section, or switches to it if the GUI is open; if that
     * section is already showing, closes the GUI, so one key both opens and shuts it.
     *
     * @param sectionId the section, or null for the one last open
     */
    public void open(String sectionId) {
        if (gui != null && closing()) {
            reopenFromClosing();
            if (sectionId != null) {
                gui.show(sectionId);
            }
            return;
        }
        if (gui != null) {
            if (sectionId == null || gui.active().id().equals(sectionId)) {
                close();
            } else {
                gui.show(sectionId);
            }
            return;
        }
        // The game screen goes up first: the frame that draws overlays is only
        // reached once a screen is showing.
        CoreScreen.open();
        gui = new ClientGui(sectionId);
        openedAt = System.nanoTime();
        closingAt = 0L;
    }

    /** Closes with the closing animation. */
    public void close() {
        if (gui == null || closing()) {
            return;
        }
        gui.closed();
        AutoSave.markDirty();
        if (!Animations.enabled()) {
            gui = null;
            return;
        }
        closingAt = System.nanoTime();
    }

    @Override
    public void dismiss() {
        if (gui != null) {
            if (!closing()) {
                gui.closed();
            }
            gui = null;
            closingAt = 0L;
            AutoSave.markDirty();
        }
    }

    @Override
    public boolean isCapturing() {
        return gui != null;
    }

    private boolean closing() {
        return closingAt != 0L;
    }

    private void reopenFromClosing() {
        float left = 1f - Animations.progress(closingAt, Animations.CLOSE);
        // Start the opening as far along as the window is still showing.
        openedAt = System.nanoTime() - (long) (left * Animations.OPEN * 1_000_000L);
        closingAt = 0L;
    }

    // --------------------------------------------------------------- drawing

    @Override
    public void render(float screenWidth, float screenHeight) {
        if (gui == null) {
            return;
        }
        float shown;
        float scale;
        if (closing()) {
            float progress = Animations.progress(closingAt, Animations.CLOSE);
            if (progress >= 1f) {
                gui = null;
                closingAt = 0L;
                return;
            }
            float eased = Easing.ACCELERATE.apply(progress);
            shown = 1f - eased;
            scale = 1f - (1f - Animations.CLOSE_SCALE) * eased;
        } else if (Animations.enabled()) {
            float eased = Easing.DECELERATE.apply(Animations.progress(openedAt, Animations.OPEN));
            shown = eased;
            scale = Animations.OPEN_SCALE + (1f - Animations.OPEN_SCALE) * eased;
        } else {
            shown = 1f;
            scale = 1f;
        }

        Render.rect(0f, 0f, screenWidth, screenHeight, Palette.faded(Palette.SCRIM, shown * gui.scrim()));
        float mouseX = Core.platform().getMouseX();
        float mouseY = Core.platform().getMouseY();
        if (shown >= 1f && scale >= 1f) {
            gui.frame(screenWidth, screenHeight, mouseX, mouseY);
            return;
        }
        // Grows or shrinks about the window's centre. Drawing only: for these few
        // frames a click lands where the window will be, not where it is drawn.
        Bounds window = gui.windowBounds();
        Render.pushAlpha(shown);
        try {
            Render.scaled(window.getCenterX(), window.getCenterY(), scale,
                    () -> gui.frame(screenWidth, screenHeight, mouseX, mouseY));
        } finally {
            Render.popAlpha();
        }
    }

    // ----------------------------------------------------------------- input

    @Override
    public boolean keyPressed(Key key, Set<Modifier> modifiers) {
        if (closing()) {
            return true;
        }
        if (gui.key(key, modifiers)) {
            return true;
        }
        if (key == Key.ESCAPE) {
            close();
            return true;
        }
        // Unused keys go on to the client's binds, so the key that opened the GUI closes it.
        return false;
    }

    @Override
    public boolean charTyped(char character) {
        if (!closing()) {
            gui.typed(character);
        }
        return true;
    }

    @Override
    public boolean mousePressed(float x, float y, MouseButton button) {
        if (!closing()) {
            gui.press(x, y, button);
        }
        // Consumed either way: a press that landed on nothing is still a press
        // inside our screen, and the game must not act on it.
        return true;
    }

    @Override
    public void mouseReleased(float x, float y) {
        if (gui == null || closing()) {
            return;
        }
        gui.release(x, y);
        // Whatever was just changed is saved shortly, as the old click GUI did.
        AutoSave.markDirty();
    }

    @Override
    public boolean scrolled(float amount, float x, float y) {
        if (!closing()) {
            gui.scroll(amount, x, y);
        }
        return true;
    }

    @Override
    public void modifiersChanged(Set<Modifier> modifiers) {
        if (gui != null && !closing()) {
            gui.modifiers(modifiers);
        }
    }
}
