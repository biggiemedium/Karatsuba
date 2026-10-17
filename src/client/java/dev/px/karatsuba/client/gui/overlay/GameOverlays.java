package dev.px.karatsuba.client.gui.overlay;

import dev.px.karatsuba.adapter.screen.LoadingScreen;
import dev.px.karatsuba.adapter.screen.MenuPanorama;

/**
 * The game overlays catalogue: the client's pictures in place of the game's own
 * full-screen ones.
 *
 * <p>Same shape as {@code screen/Overlays}: write the screen in this folder, then
 * install it here. Each is a slot the adapter declares (one loading screen, one
 * menu panorama) rather than a list, so installing is what replaces the game's,
 * and leaving a line out puts the game's back.
 */
public final class GameOverlays {

    private static final LoadingOverlay LOADING = new LoadingOverlay();

    private GameOverlays() {
    }

    /**
     * Installs every game overlay with the adapter. Order-independent; on the
     * render thread during boot, where the panorama's faces start loading.
     */
    public static void install() {
        LoadingScreen.install(LOADING);
        MenuPanorama.install(OverlayStyle.MENU_PANORAMA);
    }
}
