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
 * device code  -&gt; login.microsoftonline.com   the code the user types
 * poll token   -&gt; login.microsoftonline.com   until they finish, or it expires
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
 * <h2>Configuration</h2>
 * {@link #setClientId} takes an Azure application id with the device code flow
 * enabled and the {@code XboxLive.signin} scope granted. The default is the
 * public client id the vanilla launcher uses, which works but is not yours: an
 * application you own is what lets you see your own throttling and revoke your
 * own consent.
 *
 * <p>Every method here blocks and is called from a {@code ThreadService} worker.
 * Nothing in it touches game state except through {@link GameThread}.
 */
public final class MicrosoftAuth {

    private static final String DEVICE_CODE_URL =
            "https://login.microsoftonline.com/consumers/oauth2/v2.0/devicecode";
    private static final String TOKEN_URL =
            "https://login.microsoftonline.com/consumers/oauth2/v2.0/token";
    private static final String XBL_URL = "https://user.auth.xboxlive.com/user/authenticate";
    private static final String XSTS_URL = "https://xsts.auth.xboxlive.com/xsts/authorize";
    private static final String MC_LOGIN_URL =
            "https://api.minecraftservices.com/authentication/login_with_xbox";
    private static final String MC_PROFILE_URL =
            "https://api.minecraftservices.com/minecraft/profile";

    private static final String SCOPE = "XboxLive.signin offline_access";
    private static final String FORM = "application/x-www-form-urlencoded; charset=utf-8";

    /** The vanilla launcher's public client id. Replace with your own; see above. */
    private static final String DEFAULT_CLIENT_ID = "00000000402b5328";

    /** How long to keep polling if the service does not say. */
    private static final long DEFAULT_EXPIRY_SECONDS = 900L;

    private static volatile String clientId = DEFAULT_CLIENT_ID;

    private MicrosoftAuth() {
    }

    /** @param id an Azure application id with the device code flow enabled */
    public static void setClientId(String id) {
        clientId = id == null || id.trim().isEmpty() ? DEFAULT_CLIENT_ID : id;
    }

    /**
     * Runs the whole chain, blocking until the user finishes or it expires.
     *
     * @throws IOException on a transport failure, a rejection, or a timeout. The
     *         message is written to be shown to the user, because
     *         {@code AuthResult.failure} puts it straight on screen.
     */
    public static Session login() throws IOException {
        JsonObject device = beginDeviceCode();
        String deviceCode = device.get("device_code").getAsString();
        String userCode = device.get("user_code").getAsString();
        String verificationUri = device.get("verification_uri").getAsString();

        long intervalSeconds = device.has("interval") ? device.get("interval").getAsLong() : 5L;
        long expirySeconds = device.has("expires_in")
                ? device.get("expires_in").getAsLong()
                : DEFAULT_EXPIRY_SECONDS;

        promptUser(userCode, verificationUri);

        String msaToken = pollForToken(deviceCode, intervalSeconds, expirySeconds);
        JsonObject xbl = authenticateWithXbox(msaToken);
        String xstsToken = authorizeWithXsts(xbl.get("Token").getAsString());
        String userHash = userHashOf(xbl);
        String minecraftToken = loginWithXbox(userHash, xstsToken);

        return profileOf(minecraftToken);
    }

    // ------------------------------------------------------------ device code

    private static JsonObject beginDeviceCode() throws IOException {
        String body = "client_id=" + encode(clientId) + "&scope=" + encode(SCOPE);
        return object(Http.post(DEVICE_CODE_URL, body, FORM));
    }

    /**
     * Shows the user their code and opens the page for them.
     *
     * <p>On the game thread for both: the clipboard is a GLFW window call, and
     * handing a URI to the OS from a pool thread is the kind of thing that works
     * until the one platform where it does not.
     */
    private static void promptUser(String userCode, String verificationUri) {
        Core.notifications().info("Sign in to Microsoft",
                "Enter " + userCode + " at " + verificationUri + " (copied to your clipboard)");

        GameThread.run(() -> {
            Core.platform().setClipboard(userCode);
            try {
                Util.getOperatingSystem().open(URI.create(verificationUri));
            } catch (RuntimeException failure) {
                // A headless or locked-down desktop cannot open a browser, and that
                // is not a failed login: the code and the URL are already on screen.
                Core.log().warn("Could not open the sign-in page; enter the code manually");
            }
        });
    }

    /**
     * Polls until the user finishes.
     *
     * <p>{@code authorization_pending} is the expected answer for as long as they
     * are still typing, and it arrives as an HTTP 400. {@link Http} folds the
     * response body into the exception message rather than discarding it, which is
     * what makes "still waiting" distinguishable from "declined" here at all.
     */
    private static String pollForToken(String deviceCode, long intervalSeconds, long expirySeconds)
            throws IOException {

        String body = "grant_type=urn:ietf:params:oauth:grant-type:device_code"
                + "&client_id=" + encode(clientId)
                + "&device_code=" + encode(deviceCode);

        long deadline = System.currentTimeMillis() + expirySeconds * 1000L;
        long waitMillis = intervalSeconds * 1000L;

        while (System.currentTimeMillis() < deadline) {
            try {
                return object(Http.post(TOKEN_URL, body, FORM)).get("access_token").getAsString();
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
            try {
                Thread.sleep(waitMillis);
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
                throw new IOException("Sign-in was cancelled");
            }
        }
        throw new IOException("Sign-in expired before it was completed");
    }

    // ------------------------------------------------------------------ xbox

    private static JsonObject authenticateWithXbox(String msaToken) throws IOException {
        // "d=" marks the ticket as coming from a device code flow rather than a
        // full OAuth redirect; without it Xbox rejects an otherwise valid token.
        String body = "{\"Properties\":{\"AuthMethod\":\"RPS\",\"SiteName\":\"user.auth.xboxlive.com\","
                + "\"RpsTicket\":\"d=" + msaToken + "\"},"
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
