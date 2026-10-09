package dev.px.karatsuba.client.screen;

import dev.px.karatsuba.adapter.screen.OverlayHost;

/**
 * The overlay catalogue: everything the client puts on screen.
 *
 * <p>Same shape as {@code Modules}, {@code Commands} and {@code HudElements},
 * and in the same place relative to what it lists. Adding a screen is two steps
 * and both happen in this folder: write the {@code Overlay}, then add a line here.
 *
 * <p><b>Registration order is draw order</b>, bottom first, and input is offered
 * in the reverse. That one rule decides both of the things that used to be spread
 * across the adapter: the GUI is drawn over the HUD, and the GUI is asked about a
 * keystroke before the HUD editor is.
 *
 * <p>The instances are singletons because they are the client's screens, not
 * documents &mdash; there is one HUD and one click GUI. {@link #hud()} and
 * {@link #clickGui()} are how a keybind or a command reaches them.
 */
public final class Overlays {

    private static final HudOverlay HUD = new HudOverlay();
    private static final ClickGuiOverlay CLICK_GUI = new ClickGuiOverlay();
    private static final ClientGuiOverlay GUI = new ClientGuiOverlay();

    private Overlays() {
    }

    /**
     * Registers every overlay with the adapter.
     *
     * <p>Not {@code registerAll(Core)} like the other catalogues, because these
     * register with the adapter rather than with Core: an overlay is how the game
     * is drawn into, which is a seam Core does not have. Safe at any point in the
     * boot &mdash; nothing draws until a frame happens.
     */
    public static void install() {
        OverlayHost.register(HUD);
        OverlayHost.register(CLICK_GUI);
        OverlayHost.register(GUI);
    }

    /** The HUD, and its editor. */
    public static HudOverlay hud() {
        return HUD;
    }

    /** The client GUI: Modules, HUD, the Layout editor and Themes. */
    public static ClientGuiOverlay gui() {
        return GUI;
    }

    /** The legacy GUI's screens: the click GUI, and anything else opened through it. */
    public static ClickGuiOverlay clickGui() {
        return CLICK_GUI;
    }
}
