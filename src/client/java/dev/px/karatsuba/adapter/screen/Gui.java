package dev.px.karatsuba.adapter.screen;

import dev.px.core.Core;
import dev.px.gui.legacy.GuiService;

/**
 * Where the client gets the GUI library's click GUI, installed on Core.
 *
 * <pre>{@code
 * Gui.service().toggleClickGui();
 * }</pre>
 *
 * <p>Since Core 2.3 the click GUI is no longer part of Core: it lives in the
 * {@code gui} library as {@code dev.px.gui.legacy}, and is built over a Core with
 * {@link GuiService#install} rather than reached through {@code Core.gui()}. This
 * holds the one instance, the way {@code Combat} holds the combat kit.
 *
 * <p>Legacy because upstream replaces it with the headless widgets in
 * {@code dev.px.gui} once they cover it. The service itself names no game type;
 * what ties it to the game is {@code ClickGuiOverlay} on top of {@link CoreScreen}.
 */
public final class Gui {

    private static GuiService service;

    private Gui() {
    }

    /**
     * Builds the GUI over {@code core} and registers it as a service and a config
     * section, so it starts, stops and saves its window positions with Core.
     *
     * <p>From {@code MinecraftAdapter.install}, between {@code build()} and
     * {@code start()}: {@code GuiService.install} refuses a Core already started.
     */
    public static void install(Core core) {
        service = GuiService.install(core);
    }

    /**
     * @throws IllegalStateException if called before the adapter installed it
     *
     * <p>Loud rather than null, like {@code Combat.kit()}: reaching for the GUI
     * before boot is a boot-order mistake.
     */
    public static GuiService service() {
        if (service == null) {
            throw new IllegalStateException("the GUI is not installed yet;"
                    + " MinecraftAdapter.install(core) installs it during boot, before Core.start()");
        }
        return service;
    }
}
