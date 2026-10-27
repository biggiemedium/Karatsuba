package dev.px.karatsuba.client.gui.apple.shell.section.accounts;

import dev.px.core.account.Account;
import dev.px.karatsuba.client.account.Accounts;
import dev.px.karatsuba.client.gui.apple.shell.setting.ControlRow;

/**
 * A saved account in its card ({@code gui/apple/DESIGN.md} §2.6c): its monogram,
 * with the presence dot while the game is playing as it, its name, a few words on
 * how it signs in, and a ›. The whole row opens its page; signing in is done there.
 */
public final class AccountRow extends ControlRow {

    private final Account account;
    private final Runnable open;

    AccountRow(Account account, Runnable open) {
        this.account = account;
        this.open = open;
    }

    @Override
    public String name() {
        return account.getName();
    }

    public boolean isInUse() {
        return Accounts.isInUse(account);
    }

    /** @return a few words on how it signs in: "In use", "Asks for a code to sign in", "Offline" */
    public String status() {
        return AccountWords.status(account);
    }

    @Override
    protected boolean pressed(float x, float y) {
        open.run();
        return true;
    }

    @Override
    protected boolean activated() {
        open.run();
        return true;
    }

    /** Nothing to put back: the row goes somewhere, it holds no value. */
    @Override
    protected void reset() {
    }

    @Override
    public boolean isFocusable() {
        return true;
    }
}
