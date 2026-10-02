package dev.px.karatsuba.adapter.account;

import dev.px.core.Core;
import dev.px.core.account.Account;
import dev.px.core.account.AccountType;
import dev.px.core.account.AuthProvider;
import dev.px.core.account.AuthResult;
import dev.px.core.account.Session;
import dev.px.karatsuba.adapter.concurrent.GameThread;
import dev.px.karatsuba.adapter.mixin.game.MinecraftClientAccessor;
import net.minecraft.client.MinecraftClient;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Optional;
import java.util.UUID;

/**
 * Core's {@code AuthProvider}, filled in for Minecraft.
 *
 * <p>{@code AccountService} is complete without this: it stores accounts, saves
 * them to the profile, and runs logins on the thread pool. What it cannot do is
 * either half of an actual login, and it says so &mdash; the OAuth chain needs an
 * HTTP client and a browser handoff, and applying a session means writing a field
 * whose type and name change every version. With no provider installed
 * {@code login} returns "No authentication provider is installed" and the saved
 * account list is decorative.
 *
 * <h2>The two threads</h2>
 * {@link #authenticate} and {@link #authenticateOffline} are called on a
 * {@code ThreadService} worker and may block for as long as the user takes.
 * {@link #apply} writes game state and must not, so it is marshalled onto the
 * game thread here rather than trusting every caller to remember.
 *
 * <h2>What a session swap does and does not cover</h2>
 * Replacing the session is what lets the client join a server as the new account,
 * and it is all a session swap has ever done. The profile keys behind signed chat
 * and the telemetry sender are built once from the launcher's session and are not
 * rebuilt here, so signed chat keeps using the original account's keys until the
 * game restarts. Servers that require chat signing will reject messages; joining,
 * the player list and skins are unaffected.
 */
public final class MinecraftAuthProvider implements AuthProvider {

    /**
     * How the game derives an offline UUID, and the reason a name maps to the same
     * player every time on a cracked server. Matching it exactly is what keeps an
     * offline account's inventory and position across sessions.
     */
    private static final String OFFLINE_PREFIX = "OfflinePlayer:";

    /**
     * Where a refresh token is kept between sessions.
     *
     * <p>{@code Account} metadata, which Core persists with the rest of the
     * config. See the class note on what that means.
     */
    public static final String REFRESH_TOKEN = "msaRefreshToken";

    @Override
    public AuthResult authenticate(Account account) {
        if (account.getType() != AccountType.MICROSOFT) {
            return authenticateOffline(account.getName());
        }
        try {
            MicrosoftAuth.Login login = signIn(account);
            // Written back every time: Microsoft invalidates the old token on each
            // exchange, so keeping the previous one would work exactly once.
            account.setMeta(REFRESH_TOKEN, login.refreshToken());
            return AuthResult.success(login.session());
        } catch (IOException failure) {
            // The messages out of MicrosoftAuth are written for a person to read,
            // so they go through unchanged; AuthResult puts them straight on screen.
            return AuthResult.failure(failure.getMessage());
        }
    }

    /**
     * Uses a kept token if there is one, and falls back to the browser if it is
     * refused.
     *
     * <p>The fallback is the part that matters. A refresh token expires, and is
     * revoked by a password change or by signing out elsewhere &mdash; treating
     * that as a failed login would leave a saved account permanently broken with
     * no way back except deleting it.
     */
    private static MicrosoftAuth.Login signIn(Account account) throws IOException {
        String stored = account.getMeta(REFRESH_TOKEN, null);
        if (stored == null || stored.isEmpty()) {
            return MicrosoftAuth.login();
        }
        try {
            return MicrosoftAuth.refresh(stored);
        } catch (IOException stale) {
            Core.log().info("Stored sign-in for " + account.getName()
                    + " was refused; asking for a fresh one");
            account.setMeta(REFRESH_TOKEN, null);
            return MicrosoftAuth.login();
        }
    }

    @Override
    public AuthResult authenticateOffline(String username) {
        String name = username == null ? "" : username.trim();
        if (name.isEmpty()) {
            return AuthResult.failure("An offline account needs a name");
        }
        // The game's own bound, and a server will refuse anything longer.
        if (name.length() > 16) {
            return AuthResult.failure("Names are at most 16 characters");
        }
        return AuthResult.success(new Session(name, offlineUuid(name).toString(), "", AccountType.OFFLINE));
    }

    @Override
    public void apply(Session session) {
        // Deferred rather than run inline even when already on the game thread: the
        // caller is usually a GUI button handler, and swapping the session out from
        // under a screen that is mid-render is a needless way to find out what still
        // holds a reference to the old one.
        GameThread.later(() -> {
            MinecraftClient client = MinecraftClient.getInstance();
            ((MinecraftClientAccessor) client).karatsuba$setSession(toGameSession(session));
        });
    }

    /**
     * Builds the game's session type from Core's.
     *
     * <p>The xuid and client id are the launcher's telemetry identifiers. They are
     * left empty rather than invented: the game treats them as optional, and a
     * fabricated value is worse than an absent one.
     */
    private static net.minecraft.client.session.Session toGameSession(Session session) {
        return new net.minecraft.client.session.Session(
                session.getUsername(),
                UUID.fromString(session.getUuid()),
                session.getAccessToken(),
                Optional.empty(),
                Optional.empty(),
                session.getType() == AccountType.MICROSOFT
                        ? net.minecraft.client.session.Session.AccountType.MSA
                        : net.minecraft.client.session.Session.AccountType.LEGACY);
    }

    private static UUID offlineUuid(String name) {
        return UUID.nameUUIDFromBytes((OFFLINE_PREFIX + name).getBytes(StandardCharsets.UTF_8));
    }
}
