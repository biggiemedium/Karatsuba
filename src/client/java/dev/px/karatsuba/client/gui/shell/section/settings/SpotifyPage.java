package dev.px.karatsuba.client.gui.shell.section.settings;

import dev.px.core.Core;
import dev.px.core.integration.IntegrationService;
import dev.px.core.setting.Setting;
import dev.px.gui.Widget;
import dev.px.karatsuba.adapter.integration.Spotify;
import dev.px.karatsuba.client.gui.shell.setting.InfoRow;

import java.util.List;
import java.util.function.Function;

/**
 * The Spotify page: everything from no app at all to music in the HUD, without
 * leaving the screen.
 *
 * <pre>
 * Spotify App      developer.spotify.com  [Open]     1. make an app
 * Redirect URI     http://127.0.0.1:...   [Copy]     2. give it this
 * Client ID        text field                        3. paste its ID
 * Account          status   [Connect | Cancel | Disconnect]
 * Now Playing      switch, turned on by connecting
 * Poll Interval    slider, while Now Playing is on
 * </pre>
 *
 * <p>Laid out as the steps, top to bottom, so the page reads as instructions
 * without a paragraph of them. It mixes owners on purpose: Core's switch and
 * slider, the adapter's Client ID and connection. The Account row is the whole
 * sign-in in one button whose label follows the state, so there is always
 * exactly one thing to press next.
 */
final class SpotifyPage {

    static final String ID = "spotify";

    private SpotifyPage() {
    }

    static SettingsPage create() {
        return new SettingsPage(ID, "Spotify", "What is playing, for the Now Playing HUD element", null,
                SpotifyPage::settings, SpotifyPage::layout);
    }

    /** The page's settings, for search, the changed count and reset. */
    private static List<Setting<?>> settings() {
        IntegrationService integrations = Core.integrations();
        return List.of(Spotify.settings().clientIdSetting(),
                integrations.getMediaEnabled(), integrations.getMediaPollSeconds());
    }

    private static List<Widget> layout(Function<Setting<?>, Widget> row) {
        IntegrationService integrations = Core.integrations();
        return List.of(
                new InfoRow("Spotify App", () -> "developer.spotify.com", "Open", Spotify::openDashboard)
                        .describe("First, create an app here. It is free and takes a minute"),
                new InfoRow("Redirect URI", Spotify.REDIRECT_URI::toString, "Copy",
                        () -> Core.platform().setClipboard(Spotify.REDIRECT_URI.toString()))
                        .describe("Add this to the app's Redirect URIs, exactly as it is"),
                row.apply(Spotify.settings().clientIdSetting()),
                new InfoRow("Account", SpotifyPage::status, SpotifyPage::action, SpotifyPage::press)
                        .describe("Connect opens Spotify in your browser to allow access")
                        .note(SpotifyPage::problem),
                row.apply(integrations.getMediaEnabled()),
                row.apply(integrations.getMediaPollSeconds()));
    }

    /** What the Account row says: the state, in a few words. Why it is stuck goes in {@link #problem}. */
    private static String status() {
        if (Spotify.isConnecting()) {
            return "Waiting for the browser...";
        }
        if (Spotify.isConnected()) {
            String name = Spotify.accountName();
            return name == null ? "Connected" : "Connected as " + name;
        }
        if (Spotify.settings().clientId().isEmpty()) {
            return "Paste a Client ID first";
        }
        return Spotify.lastError() == null ? "Not connected" : "Could not connect";
    }

    /** Under the row, in the warning colour, wrapping: a Client ID that is not one, or why the last attempt failed. */
    private static String problem() {
        if (Spotify.isConnecting() || Spotify.isConnected() || Spotify.settings().clientId().isEmpty()) {
            return "";
        }
        if (!Spotify.hasClientId()) {
            return "That is not a Client ID: it is 32 letters and digits, from your app's Basic Information."
                    + " The Client Secret is never needed.";
        }
        String error = Spotify.lastError();
        return error == null ? "" : error;
    }

    /** The one button, or none while there is nothing it could do. */
    private static String action() {
        if (Spotify.isConnecting()) {
            return "Cancel";
        }
        if (Spotify.isConnected()) {
            return "Disconnect";
        }
        return Spotify.hasClientId() ? "Connect" : null;
    }

    private static void press() {
        if (Spotify.isConnecting()) {
            Spotify.cancel();
        } else if (Spotify.isConnected()) {
            Spotify.disconnect();
        } else {
            String refused = Spotify.connect();
            if (refused != null) {
                Core.notifications().warn("Spotify", refused);
            }
        }
    }
}
