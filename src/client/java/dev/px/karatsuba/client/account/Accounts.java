package dev.px.karatsuba.client.account;

import dev.px.core.Core;
import dev.px.core.account.Account;
import dev.px.core.account.AccountType;
import dev.px.core.account.AuthResult;
import dev.px.karatsuba.adapter.account.MicrosoftAuth;
import dev.px.karatsuba.adapter.account.MinecraftAuthProvider;
import dev.px.karatsuba.adapter.asset.PlayerFaces;
import dev.px.karatsuba.adapter.concurrent.GameThread;
import dev.px.karatsuba.client.config.AutoSave;
import dev.px.karatsuba.client.notify.Notices;
import dev.px.karatsuba.client.notify.Source;

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
 * signing in again needs no browser. Core persists metadata, and the accounts
 * section is encrypted: it is written to {@code configs/shared/accounts.enc},
 * sealed with {@code adapter/platform/MachineKey}.
 *
 * <p>That is a real credential: anything holding it can obtain a session for that
 * account until it is revoked or expires. It is stored because the alternative is
 * a fifteen-minute browser dance every time an account is switched, which makes a
 * saved list pointless. On Windows only the same Windows user can open it;
 * elsewhere the key sits beside the file, so the encryption only stops it being
 * read by accident. {@link #forget} removes it, and signing out of Microsoft in a
 * browser revokes it.
 */
public final class Accounts {

    /**
     * Where the name a Microsoft account plays as is kept, once a sign-in has said it.
     *
     * <p>The account's own name is only the label it was saved under (usually an
     * email); the in-game name is not known until Microsoft hands over the profile.
     * Kept so the list can tell which saved account the game is playing as.
     */
    private static final String IN_GAME_NAME = "inGameName";

    /**
     * The UUID the account plays as, from its last sign-in: what its face is looked
     * up by ({@link PlayerFaces}). An account saved before this was recorded learns
     * it the first time it is seen in use.
     */
    private static final String UUID = "uuid";

    /**
     * The sign-in running now, or the last one; null before any.
     *
     * <p>One at a time, deliberately. Two concurrent Microsoft logins would each
     * want a code entered and race to swap the session, and the one that lost
     * would leave the player signed in as somebody they did not pick.
     */
    private static volatile SignIn current;

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
        forget(found.get());
        return true;
    }

    /**
     * Drops {@code account}'s stored sign-in, handing it back sealed so it can be
     * put back with {@link #keep} if the player undoes the forget.
     *
     * @return what was dropped, or null if nothing was kept
     */
    public static KeptSignIn forget(Account account) {
        String token = account.getMeta(MinecraftAuthProvider.REFRESH_TOKEN, null);
        account.setMeta(MinecraftAuthProvider.REFRESH_TOKEN, null);
        AutoSave.markDirty();
        return token == null ? null : new KeptSignIn(token);
    }

    /** Puts back a sign-in {@link #forget(Account)} dropped: an undo. */
    public static void keep(Account account, KeptSignIn kept) {
        if (kept == null) {
            return;
        }
        account.setMeta(MinecraftAuthProvider.REFRESH_TOKEN, kept.token);
        AutoSave.markDirty();
    }

    /**
     * A sign-in that was forgotten, held only for as long as it can be undone.
     *
     * <p>Sealed on purpose: nothing outside this class can read the credential
     * in it, so a screen holding one for its undo strip cannot draw it.
     */
    public static final class KeptSignIn {

        private final String token;

        private KeptSignIn(String token) {
            this.token = token;
        }
    }

    /**
     * Saves {@code account} again after a {@link #remove}, sign-in and all: an undo.
     *
     * @throws IllegalArgumentException if another account took its name meanwhile
     */
    public static Account restore(Account account) {
        return remember(account);
    }

    /**
     * @return the name {@code account} plays as: an offline account's own name, the
     *         name a Microsoft account's last sign-in gave, or null before its first
     */
    public static String inGameName(Account account) {
        return account.isOffline() ? account.getName() : account.getMeta(IN_GAME_NAME, null);
    }

    /**
     * @return what to call {@code account}: the name it plays as, or, before its
     *         first sign-in tells us that, the label it was saved under (an email)
     */
    public static String displayName(Account account) {
        String name = inGameName(account);
        return name != null ? name : account.getName();
    }

    /**
     * @return the UUID {@code account} plays as, as text; null before it has signed
     *         in. One in use without one recorded (saved before UUIDs were) takes
     *         the session's, once.
     */
    public static String uuid(Account account) {
        String uuid = account.getMeta(UUID, null);
        if (uuid == null && isInUse(account)) {
            uuid = PlayerFaces.currentUuid();
            if (uuid != null) {
                account.setMeta(UUID, uuid);
                AutoSave.markDirty();
            }
        }
        return uuid;
    }

    /** @return the UUID the game is playing as now, as text; null if the session has none */
    public static String currentUuid() {
        return PlayerFaces.currentUuid();
    }

    /** @return whether the game is playing as {@code account} now */
    public static boolean isInUse(Account account) {
        String name = inGameName(account);
        return name != null && name.equalsIgnoreCase(current());
    }

    /** @return the saved account the game is playing as now, if it is one */
    public static Optional<Account> inUse() {
        return all().stream().filter(Accounts::isInUse).findFirst();
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

    /**
     * Hears the adapter's sign-in reports and puts them on {@link #signIn()}.
     * Before the first sign-in; at boot.
     *
     * <p>The adapter shows nothing itself, so this is where a person is told the
     * code when no GUI is open: a notification and a chat line, with the address
     * and the code to type. Nothing is copied and no page is opened for them; the
     * GUI's sign-in card does both, at a press.
     */
    public static void install() {
        MicrosoftAuth.setListener(new MicrosoftAuth.Listener() {
            @Override
            public void prompt(MicrosoftAuth.Prompt prompt) {
                GameThread.run(() -> prompted(prompt));
            }

            @Override
            public void stage(MicrosoftAuth.Stage stage) {
                GameThread.run(() -> {
                    SignIn now = current;
                    if (now != null) {
                        now.reached(stage);
                    }
                });
            }
        });
    }

    /** @return the sign-in running now, or the last one, until another starts; null before any */
    public static SignIn signIn() {
        return current;
    }

    /** @return whether a sign-in is in flight */
    public static boolean isSigningIn() {
        SignIn now = current;
        return now != null && !now.isOver();
    }

    /** @return whether the sign-in in flight is {@code account}'s */
    public static boolean isSigningIn(Account account) {
        SignIn now = current;
        return now != null && !now.isOver() && now.account() == account;
    }

    /**
     * Signs in and swaps the session.
     *
     * <p>Returns immediately with the sign-in, which says how it goes from then on:
     * a Microsoft account with nothing kept waits for a person to enter a code, so
     * there is no result to wait for here.
     *
     * @return the sign-in started, or null if one was already running
     */
    public static SignIn login(Account account) {
        if (account == null) {
            return null;
        }
        if (isSigningIn()) {
            Notices.warn(Source.CLIENT, "Accounts", "A sign-in is already running");
            return null;
        }
        SignIn signIn = new SignIn(account);
        current = signIn;
        MicrosoftAuth.clearCancel();
        Notices.info(Source.CLIENT, "Accounts", "Signing in as " + account.getName());

        Core.accounts().login(account, GameThread.marshal(result -> complete(signIn, result)));
        return signIn;
    }

    /** Stops the sign-in waiting for a person. Its card closes; nothing is signed in. */
    public static void cancelSignIn() {
        SignIn now = current;
        if (now != null && !now.isOver()) {
            MicrosoftAuth.cancel();
            now.finish(SignIn.Step.CANCELLED, "");
        }
    }

    /** Puts the sign-in's code on the clipboard: only when asked, never on its own. */
    public static void copyCode() {
        SignIn now = current;
        if (now != null && now.hasPrompt()) {
            Core.platform().setClipboard(now.rawCode());
        }
    }

    /** Opens the page to enter the code on, in the person's browser: only when asked. */
    public static void openSignInPage() {
        SignIn now = current;
        if (now != null && now.hasPrompt() && !now.isOver()) {
            MicrosoftAuth.open(now.page());
        }
    }

    /** The code is ready: shown on the card, and told in words for when no GUI is open. */
    private static void prompted(MicrosoftAuth.Prompt prompt) {
        SignIn now = current;
        if (now == null || now.isOver()) {
            return;
        }
        now.prompted(prompt);
        // Chat as well as the notification: a notification times out and a code
        // does not, and the player may have looked away.
        Notices.info(Source.CLIENT, "Sign in to Microsoft", "Enter " + now.code() + " at " + now.address());
        Core.platform().printMessage("Sign in to Microsoft: enter " + now.code() + " at " + now.address()
                + " (or open Accounts in the GUI)");
    }

    /**
     * Applies the result, on the game thread.
     *
     * <p>The sign-in is finished first and unconditionally: one that failed has
     * still ended, and leaving it running would lock the client out of trying
     * again.
     */
    private static void complete(SignIn signIn, AuthResult result) {
        Account account = signIn.account();
        if (!result.isSuccessful()) {
            if (MicrosoftAuth.wasCancelled()) {
                signIn.finish(SignIn.Step.CANCELLED, "");
                Notices.info(Source.CLIENT, "Accounts", "Sign-in cancelled");
            } else {
                signIn.finish(SignIn.Step.FAILED, result.getMessage());
                Notices.error(Source.CLIENT, "Accounts", result.getMessage());
            }
            return;
        }
        Core.accounts().applySession(result.getSession());
        String name = result.getSession().getUsername();
        if (!account.isOffline()) {
            account.setMeta(IN_GAME_NAME, name);
        }
        account.setMeta(UUID, result.getSession().getUuid());
        signIn.finish(SignIn.Step.DONE, name);
        Notices.success(Source.CLIENT, "Accounts", "Signed in as " + name);

        // Three things worth keeping changed during that call and none posts a
        // setting change: the last-used timestamp Core stamps on success, the
        // refresh token the provider wrote back, and the name in game.
        AutoSave.markDirty();
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
