package dev.px.karatsuba.client.account;

import dev.px.core.Core;
import dev.px.core.account.Account;
import dev.px.core.account.AccountType;
import dev.px.core.account.AuthResult;
import dev.px.karatsuba.adapter.account.MinecraftAuthProvider;
import dev.px.karatsuba.adapter.concurrent.GameThread;
import dev.px.karatsuba.client.config.AutoSave;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * Signing in, and the list of accounts to sign in as.
 *
 * <p>Core owns the list: {@code AccountService} is a {@code Registry<Account>}
 * that is also a {@code ConfigSection}, registered by {@code Core.start()}, so
 * accounts are saved and loaded with everything else and none of that is written
 * here. What Core deliberately does not own is what a client <em>does</em> around
 * a login &mdash; which thread the result lands on, what the player is told, and
 * when the config is written. That is this class.
 *
 * <pre>{@code
 * Accounts.addMicrosoft("main");
 * Accounts.login(Accounts.find("main").orElseThrow());
 * }</pre>
 *
 * <h2>The result comes back on a worker thread</h2>
 * {@code AccountService.login} runs the whole chain on {@code ThreadService},
 * which it has to: a Microsoft sign-in can block for fifteen minutes waiting for
 * a person. The callback therefore arrives off the game thread, and swapping the
 * session or raising a notification from there is a data race with the renderer.
 * {@link GameThread#marshal} is what puts it back, and this is the class that
 * knows to use it.
 *
 * <h2>Where the credentials go</h2>
 * A Microsoft account keeps a <b>refresh token</b> in its metadata so that
 * signing in again needs no browser. Core persists metadata, which means that
 * token is written to {@code .minecraft/karatsuba/configs/*.json} <b>in plain
 * text</b>.
 *
 * <p>That is a real credential: anything holding it can obtain a session for that
 * account until it is revoked or expires. It is stored because the alternative is
 * a fifteen-minute browser dance every time an account is switched, which makes a
 * saved list pointless &mdash; but it is worth knowing, and {@link #forget} is
 * how it is removed. Signing out of Microsoft in a browser revokes it too.
 */
public final class Accounts {

    /**
     * Whether a sign-in is already running.
     *
     * <p>One at a time, deliberately. Two concurrent Microsoft logins would each
     * open a browser and race to swap the session, and the one that lost would
     * leave the player signed in as somebody they did not pick.
     */
    private static volatile boolean signingIn;

    private Accounts() {
    }

    // -------------------------------------------------------------- the list

    /**
     * Adds an offline account.
     *
     * <p>No credentials and no network: the name is hashed into a UUID exactly as
     * a server in offline mode would do it. Useful on a LAN world and refused by
     * anything online.
     */
    public static Account addOffline(String name) {
        return remember(Account.offline(name.trim()));
    }

    /**
     * Adds a Microsoft account.
     *
     * <p>The label is what the list shows and is the client's own &mdash; nothing
     * is known about the real account until the first successful sign-in, which
     * is when the in-game name arrives. Naming it after the email is the usual
     * choice and is what the command does.
     */
    public static Account addMicrosoft(String label, String email) {
        return remember(Account.microsoft(label.trim(), email));
    }

    public static boolean remove(String name) {
        Optional<Account> found = find(name);
        if (found.isEmpty()) {
            return false;
        }
        Core.accounts().remove(found.get());
        AutoSave.markDirty();
        return true;
    }

    /**
     * Drops a stored sign-in without removing the account.
     *
     * <p>The next login goes the long way round. For a shared machine, or for
     * getting a credential out of the config file without losing the entry.
     */
    public static boolean forget(String name) {
        Optional<Account> found = find(name);
        if (found.isEmpty()) {
            return false;
        }
        found.get().setMeta(MinecraftAuthProvider.REFRESH_TOKEN, null);
        AutoSave.markDirty();
        return true;
    }

    /** @return whether this account has a sign-in kept, so logging in needs no browser */
    public static boolean isRemembered(Account account) {
        return account.getType() == AccountType.MICROSOFT
                && account.getMeta(MinecraftAuthProvider.REFRESH_TOKEN, null) != null;
    }

    public static Optional<Account> find(String name) {
        return Core.accounts().getAccounts().find(name);
    }

    /**
     * @return every saved account, most recently used first
     *
     * <p>Sorted rather than in registration order, because the account somebody
     * wants next is overwhelmingly the one they had last.
     */
    public static List<Account> all() {
        List<Account> sorted = new ArrayList<>(Core.accounts().getAccounts().all());
        sorted.sort(Comparator.comparingLong(Account::getLastUsedAt).reversed());
        return sorted;
    }

    /** @return the name the game is currently playing as */
    public static String current() {
        return Core.platform().getUsername();
    }

    // ------------------------------------------------------------- signing in

    /** @return whether a sign-in is in flight */
    public static boolean isSigningIn() {
        return signingIn;
    }

    /**
     * Signs in and swaps the session.
     *
     * <p>Returns immediately; everything visible happens later, through
     * notifications. A Microsoft account with nothing kept will open a browser and
     * wait for a person, so there is no useful value to return and no point
     * blocking for one.
     */
    public static void login(Account account) {
        if (account == null) {
            return;
        }
        if (signingIn) {
            Core.notifications().warn("Accounts", "A sign-in is already running");
            return;
        }
        signingIn = true;
        Core.notifications().info("Accounts", announce(account));

        Core.accounts().login(account, GameThread.marshal(result -> complete(account, result)));
    }

    /**
     * Applies the result, on the game thread.
     *
     * <p>The flag is cleared first and unconditionally: a sign-in that failed has
     * still finished, and leaving it set would lock the client out of trying
     * again.
     */
    private static void complete(Account account, AuthResult result) {
        signingIn = false;

        if (!result.isSuccessful()) {
            Core.notifications().error("Accounts", result.getMessage());
            return;
        }
        Core.accounts().applySession(result.getSession());
        Core.notifications().success("Accounts", "Signed in as " + result.getSession().getUsername());

        // Two things worth keeping changed during that call and neither posts a
        // setting change: the last-used timestamp Core stamps on success, and the
        // refresh token the provider wrote back.
        AutoSave.markDirty();
    }

    private static String announce(Account account) {
        if (account.getType() != AccountType.MICROSOFT) {
            return "Signing in as " + account.getName();
        }
        return isRemembered(account)
                ? "Signing in as " + account.getName()
                : "Signing in as " + account.getName() + "; a browser will open";
    }

    /**
     * @throws IllegalArgumentException if the name is taken, which is Core's
     *         {@code Registry} refusing a duplicate
     */
    private static Account remember(Account account) {
        Account added = Core.accounts().add(account);
        AutoSave.markDirty();
        return added;
    }
}
