package dev.px.karatsuba.client.gui.apple.shell.section.accounts;

import dev.px.gui.Widget;
import dev.px.karatsuba.client.account.Accounts;
import dev.px.karatsuba.client.account.SignIn;

/**
 * What the sign-in card says: a title, a sentence, the code when there is one, and
 * a line on where the sign-in has got to. Words only; the card's buttons do things.
 */
public final class SignInBody extends Widget {

    SignInBody() {
    }

    private static SignIn signIn() {
        return Accounts.signIn();
    }

    public String title() {
        SignIn now = signIn();
        if (now == null) {
            return "";
        }
        return switch (now.step()) {
            case DONE -> "Signed in";
            case FAILED -> "Couldn't sign in";
            default -> "Sign in to Microsoft";
        };
    }

    public String sentence() {
        SignIn now = signIn();
        if (now == null) {
            return "";
        }
        return switch (now.step()) {
            case STARTING -> "Asking Microsoft for a code…";
            case DONE -> "Playing as " + now.message() + ".";
            case FAILED -> now.message();
            case CANCELLED -> "";
            case XBOX, MINECRAFT, PROFILE -> "You approved the sign-in. This takes a few seconds.";
            default -> "Enter this code at " + now.address() + ", then approve the sign-in.";
        };
    }

    /** @return the code, grouped for reading, while it is the thing to act on; empty otherwise */
    public String code() {
        SignIn now = signIn();
        if (now == null || now.step() != SignIn.Step.WAITING) {
            return "";
        }
        return now.code();
    }

    /** @return whether the code is the thing to act on now, rather than spent (once approved) */
    public boolean codeLive() {
        SignIn now = signIn();
        return now != null && now.step() == SignIn.Step.WAITING && now.secondsLeft() > 0L;
    }

    /** @return where the sign-in has got to, in a few words; empty when the sentence says it all */
    public String status() {
        SignIn now = signIn();
        if (now == null) {
            return "";
        }
        return switch (now.step()) {
            case WAITING -> now.secondsLeft() == 0L
                    ? "This code has expired. Cancel and try again."
                    : "Waiting for you  ·  Expires in " + clock(now.secondsLeft());
            case XBOX -> "Approved. Signing in to Xbox…";
            case MINECRAFT -> "Signing in to Minecraft…";
            case PROFILE -> "Getting your profile…";
            default -> "";
        };
    }

    /** "14:32". */
    private static String clock(long seconds) {
        return seconds / 60L + ":" + String.format("%02d", seconds % 60L);
    }
}
