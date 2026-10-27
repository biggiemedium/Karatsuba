package dev.px.karatsuba.client.gui.apple.shell.section.accounts;

import dev.px.core.account.Account;
import dev.px.karatsuba.client.account.Accounts;
import dev.px.karatsuba.client.gui.apple.shell.widget.Times;

/**
 * What the Accounts pages say about an account, in one place so the list and the
 * account's page never put it two ways. Words only: a credential, or any part of
 * one, is never among them ({@code gui/apple/DESIGN.md} §2.6c).
 */
final class AccountWords {

    private AccountWords() {
    }

    /** @return a few words for a list row */
    static String status(Account account) {
        if (Accounts.isSigningIn(account)) {
            return "Signing in…";
        }
        if (Accounts.isInUse(account)) {
            return "In use";
        }
        if (account.isOffline()) {
            return "Offline";
        }
        return Accounts.isRemembered(account) ? "Signs in without a code" : "Asks for a code to sign in";
    }

    /** @return "Microsoft" or "Offline" */
    static String kind(Account account) {
        return account.isOffline() ? "Offline" : "Microsoft";
    }

    /** @return the line under an account's name on its page: its kind, and what it is doing */
    static String caption(Account account) {
        String state = Accounts.isSigningIn(account) ? "Signing in…"
                : Accounts.isInUse(account) ? "In use" : "";
        return state.isEmpty() ? kind(account) + " account" : kind(account) + " account  ·  " + state;
    }

    /** @return the name in game, or what is known of it before the first sign-in */
    static String inGameName(Account account) {
        String name = Accounts.inGameName(account);
        return name == null ? "Known after the first sign-in" : name;
    }

    static String lastUsed(Account account) {
        return account.getLastUsedAt() <= 0L ? "Never" : Times.ago(account.getLastUsedAt());
    }
}
