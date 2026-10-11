package dev.px.karatsuba.client;

import dev.px.core.Core;
import dev.px.karatsuba.adapter.MinecraftAdapter;
import dev.px.karatsuba.client.automation.Automation;
import dev.px.karatsuba.client.command.Commands;
import dev.px.karatsuba.client.config.AutoSave;
import dev.px.karatsuba.client.config.BindsSection;
import dev.px.karatsuba.client.config.ConfigLayout;
import dev.px.karatsuba.client.social.FriendRecords;
import dev.px.karatsuba.client.gui.shell.section.home.RecentModules;
import dev.px.karatsuba.client.debug.DebugMonitor;
import dev.px.karatsuba.client.debug.PerformanceMonitor;
import dev.px.karatsuba.client.hud.HudElements;
import dev.px.karatsuba.client.input.Binds;
import dev.px.karatsuba.client.module.Categories;
import dev.px.karatsuba.client.module.Modules;
import dev.px.karatsuba.client.anticheat.AntiCheats;
import dev.px.karatsuba.client.network.Servers;
import dev.px.karatsuba.client.render.ShaderCatalogue;
import dev.px.karatsuba.client.screen.Overlays;
import dev.px.karatsuba.client.service.Services;
import dev.px.karatsuba.client.setting.SettingTypes;
import dev.px.karatsuba.client.settings.ClientSettings;
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
 *   <li>{@link Overlays} &mdash; {@code screen/}, what the client draws over the
 *       game</li>
 *   <li>{@link Services} &mdash; {@code service/}, the arbiters that decide which
 *       module wins when two want the same thing</li>
 *   <li>{@link Automation} &mdash; {@code automation/}, the tasks the player can
 *       start: walking somewhere, following someone</li>
 *   <li>{@link Servers} &mdash; {@code network/}, what Core's server detection
 *       can recognise</li>
 *   <li>{@link AntiCheats} &mdash; {@code anticheat/}, what Core's anticheat
 *       detection can recognise, with {@code Grim.md} on how Grim checks</li>
 *   <li>{@link ConfigLayout} &mdash; {@code config/}, which file each part of the
 *       config is saved in</li>
 * </ul>
 */
public final class Karatsuba {

    public static final String NAME = "Karatsuba";
    public static final String VERSION = "1.0.0";

    /** Config and data land in {@code .minecraft/karatsuba/}. */
    private static final String DATA_FOLDER = "karatsuba";

    /**
     * The Azure application Microsoft sign-ins are made against.
     *
     * <p>Empty uses the vanilla launcher's public id, which is what ships and
     * what most clients use. Put your own here if Microsoft starts refusing it
     * with {@code unauthorized_client} or {@code invalid_client} &mdash; that
     * error means the id, not the account, and it is the one login failure a
     * player cannot do anything about.
     *
     * <p>An application you own also lets you see your own throttling and revoke
     * your own consent. It needs the device code flow enabled and the
     * {@code XboxLive.signin} scope.
     */
    private static final String MICROSOFT_CLIENT_ID = "";

    private static Core core;

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
                // Has to arrive here rather than through install() below: Core
                // resolves its logger in the constructor and hands it to every
                // service there. Without it Core falls back to System.out, which
                // reaches neither latest.log nor a crash report.
                .logger(MinecraftAdapter.logger(NAME))
                .build();

        // Render backends, the font provider and the event bridges. Before start(),
        // because start() asks the font provider for the default font.
        MinecraftAdapter.install(core);

        // Empty leaves the adapter's default in place, so this is safe to ship
        // blank and is the one line to change if sign-ins start being refused.
        MinecraftAdapter.microsoftClientId(MICROSOFT_CLIENT_ID);

        registerContent();

        // Installed before start() so the config load inside it is what first
        // clears the dirty flag, rather than a stray toggle marking us dirty at boot.
        AutoSave.install();

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
        // First: modules reach these from their very first tick, and Core's
        // container refuses a registration once start() has run.
        Services.registerAll(core);

        // Client settings sit above the modules: they set the rules every module
        // plays by (how rotations, clicks and packets go out). After the services,
        // which a section hands itself to, and before any module is created.
        ClientSettings.registerAll(core);

        // Tasks, run as flows under the Automation settings just registered. Before
        // start(), which starts it; nothing runs until a task is asked for.
        Automation.install(core);

        // The rows for setting types Core does not have, before any screen is built.
        SettingTypes.registerAll(core);

        Categories.registerAll(core);
        Themes.registerAll(core);

        // The GLSL programs render modules draw with. Pure data: nothing compiles
        // until a module first draws with one.
        ShaderCatalogue.registerAll(core);

        Modules.registerAll(core);
        Commands.registerAll(core);
        HudElements.registerAll(core);
        Binds.registerAll(core);

        // Not Core's: an overlay registers with the adapter, because being drawn
        // into the game is a seam Core does not have. Order-independent.
        Overlays.install();

        // What Core's network services can recognise: server software from a
        // brand, and anticheats from the evidence. Core ships neither list.
        Servers.registerAll(core);
        AntiCheats.registerAll(core);

        // Collects packets, movement and alerts for the Debug HUD element from
        // boot, so a rubberband that happened before it was switched on still
        // shows in its log.
        DebugMonitor.install();
        // Times the tick, the flows, the drawing and every route plan, for that same
        // element's Performance section. After Automation, whose planners it reads.
        PerformanceMonitor.install();

        // After the binds exist, before start() loads over them. Core has no
        // section for action binds, so without this a rebind lasts until restart.
        BindsSection.install();

        // Notes and sightings for each friend, beside Core's list of them.
        FriendRecords.install();

        // What was switched on or off lately, for the GUI's Home.
        RecentModules.install();

        // Before start(), which is the first load: one file per section.
        ConfigLayout.install();
    }

    /** @return the running Core, or null before {@link #boot()} */
    public static Core core() {
        return core;
    }
}
