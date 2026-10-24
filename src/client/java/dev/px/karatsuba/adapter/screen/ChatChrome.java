package dev.px.karatsuba.adapter.screen;

/**
 * What the client adds to the game's chat box while it is open: how the input
 * bar moves in, and anything drawn over it.
 *
 * <p>A seam for the same reason as {@link LoadingScreen}. The adapter owns
 * <em>when</em> &mdash; the chat opening, where the game draws its input bar this
 * frame and what has been typed into it &mdash; and the client owns what that
 * looks like. The game's chat keeps everything else: typing, history,
 * suggestions, clicking a link.
 *
 * <p>One slot, not a list: there is one chat. Empty until the client
 * {@linkplain #install installs} one, and the game's own chat is unchanged while
 * it is.
 */
public interface ChatChrome {

    /** The chat has just opened. Called once per opening, before its first frame. */
    void opened();

    /**
     * @return how far below its place the input bar is drawn this frame, in scaled
     *         units; 0 at rest. Asked once per frame while the chat is open.
     */
    float slide();

    /**
     * Draws over the input bar, inside a 2D frame of its own, after the game has
     * drawn the bar and before its suggestions go over it.
     *
     * <p>The bounds are the bar's as the game drew it this frame, {@link #slide}
     * included, in the space Core's {@code Render} draws in.
     *
     * @param text what is typed in the bar
     */
    void renderInput(float x, float y, float width, float height, String text);

    /**
     * What the line being typed is predicted to become, shown after it in the
     * game's grey suggestion text and taken with Tab, as the game's own command
     * completion is.
     *
     * <p>Asked each time the text changes, and again when Tab is pressed. Only a
     * prediction that starts with {@code text} (ignoring case) and is longer is
     * shown; anything else is treated as none.
     *
     * @return the whole predicted line, or null to leave the game's suggestion alone
     */
    default String predict(String text) {
        return null;
    }

    /** Makes {@code chrome} the chat's, or leaves the game's chat alone with null. */
    static void install(ChatChrome chrome) {
        Slot.current = chrome;
    }

    /** @return the installed chrome, or null to leave the game's chat alone */
    static ChatChrome installed() {
        return Slot.current;
    }

    /** Holds the one installed chrome; an interface cannot have a field that changes. */
    final class Slot {

        private static volatile ChatChrome current;

        private Slot() {
        }
    }
}
