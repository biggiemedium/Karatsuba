package dev.px.karatsuba.adapter.integration;

import dev.px.core.setting.SettingHolder;
import dev.px.core.setting.impl.StringSetting;
import dev.px.karatsuba.adapter.concurrent.GameThread;

/**
 * What Spotify needs kept: whose app to sign in through, and the sign-in itself.
 *
 * <p>A config section of its own, shared by every profile: which Spotify account
 * you listen on does not change with the settings you play on, the same reason
 * accounts and friends are shared.
 *
 * <h2>Why every player brings a Client ID</h2>
 * Spotify has no public client the way Microsoft does. An app in development mode
 * only serves the accounts its owner has added to it, so a Client ID baked into the
 * client would work for whoever registered it and refuse everyone else. Each
 * player registers an app of their own, which takes a minute and is free: see
 * {@link Spotify#REDIRECT_URI} for the one value it has to be given.
 *
 * <h2>Where the credential goes</h2>
 * The refresh token is kept in {@code shared/spotify.enc}, encrypted with
 * {@code MachineKey} like a Microsoft account's, for the same reason it is kept at
 * all: without it every launch is a browser round trip. It grants control of your playback and
 * nothing else on the account, and Disconnect in Settings or removing the app
 * at spotify.com/account/apps revokes it.
 */
public final class SpotifySettings extends SettingHolder {

    /** The config section id, and its file name. */
    public static final String ID = "spotify";

    private final StringSetting clientId = text("Client ID", "")
            .describe("From your app's page at developer.spotify.com. Not the Client Secret, which is never needed")
            .maxLength(64);

    /**
     * Never shown: a credential is not a setting anybody edits. Saved with the
     * section, since a section saves every setting, visible or not, and left alone
     * by the page's "reset all", which only touches what it shows.
     */
    private final StringSetting refreshToken = text("Refresh Token", "")
            .maxLength(1024)
            .visibleWhen(() -> false);

    @Override
    public String getName() {
        return "Spotify";
    }

    /** @return the Client ID setting itself, for a page to show and edit */
    public StringSetting clientIdSetting() {
        return clientId;
    }

    /** @return the Client ID as typed, trimmed; empty when there is none */
    public String clientId() {
        return clientId.get().trim();
    }

    /** @return the kept sign-in, or empty */
    String refreshToken() {
        return refreshToken.get();
    }

    /**
     * Keeps a sign-in, or drops it given an empty string.
     *
     * <p>Called from the poll and login workers. The write itself is marshalled:
     * a setting change posts on the bus, and the autosave that hears it is not
     * written for a pool thread.
     */
    void storeRefreshToken(String token) {
        String value = token == null ? "" : token;
        GameThread.run(() -> refreshToken.set(value));
    }

    /** @return whether there is both an app and a sign-in to use */
    boolean isConnected() {
        return !clientId().isEmpty() && !refreshToken().isEmpty();
    }
}
