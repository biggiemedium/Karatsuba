package dev.px.karatsuba.client.account;

import dev.px.core.account.Account;
import dev.px.karatsuba.adapter.account.MicrosoftAuth;

/**
 * One sign-in, from the press of Sign In to its end: what it is waiting for, the
 * code a person has to enter, and how it finished.
 *
 * <p>Read by the GUI's sign-in card and written by {@link Accounts}, always on the
 * game thread: the adapter's reports arrive on a worker and are marshalled here
 * before they touch this. Nothing in it is a credential; the code is single-use
 * and only good with the device that asked for it.
 */
public final class SignIn {

    /** Where a sign-in has got to. */
    public enum Step {
        /** Asking Microsoft for a code, or trying a kept sign-in. */
        STARTING,
        /** Showing the code, waiting for the person to enter it. */
        WAITING,
        /** Approved; signing in to Xbox. */
        XBOX,
        /** Signing in to Minecraft. */
        MINECRAFT,
        /** Asking for the name to play as. */
        PROFILE,
        DONE,
        FAILED,
        CANCELLED;

        /** @return whether the sign-in has ended, one way or another */
        public boolean isOver() {
            return this == DONE || this == FAILED || this == CANCELLED;
        }
    }

    private final Account account;
    private Step step = Step.STARTING;
    private MicrosoftAuth.Prompt prompt;
    private String message = "";

    SignIn(Account account) {
        this.account = account;
    }

    public Account account() {
        return account;
    }

    public Step step() {
        return step;
    }

    public boolean isOver() {
        return step.isOver();
    }

    /** @return whether a code was ever shown: a sign-in that needed a person, not a kept one */
    public boolean hasPrompt() {
        return prompt != null;
    }

    /** @return the code to enter, grouped in fours for reading ("ABCD EFGH"); empty before there is one */
    public String code() {
        if (prompt == null) {
            return "";
        }
        String code = prompt.userCode();
        return code.length() == 8 ? code.substring(0, 4) + " " + code.substring(4) : code;
    }

    /** @return the code exactly as Microsoft wants it typed, for the clipboard */
    public String rawCode() {
        return prompt == null ? "" : prompt.userCode();
    }

    /** @return where to enter it, as a person would type it: "microsoft.com/link" */
    public String address() {
        if (prompt == null) {
            return "";
        }
        return prompt.verificationUri().replaceFirst("^https?://", "").replaceFirst("^www\\.", "").replaceFirst("/$", "");
    }

    /** @return the page to open: with the code filled in, when Microsoft offers that */
    public String page() {
        return prompt == null ? "" : prompt.openUri();
    }

    /** @return whole seconds until the code expires; 0 once it has */
    public long secondsLeft() {
        return prompt == null ? 0L : Math.max(0L, (prompt.expiresAt() - System.currentTimeMillis()) / 1000L);
    }

    /** @return how it ended: the name now played as, or what went wrong; empty while it runs */
    public String message() {
        return message;
    }

    // --------------------------------------------------------------- writes

    void prompted(MicrosoftAuth.Prompt prompt) {
        if (!isOver()) {
            this.prompt = prompt;
            this.step = Step.WAITING;
        }
    }

    void reached(MicrosoftAuth.Stage stage) {
        if (isOver()) {
            return;
        }
        step = switch (stage) {
            case XBOX -> Step.XBOX;
            case MINECRAFT -> Step.MINECRAFT;
            case PROFILE -> Step.PROFILE;
        };
    }

    void finish(Step how, String message) {
        if (!isOver()) {
            this.step = how;
            this.message = message == null ? "" : message;
        }
    }
}
