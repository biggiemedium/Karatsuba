package dev.px.karatsuba.adapter.integration;

import dev.px.core.Core;
import dev.px.core.config.ConfigLocation;
import dev.px.core.config.section.SettingsSection;
import dev.px.core.integration.MediaTrack;
import dev.px.core.notification.NotificationService;
import dev.px.karatsuba.adapter.concurrent.GameThread;
import net.minecraft.util.Util;

import java.io.IOException;
import java.net.URI;
import java.util.concurrent.CancellationException;
import java.util.function.Consumer;

/**
 * Spotify, wired into Core's integration service: the one door the client uses.
 *
 * <pre>{@code
 * Spotify.settings()                    // the Client ID, a setting the Settings page edits
 * Spotify.connect();                    // opens a browser; the result is a notification and lastError()
 * Core.integrations().getCurrentTrack() // what is playing, while Now Playing is on
 * }</pre>
 *
 * <p>Core declares {@code MediaProvider} and drives it &mdash; when to poll, on
 * which thread, whether at all &mdash; and never carries an API client or its
 * credentials. This package is the other side: {@link SpotifyMedia} answers the
 * polls, {@link SpotifyLogin} gets the sign-in, {@link SpotifySettings} keeps it.
 * It lives in the adapter because a sign-in has to open the player's browser,
 * which only the game knows how to do on every platform.
 */
public final class Spotify {

    /**
     * Where Spotify sends the browser back to. Has to be added, exactly, to the
     * app's Redirect URIs at developer.spotify.com, or every sign-in is refused.
     */
    public static final URI REDIRECT_URI = URI.create("http://127.0.0.1:43117/callback");

    private static final URI DASHBOARD = URI.create("https://developer.spotify.com/dashboard");

    private static SpotifySettings settings;
    private static SpotifyMedia media;

    /** The sign-in in flight, or null. One at a time: there is one port to listen on. */
    private static volatile SpotifyLogin pending;

    private static volatile String lastError;

    private Spotify() {
    }

    /**
     * Registers the settings section and hands Core the provider.
     *
     * <p>Between {@code build()} and {@code start()}: {@code start()} loads the
     * config, and a section registered after it would keep its defaults.
     */
    public static void install(Core core) {
        settings = new SpotifySettings();
        media = new SpotifyMedia(settings);
        core.getConfigService().register(new SettingsSection(SpotifySettings.ID, settings),
                ConfigLocation.shared(SpotifySettings.ID));
        core.getIntegrationService().setMediaProvider(media);
    }

    /** @return the Client ID and the kept sign-in */
    public static SpotifySettings settings() {
        return settings;
    }

    /** @return whether there is a sign-in to poll with */
    public static boolean isConnected() {
        return settings != null && settings.isConnected();
    }

    /** @return whether a sign-in is waiting on the browser */
    public static boolean isConnecting() {
        return pending != null;
    }

    /**
     * @return whether the Client ID looks like one: 32 hex characters, which is
     *         every Client ID Spotify has issued. Anything else is usually the
     *         Client Secret pasted into the wrong field.
     */
    public static boolean hasClientId() {
        return settings.clientId().matches("[0-9a-fA-F]{32}");
    }

    /** @return the connected account's display name, or null until it is known */
    public static String accountName() {
        return isConnected() ? media.accountName() : null;
    }

    /**
     * @return why the last sign-in failed, or null after a success or before any.
     *         Kept because a notification fades and the Settings page does not.
     */
    public static String lastError() {
        return lastError;
    }

    /** @return what is playing, or null; null too while Now Playing is switched off */
    public static MediaTrack nowPlaying() {
        return Core.integrations().getCurrentTrack();
    }

    /**
     * Starts a sign-in, and returns at once.
     *
     * <p>Opens the authorization page in the browser and puts its address in chat
     * as well, for a desktop that cannot open one. Everything after that &mdash;
     * success, refusal, timeout &mdash; arrives as a notification and in
     * {@link #lastError}.
     *
     * @return why it could not start, or null when it has
     */
    public static String connect() {
        if (!hasClientId()) {
            return "Paste your Client ID first";
        }
        if (pending != null) {
            return "Already waiting for the browser";
        }
        SpotifyLogin login = new SpotifyLogin(settings.clientId());
        pending = login;
        lastError = null;
        Core.threads().submit(() -> {
            try {
                media.signedIn(login.run(Spotify::openPage));
                // Connecting is asking to see it: no second switch to find afterwards.
                GameThread.run(() -> Core.integrations().getMediaEnabled().set(true));
                tell(n -> n.success("Spotify", "Connected"));
            } catch (IOException failure) {
                lastError = failure.getMessage();
                tell(n -> n.error("Spotify", failure.getMessage()));
            } catch (CancellationException cancelled) {
                // Asked for, so not an error: nothing to explain on the page.
                tell(n -> n.info("Spotify", "Sign-in cancelled"));
            } finally {
                pending = null;
            }
        });
        return null;
    }

    /** Stops a sign-in waiting on the browser. @return whether there was one */
    public static boolean cancel() {
        SpotifyLogin login = pending;
        if (login == null) {
            return false;
        }
        login.cancel();
        return true;
    }

    /**
     * Forgets the sign-in, here and on disk.
     *
     * <p>Spotify keeps the app's access until it is removed at
     * spotify.com/account/apps; the token this held is simply no longer anywhere.
     */
    public static void disconnect() {
        cancel();
        settings.storeRefreshToken("");
        media.signedOut();
        lastError = null;
    }

    /** Opens Spotify's developer dashboard, where the app and its Client ID are made. */
    public static void openDashboard() {
        GameThread.run(() -> browse(DASHBOARD));
    }

    public static void next() {
        media.skipNext();
    }

    public static void previous() {
        media.skipPrevious();
    }

    public static void togglePlayback() {
        media.togglePlayback();
    }

    /** Puts a notification up from any thread. */
    static void tell(Consumer<NotificationService> message) {
        GameThread.run(() -> message.accept(Core.notifications()));
    }

    /**
     * Opens the authorization page, on the game thread, and keeps the address in
     * chat too, for a desktop that cannot open a browser.
     */
    private static void openPage(URI page) {
        tell(n -> n.info("Spotify", "Sign in in your browser to connect"));
        GameThread.run(() -> {
            Core.platform().printMessage("Spotify sign-in: " + page);
            browse(page);
        });
    }

    /**
     * Hands a page to the OS browser. Game thread only. Where none opens, the
     * address goes to the clipboard: the player is usually in a screen that hides
     * chat, and pasting it into a browser is the one step left to them.
     */
    private static void browse(URI page) {
        try {
            Util.getOperatingSystem().open(page);
        } catch (RuntimeException failure) {
            Core.platform().setClipboard(page.toString());
            Core.notifications().warn("Spotify", "Could not open a browser; the link is on your clipboard");
        }
    }
}
