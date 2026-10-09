package dev.px.karatsuba.adapter.integration;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import se.michaelthelin.spotify.SpotifyApi;
import se.michaelthelin.spotify.model_objects.credentials.AuthorizationCodeCredentials;

import java.io.IOException;
import java.io.OutputStream;
import java.net.BindException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Consumer;

/**
 * One Spotify sign-in: the authorization code flow with PKCE, end to end.
 *
 * <pre>
 * verifier, challenge   made here; the verifier never leaves this object
 * authorize             accounts.spotify.com, in the player's browser
 * callback              http://127.0.0.1:{port}/callback, served here, for one request
 * token                 accounts.spotify.com: code + verifier -&gt; access and refresh token
 * </pre>
 *
 * <h2>Why PKCE and a loopback server</h2>
 * Spotify offers no device code flow, so the browser has to come back to us, and
 * a loopback redirect is the one way back a desktop app has. PKCE is what makes
 * that safe without a client secret: a secret shipped in a mod jar is public, and
 * the verifier stands in for it, made fresh for each sign-in and never sent until
 * the code is exchanged. The {@code state} value ties the callback to this sign-in,
 * so a page that fires a request at the port cannot plant a code of its own.
 *
 * <p>The server binds {@code 127.0.0.1} only, never all interfaces, and stops as
 * soon as it has an answer or gives up. Spotify requires the explicit loopback
 * address in redirect URIs; {@code localhost} is refused.
 *
 * <p>{@link #run} blocks for as long as the player takes and is called from a
 * {@code ThreadService} worker.
 */
final class SpotifyLogin {

    /** What the client asks to do: read what is playing, and skip and pause it. */
    private static final String SCOPES =
            "user-read-playback-state user-read-currently-playing user-modify-playback-state";

    /** How long the browser has before the sign-in is abandoned and the port let go. */
    private static final long TIMEOUT_MINUTES = 5L;

    private static final SecureRandom RANDOM = new SecureRandom();

    private final String clientId;
    private final CompletableFuture<String> code = new CompletableFuture<>();
    private final String state = random(16);

    SpotifyLogin(String clientId) {
        this.clientId = clientId;
    }

    /**
     * Signs in.
     *
     * @param openPage handed the authorization page once the callback is listening;
     *                 it opens the browser and puts the address on screen
     * @return the tokens, the refresh token among them
     * @throws IOException with a message written for the player, for every way it
     *                     can end other than success or cancellation
     * @throws CancellationException when {@link #cancel} stopped it
     */
    AuthorizationCodeCredentials run(Consumer<URI> openPage) throws IOException {
        String verifier = random(64);
        SpotifyApi api = SpotifyApi.builder()
                .setClientId(clientId)
                .setRedirectUri(Spotify.REDIRECT_URI)
                .build();

        HttpServer server = listen();
        try {
            URI page = api.authorizationCodePKCEUri(challenge(verifier))
                    .state(state)
                    .scope(SCOPES)
                    .build()
                    .execute();
            openPage.accept(page);

            String authorizationCode = await();
            return api.authorizationCodePKCE(authorizationCode, verifier)
                    .build()
                    .execute();
        } catch (IOException | CancellationException failure) {
            throw failure;
        } catch (Exception failure) {
            // The library's own errors: Spotify refusing the exchange, which is
            // almost always a Client ID or redirect URI that does not match the app.
            throw new IOException("Spotify refused the sign-in: " + failure.getMessage()
                    + ". Check the Client ID, and that the app lists " + Spotify.REDIRECT_URI, failure);
        } finally {
            server.stop(0);
        }
    }

    /** Abandons the sign-in: the waiting worker wakes and the port is let go. */
    void cancel() {
        code.completeExceptionally(new CancellationException());
    }

    private HttpServer listen() throws IOException {
        URI redirect = Spotify.REDIRECT_URI;
        try {
            HttpServer server = HttpServer.create(
                    new InetSocketAddress(InetAddress.getByName(redirect.getHost()), redirect.getPort()), 0);
            server.createContext(redirect.getPath(), this::callback);
            server.start();
            return server;
        } catch (BindException taken) {
            throw new IOException("Port " + redirect.getPort() + " is in use, so Spotify has nowhere to"
                    + " send you back to. Close whatever holds it and try again", taken);
        }
    }

    private String await() throws IOException {
        try {
            return code.get(TIMEOUT_MINUTES, TimeUnit.MINUTES);
        } catch (TimeoutException late) {
            throw new IOException("Nobody finished signing in within " + TIMEOUT_MINUTES + " minutes");
        } catch (CancellationException | InterruptedException stopped) {
            if (stopped instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            throw new CancellationException("Sign-in cancelled");
        } catch (ExecutionException refused) {
            Throwable cause = refused.getCause();
            if (cause instanceof CancellationException) {
                throw new CancellationException("Sign-in cancelled");
            }
            throw new IOException(cause.getMessage(), cause);
        }
    }

    /**
     * The one request the server exists for.
     *
     * <p>Answers the browser whatever happens, so the tab never hangs, and only
     * completes the sign-in for a request carrying this sign-in's state: anything
     * else is somebody else's request and is turned away without ending ours.
     */
    private void callback(HttpExchange exchange) throws IOException {
        Map<String, String> query = query(exchange.getRequestURI().getRawQuery());
        if (!state.equals(query.get("state"))) {
            respond(exchange, 400, "This sign-in link is not the one Karatsuba is waiting for.");
            return;
        }
        String error = query.get("error");
        if (error != null) {
            code.completeExceptionally(new IOException("access_denied".equals(error)
                    ? "Spotify access was declined"
                    : "Spotify refused the sign-in: " + error));
            respond(exchange, 200, "Sign-in cancelled. You can close this tab.");
            return;
        }
        String received = query.get("code");
        if (received == null || received.isEmpty()) {
            respond(exchange, 400, "Spotify sent no code back.");
            return;
        }
        code.complete(received);
        respond(exchange, 200, "Spotify is connected. You can close this tab and go back to the game.");
    }

    private static void respond(HttpExchange exchange, int status, String message) throws IOException {
        byte[] body = ("<!doctype html><meta charset=utf-8><title>Karatsuba</title>"
                + "<body style=\"font:16px system-ui;margin:3em\">" + message)
                .getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "text/html; charset=utf-8");
        exchange.sendResponseHeaders(status, body.length);
        try (OutputStream out = exchange.getResponseBody()) {
            out.write(body);
        }
    }

    private static Map<String, String> query(String raw) {
        Map<String, String> out = new HashMap<>();
        if (raw == null) {
            return out;
        }
        for (String pair : raw.split("&")) {
            int at = pair.indexOf('=');
            if (at > 0) {
                out.put(URLDecoder.decode(pair.substring(0, at), StandardCharsets.UTF_8),
                        URLDecoder.decode(pair.substring(at + 1), StandardCharsets.UTF_8));
            }
        }
        return out;
    }

    /** The S256 challenge: the verifier's SHA-256, base64url without padding (RFC 7636). */
    private static String challenge(String verifier) {
        try {
            byte[] hash = MessageDigest.getInstance("SHA-256").digest(verifier.getBytes(StandardCharsets.US_ASCII));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(hash);
        } catch (NoSuchAlgorithmException impossible) {
            // Every JRE is required to ship SHA-256.
            throw new IllegalStateException(impossible);
        }
    }

    /** Base64url of {@code bytes} random bytes: 64 make an 86-character verifier, inside the 43 to 128 allowed. */
    private static String random(int bytes) {
        byte[] data = new byte[bytes];
        RANDOM.nextBytes(data);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(data);
    }
}
