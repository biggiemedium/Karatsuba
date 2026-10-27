package dev.px.karatsuba.adapter.account;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.px.core.Core;
import dev.px.core.account.AccountType;
import dev.px.core.account.Session;
import dev.px.core.util.net.Http;
import dev.px.karatsuba.adapter.concurrent.GameThread;
import net.minecraft.util.Util;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.Map;
import java.util.UUID;

/**
 * The Microsoft login chain, end to end.
 *
 * <p>Four services and six requests stand between a person and a playable
 * session, and none of them are Core's business: Core owns the account list, the
 * threading and the result type, and stops at {@code AuthProvider}. This is the
 * other side of that seam.
 *
 * <pre>
 * device code  -&gt; login.live.com              the code the user types
 * poll token   -&gt; login.live.com              until they finish, or it expires
 * XBL          -&gt; user.auth.xboxlive.com      MSA token  -&gt; Xbox token + user hash
 * XSTS         -&gt; xsts.auth.xboxlive.com      Xbox token -&gt; a token Mojang accepts
 * login        -&gt; api.minecraftservices.com   XSTS       -&gt; Minecraft access token
 * profile      -&gt; api.minecraftservices.com   the name and UUID to play as
 * </pre>
 *
 * <h2>Why the device code flow</h2>
 * The alternatives both need something a mod cannot have. An embedded browser is
 * a dependency larger than this entire client, and a redirect-URI flow needs a
 * loopback HTTP server and a registered redirect, which turns a login into a
 * firewall prompt. The device code flow needs neither: the user is shown a short
 * code, types it into a page on any device, and this polls until it is accepted.
 *
 * <h2>Two front doors, one chain</h2>
 * The default is Microsoft's own public Minecraft client, {@code 00000000402b5328}.
 * It is a legacy Microsoft Account application, not an Azure one, so it signs in
 * at {@code login.live.com} with the {@code MBI_SSL} scope; Azure's
 * {@code login.microsoftonline.com} answers it with {@code AADSTS700016}, "not
 * found in the directory". It needs no registration and no approval, which is
 * why it is the default.
 *
 * <p>{@link #setClientId} takes an Azure application id instead, and the first
 * two requests then go to Azure's v2.0 endpoints. Everything from XBL on is the
 * same either way, except for one prefix on the Xbox ticket; see {@link Flow}.
 * Be aware that Mojang refuses {@code login_with_xbox} for an Azure application
 * it has not reviewed, so a new registration fails at the fifth request, not the
 * first.
 *
 * <p>A refresh token is only good with the client id that issued it. Changing the
 * id costs every saved account one more browser sign-in, which the provider's
 * fallback handles on its own.
 *
 * <p>Every method here blocks and is called from a {@code ThreadService} worker.
 * Nothing in it touches game state except through {@link GameThread}.
 */
public final class MicrosoftAuth {

    /**
     * Where the Microsoft half of the chain happens, and how its token is handed
     * to Xbox.
     *
     * @param deviceCodeParams anything the device code request needs beyond the
     *        client id and scope
     * @param ticketPrefix {@code "d="} marks an Azure token; a live.com token goes
     *        to Xbox as it is, and either one sent the other way is rejected
     */
    private record Flow(String deviceCodeUrl, String tokenUrl, String scope,
                        String deviceCodeParams, String ticketPrefix) {
    }

    /** Microsoft's public Minecraft client, on the legacy Microsoft Account endpoints. */
    private static final Flow LIVE = new Flow(
            "https://login.live.com/oauth20_connect.srf",
            "https://login.live.com/oauth20_token.srf",
            // Issues a refresh token without asking for offline_access.
            "service::user.auth.xboxlive.com::MBI_SSL",
            "&response_type=device_code",
            "");

    /** An Azure application of your own, on the v2.0 endpoints. */
    private static final Flow AZURE = new Flow(
            "https://login.microsoftonline.com/consumers/oauth2/v2.0/devicecode",
            "https://login.microsoftonline.com/consumers/oauth2/v2.0/token",
            "XboxLive.signin offline_access",
            "",
            "d=");

    private static final String XBL_URL = "https://user.auth.xboxlive.com/user/authenticate";
    private static final String XSTS_URL = "https://xsts.auth.xboxlive.com/xsts/authorize";
    private static final String MC_LOGIN_URL =
            "https://api.minecraftservices.com/authentication/login_with_xbox";
    private static final String MC_PROFILE_URL =
            "https://api.minecraftservices.com/minecraft/profile";

    private static final String FORM = "application/x-www-form-urlencoded; charset=utf-8";

    /** Microsoft's public Minecraft client. Signs in through {@link #LIVE}. */
    private static final String DEFAULT_CLIENT_ID = "00000000402b5328";

    /** How long to keep polling if the service does not say. */
    private static final long DEFAULT_EXPIRY_SECONDS = 900L;

    /** How often a cancel is noticed while waiting between polls. */
    private static final long CANCEL_CHECK_MILLIS = 250L;

    private static volatile String clientId = DEFAULT_CLIENT_ID;

    /** Who hears about a sign-in's progress; nobody until the client says. */
    private static volatile Listener listener = Listener.NONE;

    /** Set to stop a sign-in waiting for a person; cleared by {@link #clearCancel} before the next. */
    private static volatile boolean cancelled;

    private MicrosoftAuth() {
    }

    /**
     * What a person has to do to finish a sign-in: type {@code userCode} at
     * {@code verificationUri} before {@code expiresAt}.
     *
     * @param openUri the page to open for them: the same page with the code already
     *        filled in when Microsoft offers one, else {@code verificationUri}
     * @param expiresAt epoch millis
     */
    public record Prompt(String userCode, String verificationUri, String openUri, long expiresAt) {
    }

    /** How far a sign-in has got once the person has approved it. */
    public enum Stage {
        /** Trading Microsoft's token for an Xbox one. */
        XBOX,
        /** Trading the Xbox token for a Minecraft one. */
        MINECRAFT,
        /** Asking for the name and UUID to play as. */
        PROFILE
    }

    /**
     * Hears about a sign-in as it goes, so the client can show it.
     *
     * <p>This class shows nothing itself: whether the code goes in a card, a
     * notification or chat, and whether the page is opened, is the client's to
     * decide. Called on the {@code ThreadService} worker running the sign-in.
     */
    public interface Listener {

        Listener NONE = new Listener() {
        };

        /** The code is ready and the sign-in is waiting for the person. */
        default void prompt(Prompt prompt) {
        }

        /** The person approved, and the chain has reached {@code stage}. Also during a silent refresh. */
        default void stage(Stage stage) {
        }
    }

    /** @param listener who hears about sign-ins; null for nobody */
    public static void setListener(Listener listener) {
        MicrosoftAuth.listener = listener == null ? Listener.NONE : listener;
    }

    /** Stops a sign-in waiting for a person; it fails with "Sign-in was cancelled". */
    public static void cancel() {
        cancelled = true;
    }

    /** Forgets a cancel, before a new sign-in starts. */
    public static void clearCancel() {
        cancelled = false;
    }

    /** @return whether the last sign-in was cancelled rather than failing */
    public static boolean wasCancelled() {
        return cancelled;
    }

    /**
     * Opens {@code uri} in the person's browser, on the game thread: handing a URI
     * to the OS from a pool thread is the kind of thing that works until the one
     * platform where it does not.
     */
    public static void open(String uri) {
        GameThread.run(() -> {
            try {
                Util.getOperatingSystem().open(URI.create(uri));
            } catch (RuntimeException failure) {
                // A headless or locked-down desktop cannot open a browser; the code
                // and the address are still on screen to type by hand.
                Core.log().warn("Could not open " + uri + "; open it by hand");
            }
        });
    }

    /**
     * @param id an Azure application id with the device code flow enabled and the
     *        {@code XboxLive.signin} scope granted; null or blank for the default
     */
    public static void setClientId(String id) {
        clientId = id == null || id.trim().isEmpty() ? DEFAULT_CLIENT_ID : id.trim();
    }

    /** @return the endpoints the current client id signs in through */
    private static Flow flow() {
        return DEFAULT_CLIENT_ID.equals(clientId) ? LIVE : AZURE;
    }

    /**
     * A completed sign-in.
     *
     * <p>Carries the refresh token as well as the session, because the caller is
     * the only thing that knows where to keep it. Microsoft issues a fresh one on
     * every exchange and invalidates the old, so whatever stored the last one has
     * to replace it or the next sign-in falls back to the browser.
     *
     * @param refreshToken may be null if the service declined to issue one
     */
    public record Login(Session session, String refreshToken) {
    }

    /**
     * Runs the whole chain, blocking until the user finishes or it expires.
     *
     * <p>This is the one that waits for a person to enter a code. Prefer {@link #refresh} when a
     * token has been kept, and fall back to this when it is refused.
     *
     * @throws IOException on a transport failure, a rejection, or a timeout. The
     *         message is written to be shown to the user, because
     *         {@code AuthResult.failure} puts it straight on screen.
     */
    public static Login login() throws IOException {
        JsonObject device = beginDeviceCode();
        String deviceCode = device.get("device_code").getAsString();
        String userCode = device.get("user_code").getAsString();
        String verificationUri = device.get("verification_uri").getAsString();

        long intervalSeconds = device.has("interval") ? device.get("interval").getAsLong() : 5L;
        long expirySeconds = device.has("expires_in")
                ? device.get("expires_in").getAsLong()
                : DEFAULT_EXPIRY_SECONDS;

        // The spec makes this optional and Microsoft does not always send it. When
        // it does, it is the same page with the code already filled in, which
        // removes the one step of this flow a person can get wrong.
        String openUri = device.has("verification_uri_complete")
                ? device.get("verification_uri_complete").getAsString()
                : verificationUri;

        if (cancelled) {
            throw new IOException("Sign-in was cancelled");
        }
        listener.prompt(new Prompt(userCode, verificationUri, openUri,
                System.currentTimeMillis() + expirySeconds * 1000L));

        return complete(pollForTokens(deviceCode, intervalSeconds, expirySeconds));
    }

    /**
     * Signs in again with a token kept from a previous sign-in, with no browser
     * and no waiting.
     *
     * <p>What makes a saved account worth saving. The device code flow takes a
     * person up to fifteen minutes and cannot be automated; this takes a second
     * and is the difference between a list of accounts and a list of reminders.
     *
     * @throws IOException if the token has expired or been revoked, in which case
     *         the caller should fall back to {@link #login()}
     */
    public static Login refresh(String refreshToken) throws IOException {
        Flow flow = flow();
        String body = "grant_type=refresh_token"
                + "&client_id=" + encode(clientId)
                + "&refresh_token=" + encode(refreshToken)
                + "&scope=" + encode(flow.scope());

        return complete(object(Http.post(flow.tokenUrl(), body, FORM)));
    }

    /**
     * The half of the chain that is the same either way: Microsoft token in,
     * Minecraft session out.
     *
     * <p>Split so the device code flow and the refresh flow cannot drift apart;
     * everything after the first exchange is identical and there is no reason for
     * two copies of it.
     */
    private static Login complete(JsonObject tokens) throws IOException {
        String msaToken = tokens.get("access_token").getAsString();

        listener.stage(Stage.XBOX);
        JsonObject xbl = authenticateWithXbox(msaToken);
        String xstsToken = authorizeWithXsts(xbl.get("Token").getAsString());
        String userHash = userHashOf(xbl);
        listener.stage(Stage.MINECRAFT);
        String minecraftToken = loginWithXbox(userHash, xstsToken);

        // Present for MBI_SSL, and for Azure because its scope asks for
        // offline_access. Absent is not an error; it only means the next sign-in
        // goes the long way round.
        String refreshToken = tokens.has("refresh_token")
                ? tokens.get("refresh_token").getAsString()
                : null;

        listener.stage(Stage.PROFILE);
        return new Login(profileOf(minecraftToken), refreshToken);
    }

    // ------------------------------------------------------------ device code

    private static JsonObject beginDeviceCode() throws IOException {
        Flow flow = flow();
        String body = "client_id=" + encode(clientId)
                + "&scope=" + encode(flow.scope())
                + flow.deviceCodeParams();
        return object(Http.post(flow.deviceCodeUrl(), body, FORM));
    }

    /**
     * Polls until the user finishes.
     *
     * <p>{@code authorization_pending} is the expected answer for as long as they
     * are still typing, and it arrives as an HTTP 400. {@link Http} folds the
     * response body into the exception message rather than discarding it, which is
     * what makes "still waiting" distinguishable from "declined" here at all.
     */
    private static JsonObject pollForTokens(String deviceCode, long intervalSeconds, long expirySeconds)
            throws IOException {

        String body = "grant_type=urn:ietf:params:oauth:grant-type:device_code"
                + "&client_id=" + encode(clientId)
                + "&device_code=" + encode(deviceCode);

        long deadline = System.currentTimeMillis() + expirySeconds * 1000L;
        long waitMillis = intervalSeconds * 1000L;

        while (System.currentTimeMillis() < deadline) {
            if (cancelled) {
                throw new IOException("Sign-in was cancelled");
            }
            try {
                return object(Http.post(flow().tokenUrl(), body, FORM));
            } catch (IOException pending) {
                String message = pending.getMessage() == null ? "" : pending.getMessage();
                if (message.contains("slow_down")) {
                    // The service is asking for a longer interval, and ignoring it
                    // is how a poll turns into a lockout.
                    waitMillis += 5_000L;
                } else if (!message.contains("authorization_pending")) {
                    throw pending;
                }
            }
            // In short slices, so a cancel is noticed in a quarter of a second
            // rather than at the next poll.
            long wakeAt = System.currentTimeMillis() + waitMillis;
            while (!cancelled && System.currentTimeMillis() < wakeAt) {
                try {
                    Thread.sleep(Math.min(CANCEL_CHECK_MILLIS, Math.max(1L, wakeAt - System.currentTimeMillis())));
                } catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                    throw new IOException("Sign-in was cancelled");
                }
            }
        }
        throw new IOException("Sign-in expired before it was completed");
    }

    // ------------------------------------------------------------------ xbox

    private static JsonObject authenticateWithXbox(String msaToken) throws IOException {
        // "d=" marks an Azure token; a live.com one goes as it is. The wrong one
        // and Xbox rejects an otherwise valid token.
        String body = "{\"Properties\":{\"AuthMethod\":\"RPS\",\"SiteName\":\"user.auth.xboxlive.com\","
                + "\"RpsTicket\":\"" + flow().ticketPrefix() + msaToken + "\"},"
                + "\"RelyingParty\":\"http://auth.xboxlive.com\",\"TokenType\":\"JWT\"}";

        return object(Http.postJson(XBL_URL, body, accept()));
    }

    private static String authorizeWithXsts(String xblToken) throws IOException {
        String body = "{\"Properties\":{\"SandboxId\":\"RETAIL\",\"UserTokens\":[\"" + xblToken + "\"]},"
                + "\"RelyingParty\":\"rp://api.minecraftservices.com/\",\"TokenType\":\"JWT\"}";
        try {
            return object(Http.postJson(XSTS_URL, body, accept())).get("Token").getAsString();
        } catch (IOException rejected) {
            throw new IOException(explainXstsFailure(rejected), rejected);
        }
    }

    /**
     * Turns an XSTS rejection into something a person can act on.
     *
     * <p>These are the only failures in the chain a user can fix themselves, and
     * the raw response is an {@code XErr} number, so leaving it unexplained makes
     * an ordinary account problem look like a broken client.
     */
    private static String explainXstsFailure(IOException failure) {
        String message = failure.getMessage() == null ? "" : failure.getMessage();
        if (message.contains("2148916233")) {
            return "That Microsoft account has no Xbox profile; create one and try again";
        }
        if (message.contains("2148916235")) {
            return "Xbox Live is not available in that account's region";
        }
        if (message.contains("2148916238")) {
            return "That account is a child account and must be added to a family";
        }
        return "Xbox sign-in was rejected: " + message;
    }

    // ------------------------------------------------------------- minecraft

    private static String loginWithXbox(String userHash, String xstsToken) throws IOException {
        String body = "{\"identityToken\":\"XBL3.0 x=" + userHash + ";" + xstsToken + "\"}";
        return object(Http.postJson(MC_LOGIN_URL, body, accept())).get("access_token").getAsString();
    }

    private static Session profileOf(String minecraftToken) throws IOException {
        Map<String, String> headers = Collections.singletonMap("Authorization", "Bearer " + minecraftToken);
        JsonObject profile;
        try {
            profile = object(Http.get(MC_PROFILE_URL, headers));
        } catch (IOException noProfile) {
            // A 404 here means the login worked and the account simply does not own
            // the game, which is worth saying plainly rather than as a status code.
            throw new IOException("That account does not own Minecraft", noProfile);
        }
        return new Session(
                profile.get("name").getAsString(),
                dashed(profile.get("id").getAsString()).toString(),
                minecraftToken,
                AccountType.MICROSOFT);
    }

    // --------------------------------------------------------------- helpers

    private static String userHashOf(JsonObject xbl) throws IOException {
        try {
            return xbl.getAsJsonObject("DisplayClaims")
                    .getAsJsonArray("xui").get(0).getAsJsonObject()
                    .get("uhs").getAsString();
        } catch (RuntimeException malformed) {
            throw new IOException("Xbox returned a response this client did not understand", malformed);
        }
    }

    /** The profile API returns an undashed UUID, and {@link UUID} will not parse one. */
    private static UUID dashed(String undashed) {
        return UUID.fromString(undashed.replaceFirst(
                "(\\p{XDigit}{8})(\\p{XDigit}{4})(\\p{XDigit}{4})(\\p{XDigit}{4})(\\p{XDigit}{12})",
                "$1-$2-$3-$4-$5"));
    }

    private static Map<String, String> accept() {
        return Collections.singletonMap("Accept", "application/json");
    }

    private static JsonObject object(String json) throws IOException {
        try {
            return JsonParser.parseString(json).getAsJsonObject();
        } catch (RuntimeException malformed) {
            throw new IOException("Sign-in service returned something that was not JSON", malformed);
        }
    }

    private static String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }
}
