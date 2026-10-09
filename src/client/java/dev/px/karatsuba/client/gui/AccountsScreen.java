package dev.px.karatsuba.client.gui;

import dev.px.karatsuba.adapter.screen.Gui;
import dev.px.core.Core;
import dev.px.core.account.Account;
import dev.px.gui.legacy.GuiStyle;
import dev.px.gui.legacy.Panel;
import dev.px.gui.legacy.Screen;
import dev.px.core.render.Render;
import dev.px.karatsuba.client.account.Accounts;
import dev.px.karatsuba.client.gui.component.Button;

/**
 * Picks a saved account and signs in as it.
 *
 * <p>The half of account management that chat is bad at: reading a list and
 * choosing from it. Adding, removing and forgetting need typing, so they are
 * {@code .account} and are not duplicated here &mdash; two ways to add an
 * account is two places to keep the validation right.
 *
 * <p>Rebuilt on open rather than kept in step, because the list changes from
 * chat, and reconciling two views of the same registry is more code than
 * throwing one away. Opening a screen is not a hot path.
 */
public final class AccountsScreen extends Screen {

    private final Panel body = add(new Panel());

    public AccountsScreen() {
        super("Accounts");
        body.setBackground(true);
        rebuild();
    }

    /**
     * Rebuilds the list.
     *
     * <p>Called again after a sign-in finishes so the "playing as" row and the
     * ordering catch up &mdash; most recently used first means the list reorders
     * itself under the button that was just pressed, which is the correct
     * behaviour and worth seeing happen.
     */
    private void rebuild() {
        body.clearChildren();

        for (Account account : Accounts.all()) {
            body.add(new Button(label(account), () -> signIn(account))
                    .tooltip(tooltip(account)));
        }
        if (Accounts.all().isEmpty()) {
            body.add(new Button("No accounts saved", () -> {
            }).tooltip("Add one from chat with .account add <email>"));
        }
        body.add(new Button("Close", () -> Gui.service().close()));
    }

    private void signIn(Account account) {
        Accounts.login(account);
        // The sign-in itself finishes later, on a worker thread; this only
        // refreshes what the list says about what is happening now.
        rebuild();
    }

    /** The row text: the account, and a marker for the one being played. */
    private static String label(Account account) {
        return account.getName().equals(Accounts.current())
                ? account.getName() + "  <"
                : account.getName();
    }

    private static String tooltip(Account account) {
        if (account.isOffline()) {
            return "Offline account, no sign-in needed";
        }
        return Accounts.isRemembered(account)
                ? "Signs in without a browser"
                : "Opens a browser to sign in";
    }

    @Override
    protected void layoutChildren() {
        float width = GuiStyle.windowWidth() * 1.5f;
        body.layout((getScreenWidth() - width) / 2f, getScreenHeight() / 4f, width);
    }

    @Override
    protected void renderBackdrop(float x, float y, float w, float h) {
        Render.rect(0f, 0f, getScreenWidth(), getScreenHeight(), GuiStyle.backdrop());
    }
}
