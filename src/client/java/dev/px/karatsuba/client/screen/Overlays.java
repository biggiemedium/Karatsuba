package dev.px.karatsuba.client.screen;

import dev.px.core.Core;
import dev.px.karatsuba.adapter.screen.OverlayHost;
import dev.px.karatsuba.client.config.AutoSave;
import dev.px.karatsuba.client.settings.ClientSettings;
import dev.px.karatsuba.client.settings.gui.GuiSettings;

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
 * documents &mdash; there is one HUD, and one of each interface. {@link #hud()},
 * {@link #active()} and {@link #clickGui()} are how a keybind or a command reaches them.
 */
public final class Overlays {

    private static final HudOverlay HUD = new HudOverlay();
    private static final ClickGuiOverlay CLICK_GUI = new ClickGuiOverlay();
    private static final ClientGuiOverlay CLASSIC = new ClientGuiOverlay();
    private static final AppleGuiOverlay APPLE = new AppleGuiOverlay();

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
        OverlayHost.register(CLASSIC);
        OverlayHost.register(APPLE);
    }

    /** The HUD, and its editor. */
    public static HudOverlay hud() {
        return HUD;
    }

    /** The interface the open binds reach: Classic or Apple, as Interface settings say. */
    public static InterfaceOverlay active() {
        return ClientSettings.get(GuiSettings.class).style() == GuiSettings.Style.APPLE ? APPLE : CLASSIC;
    }

    /** The Classic GUI: Modules, HUD, the Layout editor and Themes. */
    public static ClientGuiOverlay classic() {
        return CLASSIC;
    }

    /** The Apple GUI, built to {@code gui/apple/DESIGN.md}. */
    public static AppleGuiOverlay apple() {
        return APPLE;
    }

    /** @return whether either interface is open, closing included */
    public static boolean anyInterfaceOpen() {
        return CLASSIC.isCapturing() || APPLE.isCapturing();
    }

    /**
     * Makes the other interface the active one. If the active one was open it
     * closes and the other opens in its place; otherwise a notification says
     * which is active now, since nothing on screen would.
     */
    public static void switchInterface() {
        GuiSettings settings = ClientSettings.get(GuiSettings.class);
        InterfaceOverlay from = active();
        boolean wasOpen = from.isCapturing();
        from.close();
        GuiSettings.Style next = settings.style() == GuiSettings.Style.APPLE
                ? GuiSettings.Style.CLASSIC : GuiSettings.Style.APPLE;
        settings.style(next);
        AutoSave.markDirty();
        if (wasOpen) {
            active().open(null);
        } else {
            Core.notifications().info("Interface", next == GuiSettings.Style.APPLE ? "Apple" : "Classic");
        }
    }

    /** The legacy GUI's screens: the click GUI, and anything else opened through it. */
    public static ClickGuiOverlay clickGui() {
        return CLICK_GUI;
    }
}
