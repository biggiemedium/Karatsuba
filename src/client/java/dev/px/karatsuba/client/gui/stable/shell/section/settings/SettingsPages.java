package dev.px.karatsuba.client.gui.stable.shell.section.settings;

import dev.px.core.Core;
import dev.px.core.setting.Setting;
import dev.px.karatsuba.client.settings.ClientSection;
import dev.px.karatsuba.client.settings.ClientSettings;

import java.util.ArrayList;
import java.util.List;

/**
 * The pages of the Settings section, most used first and the rarely changed
 * last: General, Interaction, Keybinds, Notifications, Interface, Commands,
 * Alerts, Spotify, Updates.
 *
 * <p>Most pages are the client's own sections ({@link ClientSettings}), titled and
 * described as they describe themselves. Three are Core's: the global keybinds,
 * notifications and the command prefix. A client section added later, and not
 * placed in the order here, joins at the end, so nothing is ever missing from the
 * page list.
 *
 * <p>The Spotify page is its own class, {@link SpotifyPage}: it mixes owners
 * (Core's Now Playing switch, the adapter's Client ID) and is the one page with
 * rows that are not settings, the connection's status and its button.
 *
 * <p>Core's theme options (corner radius, opacity, text colour, element
 * backgrounds) and its rich presence switch are left out on purpose: nothing in
 * this client reads the first four, and the last needs a provider the adapter
 * does not install. A switch that does nothing is worse than no switch.
 */
public final class SettingsPages {

    /** The order of the client's sections, by id, with Core's pages slotted between. */
    private static final List<String> ORDER = List.of(
            "general", "interaction", "keybinds", "notifications", "interface", "commands", "alerts",
            SpotifyPage.ID, "updates");

    private SettingsPages() {
    }

    public static List<SettingsPage> create() {
        List<SettingsPage> pages = new ArrayList<>();
        for (String id : ORDER) {
            SettingsPage page = page(id);
            if (page != null) {
                pages.add(page);
            }
        }
        for (ClientSection section : ClientSettings.all()) {
            if (!ORDER.contains(section.getId())) {
                pages.add(of(section));
            }
        }
        return pages;
    }

    private static SettingsPage page(String id) {
        switch (id) {
            case "keybinds":
                return new SettingsPage(id, "Keybinds", "Keys that open the GUI and its screens",
                        "A module's own key is set in its settings, under Modules.",
                        () -> Core.input().getActionSettings());
            case "notifications":
                return new SettingsPage(id, "Notifications", "The messages that slide in at the corner of the screen",
                        null, () -> Core.notifications().getSettings());
            case "commands":
                return new SettingsPage(id, "Commands", "Chat commands, such as .toggle and .friend",
                        null, () -> Core.commands().getPrefix().getSettings());
            case SpotifyPage.ID:
                return SpotifyPage.create();
            default:
                for (ClientSection section : ClientSettings.all()) {
                    if (section.getId().equals(id)) {
                        return of(section);
                    }
                }
                return null;
        }
    }

    private static SettingsPage of(ClientSection section) {
        return new SettingsPage(section.getId(), section.getName(), section.getDescription(), null,
                () -> new ArrayList<Setting<?>>(section.getSettings()));
    }
}
