package dev.px.karatsuba.client;

import dev.px.core.Core;
import dev.px.karatsuba.adapter.MinecraftAdapter;
import dev.px.karatsuba.client.command.Commands;
import dev.px.karatsuba.client.config.AutoSave;
import dev.px.karatsuba.client.config.BindsSection;
import dev.px.karatsuba.client.hud.HudElements;
import dev.px.karatsuba.client.input.Binds;
import dev.px.karatsuba.client.module.Categories;
import dev.px.karatsuba.client.module.Modules;
import dev.px.karatsuba.client.theme.Themes;

/**
 * The bootstrap.
 *
 * <p>Its shape is dictated by Core: registration happens <em>between</em>
 * {@code build()} and {@code start()}. Categories must exist before modules
 * resolve against them, themes before a saved selection is looked up, HUD elements
 * before the config is loaded over their layouts, and binds before the saved ones
 * are loaded over the defaults. Then {@code start()} orders service startup,
 * applies module defaults, and loads the saved config over the top.
 *
 * <p>Nothing is registered inline here, and no catalogue lives in this package.
 * Each one sits with the content it lists and exposes the same
 * {@code registerAll(Core)}, so this file is the only one that knows the order
 * and adding content never means editing it:
 *
 * <ul>
 *   <li>{@link Categories}, {@link Modules} &mdash; {@code module/}</li>
 *   <li>{@link Commands} &mdash; {@code command/}</li>
 *   <li>{@link HudElements} &mdash; {@code hud/}</li>
 *   <li>{@link Binds} &mdash; {@code input/}, the keyboard actions that are not
 *       modules</li>
 *   <li>{@link Themes} &mdash; {@code theme/}, the accent palette</li>
 * </ul>
 */
public final class Karatsuba {

    public static final String NAME = "Karatsuba";
    public static final String VERSION = "1.0.0";

    /** Config and data land in {@code .minecraft/karatsuba/}. */
    private static final String DATA_FOLDER = "karatsuba";

    private static Core core;
    private static AutoSave autoSave;

    private Karatsuba() {
    }

    /**
     * Boots the client. Called once, on the render thread, after the game is up.
     *
     * @throws IllegalStateException if called twice
     */
    public static void boot() {
        if (core != null) {
            throw new IllegalStateException(NAME + " is already booted");
        }

        core = Core.builder(NAME, VERSION)
                .platform(MinecraftAdapter.platform(DATA_FOLDER))
                .build();

        // Render backends, the font provider and the event bridges. Before start(),
        // because start() asks the font provider for the default font.
        MinecraftAdapter.install(core);

        registerContent();

        // Installed before start() so the config load inside it is what first
        // clears the dirty flag, rather than a stray toggle marking us dirty at boot.
        autoSave = AutoSave.install();

        core.start();
    }

    /**
     * Every catalogue, in the one order that satisfies Core's constraints.
     *
     * <p>Each call has the same shape on purpose. The only thing this method
     * encodes is the ordering, so that is the only thing worth reading it for:
     * categories before modules resolve against them, themes before a saved
     * selection is looked up, elements and binds before the config is loaded over
     * them.
     */
    private static void registerContent() {
        Categories.registerAll(core);
        Themes.registerAll(core);

        Modules.registerAll(core);
        Commands.registerAll(core);
        HudElements.registerAll(core);
        Binds.registerAll(core);

        // After the binds exist, before start() loads over them. Core has no
        // section for action binds, so without this a rebind lasts until restart.
        BindsSection.install();
    }

    /** @return the running Core, or null before {@link #boot()} */
    public static Core core() {
        return core;
    }

    /** @return the autosave, so a shutdown path can flush without waiting for the timer */
    public static AutoSave autoSave() {
        return autoSave;
    }
}
