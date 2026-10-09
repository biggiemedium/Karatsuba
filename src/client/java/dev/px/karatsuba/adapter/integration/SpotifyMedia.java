package dev.px.karatsuba.adapter.integration;

import dev.px.core.Core;
import dev.px.core.integration.MediaProvider;
import dev.px.core.integration.MediaTrack;
import se.michaelthelin.spotify.SpotifyApi;
import se.michaelthelin.spotify.exceptions.detailed.BadRequestException;
import se.michaelthelin.spotify.exceptions.detailed.ForbiddenException;
import se.michaelthelin.spotify.exceptions.detailed.NotFoundException;
import se.michaelthelin.spotify.exceptions.detailed.TooManyRequestsException;
import se.michaelthelin.spotify.exceptions.detailed.UnauthorizedException;
import se.michaelthelin.spotify.model_objects.IPlaylistItem;
import se.michaelthelin.spotify.model_objects.credentials.AuthorizationCodeCredentials;
import se.michaelthelin.spotify.model_objects.miscellaneous.CurrentlyPlayingContext;
import se.michaelthelin.spotify.model_objects.specification.ArtistSimplified;
import se.michaelthelin.spotify.model_objects.specification.Episode;
import se.michaelthelin.spotify.model_objects.specification.Image;
import se.michaelthelin.spotify.model_objects.specification.Track;
import se.michaelthelin.spotify.model_objects.specification.User;

import java.io.IOException;
import java.util.StringJoiner;

/**
 * Core's {@code MediaProvider}, backed by the Spotify Web API.
 *
 * <p>{@code IntegrationService} calls {@link #poll} on its own schedule, on the
 * thread pool, and only while Now Playing is switched on; this answers with one
 * request for the playback state and turns it into Core's {@link MediaTrack}. Core
 * extrapolates the position between polls, so a five-second interval still draws
 * a smooth seek bar.
 *
 * <h2>Tokens</h2>
 * An access token lasts an hour. It is kept in memory only and swapped for a new
 * one a minute before it runs out, using the refresh token from
 * {@link SpotifySettings}. Spotify may hand back a new refresh token with each
 * exchange and retire the old one, so whatever comes back is stored straight away;
 * keeping the old one would work until the next launch and then fail.
 *
 * <h2>Failures</h2>
 * <ul>
 *   <li><b>429</b>: Spotify names how long to wait, and nothing is asked until
 *       then. The last track keeps showing meanwhile.</li>
 *   <li><b>401</b> on a data call: the access token is dropped and the next poll
 *       refreshes it.</li>
 *   <li><b>A refused refresh</b> ({@code invalid_grant}): the sign-in was revoked,
 *       so it is forgotten and the player is told to connect again, rather than
 *       failing the same way every poll.</li>
 * </ul>
 *
 * <p>The playback controls need Spotify Premium and an active device; both
 * refusals are explained rather than logged. They run on the pool, never on the
 * caller's thread, since the caller is usually a key press on the game thread.
 */
public final class SpotifyMedia implements MediaProvider {

    /** Refresh this long before the access token runs out, so a poll never races its expiry. */
    private static final long EXPIRY_MARGIN_MILLIS = 60_000L;

    private static final int ARTWORK_MIN_WIDTH = 128;

    private final SpotifySettings settings;

    private volatile String accessToken;
    private volatile long accessExpiresAt;

    /** No requests before this time; set by a 429. */
    private volatile long quietUntil;

    private volatile MediaTrack last;

    private volatile String accountName;
    private volatile boolean fetchingName;

    SpotifyMedia(SpotifySettings settings) {
        this.settings = settings;
    }

    @Override
    public String getName() {
        return "Spotify";
    }

    @Override
    public boolean isAvailable() {
        return settings.isConnected();
    }

    @Override
    public MediaTrack poll() throws Exception {
        if (System.currentTimeMillis() < quietUntil) {
            return last;
        }
        try {
            CurrentlyPlayingContext context = api().getPlaybackState()
                    .additionalTypes("track,episode")
                    .build()
                    .execute();
            last = track(context);
            return last;
        } catch (TooManyRequestsException limited) {
            backOff(limited);
            return last;
        } catch (UnauthorizedException expired) {
            accessToken = null;
            throw expired;
        }
    }

    @Override
    public void skipNext() {
        control("skip", () -> api().skipToNext().build().execute());
    }

    @Override
    public void skipPrevious() {
        control("go back", () -> api().skipToPrevious().build().execute());
    }

    @Override
    public void togglePlayback() {
        MediaTrack current = last;
        if (current != null && current.isPlaying()) {
            control("pause", () -> api().pausePlayback().build().execute());
        } else {
            control("resume", () -> api().startResumePlayback().build().execute());
        }
    }

    /** Takes the tokens from a fresh sign-in, so the first poll needs no refresh. */
    void signedIn(AuthorizationCodeCredentials credentials) {
        accept(credentials);
        accountName = null;
        fetchingName = false;
    }

    /** Forgets everything held in memory; the stored sign-in is the caller's to clear. */
    void signedOut() {
        accessToken = null;
        accessExpiresAt = 0L;
        last = null;
        accountName = null;
    }

    /**
     * @return the connected account's display name, or null until it is known.
     *         Asked for once per sign-in, in the background, the first time this is
     *         called while connected: the Settings page calls it every frame.
     */
    String accountName() {
        if (accountName == null && !fetchingName && isAvailable()) {
            fetchingName = true;
            Core.threads().submit(() -> {
                try {
                    User user = api().getCurrentUsersProfile().build().execute();
                    String name = user.getDisplayName();
                    accountName = name == null || name.isEmpty() ? user.getId() : name;
                } catch (Exception failure) {
                    // Only a label; the page says "Connected" without it, and the
                    // next sign-in asks again.
                    Core.log().warn("Could not read the Spotify profile: " + failure.getMessage());
                }
            });
        }
        return accountName;
    }

    // ---------------------------------------------------------------- tokens

    /** @return a client carrying a live access token, refreshing first if it is about to lapse */
    private SpotifyApi api() throws IOException {
        return SpotifyApi.builder().setAccessToken(accessToken()).build();
    }

    private synchronized String accessToken() throws IOException {
        String token = accessToken;
        if (token != null && System.currentTimeMillis() < accessExpiresAt) {
            return token;
        }
        String clientId = settings.clientId();
        String refresh = settings.refreshToken();
        if (clientId.isEmpty() || refresh.isEmpty()) {
            throw new IOException("Spotify is not connected");
        }
        try {
            accept(SpotifyApi.builder().setClientId(clientId).build()
                    .authorizationCodePKCERefresh(clientId, refresh)
                    .build()
                    .execute());
            return accessToken;
        } catch (BadRequestException revoked) {
            // invalid_grant: the token was revoked or has lapsed, and asking again
            // every poll would only fail the same way.
            settings.storeRefreshToken("");
            signedOut();
            Spotify.tell(n -> n.warn("Spotify", "Signed out by Spotify. Connect again in Settings > Spotify"));
            throw new IOException("Spotify sign-in was revoked", revoked);
        } catch (IOException failure) {
            throw failure;
        } catch (Exception failure) {
            throw new IOException("Could not refresh the Spotify sign-in: " + failure.getMessage(), failure);
        }
    }

    private void accept(AuthorizationCodeCredentials credentials) {
        accessToken = credentials.getAccessToken();
        long lifetime = credentials.getExpiresIn() == null ? 3600L : credentials.getExpiresIn();
        accessExpiresAt = System.currentTimeMillis() + lifetime * 1000L - EXPIRY_MARGIN_MILLIS;

        String rotated = credentials.getRefreshToken();
        if (rotated != null && !rotated.isEmpty() && !rotated.equals(settings.refreshToken())) {
            settings.storeRefreshToken(rotated);
        }
    }

    private void backOff(TooManyRequestsException limited) {
        long seconds = Math.max(1, limited.getRetryAfter());
        quietUntil = System.currentTimeMillis() + seconds * 1000L;
    }

    // -------------------------------------------------------------- controls

    private interface Call {
        void run() throws Exception;
    }

    private void control(String verb, Call call) {
        if (!isAvailable()) {
            Spotify.tell(n -> n.warn("Spotify", "Not connected. Connect in Settings > Spotify"));
            return;
        }
        Core.threads().submit(() -> {
            try {
                call.run();
            } catch (ForbiddenException premium) {
                Spotify.tell(n -> n.warn("Spotify", "Spotify only lets Premium accounts " + verb + " from an app"));
            } catch (NotFoundException noDevice) {
                Spotify.tell(n -> n.warn("Spotify", "Nothing to " + verb + ": start playing on a device first"));
            } catch (TooManyRequestsException limited) {
                backOff(limited);
                Spotify.tell(n -> n.warn("Spotify", "Spotify asked us to slow down; try again shortly"));
            } catch (UnauthorizedException expired) {
                accessToken = null;
                Spotify.tell(n -> n.warn("Spotify", "Could not " + verb + "; try again"));
            } catch (Exception failure) {
                Core.log().error("Spotify " + verb + " failed", failure);
                Spotify.tell(n -> n.error("Spotify", "Could not " + verb + ": " + failure.getMessage()));
            }
        });
    }

    // --------------------------------------------------------------- mapping

    /**
     * @return the track, or null when nothing is playing &mdash; Spotify answers
     *         204 with no body then &mdash; or when the item is of a type it does
     *         not describe, which is how an advert arrives
     */
    private static MediaTrack track(CurrentlyPlayingContext context) {
        if (context == null || context.getItem() == null) {
            return null;
        }
        IPlaylistItem item = context.getItem();
        String artist = "";
        String album = "";
        String artwork = "";
        if (item instanceof Track track) {
            artist = artists(track.getArtists());
            if (track.getAlbum() != null) {
                album = nonNull(track.getAlbum().getName());
                artwork = artwork(track.getAlbum().getImages());
            }
        } else if (item instanceof Episode episode) {
            // A podcast has a show where a song has an artist, and that is what a
            // listener expects to see in the artist's place.
            if (episode.getShow() != null) {
                artist = nonNull(episode.getShow().getName());
                album = artist;
            }
            artwork = artwork(episode.getImages());
        }
        long duration = item.getDurationMs() == null ? 0L : item.getDurationMs();
        long position = context.getProgress_ms() == null ? 0L : context.getProgress_ms();
        // Sampled now rather than at the context's timestamp: that one is when
        // playback last changed state, not when this answer was true.
        return new MediaTrack(nonNull(item.getName()), artist, album, duration, position,
                System.currentTimeMillis(), Boolean.TRUE.equals(context.getIs_playing()), artwork);
    }

    private static String artists(ArtistSimplified[] artists) {
        if (artists == null) {
            return "";
        }
        StringJoiner names = new StringJoiner(", ");
        for (ArtistSimplified artist : artists) {
            if (artist != null && artist.getName() != null) {
                names.add(artist.getName());
            }
        }
        return names.toString();
    }

    /**
     * @return the smallest image at least {@link #ARTWORK_MIN_WIDTH} wide, else the
     *         largest. Spotify usually offers 640, 300 and 64 pixels; 300 is sharp in
     *         a HUD at any scale and a quarter of the GL memory of 640.
     */
    private static String artwork(Image[] images) {
        if (images == null) {
            return "";
        }
        Image best = null;
        for (Image image : images) {
            if (image == null || image.getUrl() == null) {
                continue;
            }
            int width = image.getWidth() == null ? 0 : image.getWidth();
            int bestWidth = best == null || best.getWidth() == null ? 0 : best.getWidth();
            boolean fits = width >= ARTWORK_MIN_WIDTH;
            boolean bestFits = bestWidth >= ARTWORK_MIN_WIDTH;
            if (best == null
                    || (fits && (!bestFits || width < bestWidth))
                    || (!fits && !bestFits && width > bestWidth)) {
                best = image;
            }
        }
        return best == null ? "" : best.getUrl();
    }

    private static String nonNull(String text) {
        return text == null ? "" : text;
    }
}
