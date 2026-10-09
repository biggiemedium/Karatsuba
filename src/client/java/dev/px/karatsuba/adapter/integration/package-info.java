/**
 * The transports behind Core's integration seams.
 *
 * <p>Core's {@code dev.px.core.integration} declares {@code MediaProvider} and
 * {@code PresenceProvider} and drives them from {@code IntegrationService}: when
 * to poll, on which thread, and whether the player has switched them on. It
 * carries no API client and no credentials. This package is where those live.
 *
 * <p>{@link dev.px.karatsuba.adapter.integration.Spotify} is the media provider,
 * backed by the Spotify Web API (spotify-web-api-java, nested in the mod jar). The
 * library is used nowhere outside this package: the client reaches Spotify through
 * {@code Spotify} and reads the result as Core's {@code MediaTrack}, so swapping
 * the backend for the OS media session, say, would touch nothing above here.
 *
 * <p>There is no presence provider yet. Discord's needs a native library, which
 * is a separate decision from this one.
 */
package dev.px.karatsuba.adapter.integration;
