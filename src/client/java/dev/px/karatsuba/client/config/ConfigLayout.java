package dev.px.karatsuba.client.config;

import dev.px.core.Core;
import dev.px.core.config.ConfigLocation;
import dev.px.core.config.ConfigService;

/**
 * Where each part of the config is saved: one file per section.
 *
 * <pre>
 * .minecraft/karatsuba/configs/
 *   profiles/
 *     default/                  one folder per profile; switching profile switches these
 *       modules.json            every module's enabled flag, keybind and settings
 *       hud.json                HUD element positions, scale, z-order and settings
 *       gui.json                click GUI window positions and state
 *       theme.json              the accent colours
 *       binds.json              the action keybinds: Click GUI, HUD Editor, Profiles
 *       notifications.json      notification settings
 *       integrations.json       Discord presence and media settings
 *       commands.json           the command prefix
 *       interaction.json        how attacks and clicks are checked and sent (Client Settings)
 *       updates.json            update checks (Client Settings)
 *     hypixel/...
 *   shared/                     the same whichever profile is active
 *     friends.json
 *     accounts.json
 *     profile.json              which profile was active last (Core's own)
 *   legacy/                     single-file profiles from before the split, kept as they were
 * </pre>
 *
 * <p>Saving and loading are Core's: {@code ConfigService} writes each section to
 * its own file, atomically, and a file that fails to read keeps its section's
 * defaults without touching the others. That is the point of the split &mdash;
 * a bad hand edit to {@code hud.json} costs the HUD layout, not the modules.
 *
 * <p>This class only says where. Core would put most sections at these paths
 * anyway, under their ids; naming each one here makes the layout this client's
 * decision rather than a default that could move under it, and makes this file
 * the one place to read or change it. A section not listed here still saves, in
 * the profile, under its id.
 *
 * <p>Old single-file profiles ({@code configs/default.json}) are split into this
 * layout by Core on the first load, and the originals moved to {@code legacy/}.
 */
public final class ConfigLayout {

    private ConfigLayout() {
    }

    /**
     * Places every section. Must run before {@code Core.start()}, which loads the
     * config; Core refuses a placement that would put two sections in one file.
     */
    public static void install() {
        ConfigService config = Core.config();

        // Per profile.
        config.place("modules", ConfigLocation.profile("modules"));
        config.place("hud", ConfigLocation.profile("hud"));
        config.place("gui", ConfigLocation.profile("gui"));
        config.place("theme", ConfigLocation.profile("theme"));
        config.place(BindsSection.ID, ConfigLocation.profile("binds"));
        config.place("notifications", ConfigLocation.profile("notifications"));
        config.place("integrations", ConfigLocation.profile("integrations"));
        config.place("commands", ConfigLocation.profile("commands"));

        // Shared by every profile: who your friends and accounts are does not
        // change with the settings you play on.
        config.place("friends", ConfigLocation.shared("friends"));
        config.place("accounts", ConfigLocation.shared("accounts"));
    }
}
