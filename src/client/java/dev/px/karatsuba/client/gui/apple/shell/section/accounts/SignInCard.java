package dev.px.karatsuba.client.gui.apple.shell.section.accounts;

import dev.px.core.layout.Size;
import dev.px.gui.Widget;
import dev.px.gui.container.Column;
import dev.px.gui.container.Row;
import dev.px.karatsuba.client.account.Accounts;
import dev.px.karatsuba.client.account.SignIn;
import dev.px.karatsuba.client.gui.apple.shell.card.PushButton;
import dev.px.karatsuba.client.gui.apple.shell.frame.Spacer;
import dev.px.karatsuba.client.gui.apple.shell.style.Animations;
import dev.px.karatsuba.client.gui.apple.shell.style.Palette;

import java.util.List;

/**
 * The Microsoft sign-in card ({@code gui/apple/DESIGN.md} §2.6c): a popover over
 * the Accounts page while a sign-in needs a person. It shows the code large, and
 * does nothing on its own: the code is copied, and microsoft.com opened, only when
 * their buttons are pressed.
 *
 * <p>Follows {@link Accounts#signIn()} live rather than holding one sign-in, so
 * Try Again carries on in the same card. Closing it (Escape, a press outside) only
 * hides it; the sign-in keeps waiting, and the account's page offers "Show Code…".
 * Cancel stops it.
 */
public final class SignInCard extends Column {

    /** How long Copy Code says "Copied" after a press. */
    private static final float COPIED_MS = 2000f;

    private long copiedAt;

    /** @param close closes the card, after Done or Cancel */
    SignInCard(Runnable close) {
        gap(Palette.GAP_CARD);
        add(new SignInBody());

        Row actions = add(new Row());
        actions.gap(Palette.GAP_ACTIONS);
        PushButton copy = actions.add(new PushButton(() -> copied() ? "Copied" : "Copy Code", this::copy));
        copy.visibleWhen(() -> is(SignIn.Step.WAITING));
        actions.add(new Spacer(0f)).grow(1f);

        PushButton cancel = actions.add(new PushButton("Cancel", () -> {
            Accounts.cancelSignIn();
            close.run();
        }));
        cancel.visibleWhen(() -> signIn() != null && !signIn().isOver());
        PushButton open = actions.add(new PushButton(() -> "Open " + host() + "…", Accounts::openSignInPage).primary());
        open.visibleWhen(() -> is(SignIn.Step.WAITING));

        PushButton dismiss = actions.add(new PushButton("Close", close));
        dismiss.visibleWhen(() -> is(SignIn.Step.FAILED));
        PushButton again = actions.add(new PushButton("Try Again", () -> Accounts.login(signIn().account())).primary());
        again.visibleWhen(() -> is(SignIn.Step.FAILED));
        PushButton done = actions.add(new PushButton("Done", close).primary());
        done.visibleWhen(() -> is(SignIn.Step.DONE));
    }

    private static SignIn signIn() {
        return Accounts.signIn();
    }

    private static boolean is(SignIn.Step step) {
        SignIn now = signIn();
        return now != null && now.step() == step;
    }

    /** @return the site to open, as a person says it: "microsoft.com" */
    private static String host() {
        SignIn now = signIn();
        String address = now == null ? "" : now.address();
        int slash = address.indexOf('/');
        return slash < 0 ? address : address.substring(0, slash);
    }

    private void copy() {
        Accounts.copyCode();
        copiedAt = System.nanoTime();
    }

    private boolean copied() {
        return copiedAt != 0L && Animations.progress(copiedAt, COPIED_MS) < 1f;
    }

    /** As wide as {@link Palette#SHEET_WIDTH}, whatever its words would like. */
    @Override
    protected Size childrenNatural(List<Widget> visible) {
        Size natural = super.childrenNatural(visible);
        return Size.of(Palette.SHEET_WIDTH - Palette.INSET_CONTENT * 2f, natural.getHeight());
    }
}
