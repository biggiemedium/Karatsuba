package dev.px.karatsuba.adapter.screen;

import dev.px.core.input.Key;
import dev.px.core.input.Modifier;
import dev.px.core.input.MouseButton;

import java.util.Set;

/**
 * Something the client puts on the screen, and the adapter draws and feeds input to.
 *
 * <p>This is the seam that keeps the adapter out of the client's appearance.
 * The adapter owns the things only the game can answer &mdash; when a 2D frame
 * happens, where the cursor is, which GLFW code was struck, whether a game screen
 * has to be up &mdash; and it owns none of what is drawn or what a key means.
 * An implementation of this interface answers those, and every implementation
 * lives in {@code dev.px.karatsuba.client}.
 *
 * <h2>Capturing</h2>
 * {@link #isCapturing()} is the one question the adapter asks constantly. While
 * any overlay is capturing, {@link CoreScreen} stays up (so the cursor is free and
 * the world stops taking clicks) and input is routed here instead of being posted
 * on the event bus. While none is, the game keeps its input and only
 * {@link #render} is called.
 *
 * <p>That split is what stops a keystroke both typing into a text field and
 * toggling a module, without anything having to cancel anything.
 *
 * <h2>Order</h2>
 * Overlays are drawn in registration order and offered input in the reverse, so
 * the one drawn last &mdash; the one on top &mdash; is the one asked first. That
 * is the only ordering rule, and it is the conventional one.
 *
 * <p>Input is offered only to overlays that are capturing, so an implementation
 * never has to re-check its own state in six methods. {@link #render} is called on
 * every overlay either way, because an overlay that is not capturing may still
 * have something to draw &mdash; the HUD is exactly that.
 *
 * @see OverlayHost
 */
public interface Overlay {

    /**
     * @return whether this overlay currently needs the screen and the input
     *
     * <p>Called every frame and on every keystroke, so it must be cheap and must
     * not allocate.
     */
    boolean isCapturing();

    /**
     * Draws this overlay, inside the adapter's 2D frame.
     *
     * <p>Called on every overlay every frame, capturing or not, after modules have
     * drawn. An overlay with nothing to show this frame returns immediately.
     *
     * @param screenWidth  scaled screen width, the space Core's {@code Render} draws in
     * @param screenHeight scaled screen height
     */
    void render(float screenWidth, float screenHeight);

    /**
     * Closes whatever this overlay was showing.
     *
     * <p>Called when the game takes the screen away for its own reasons &mdash; a
     * disconnect, a death screen, a menu opened by something else. Closing
     * something that is not open must be a no-op.
     */
    default void dismiss() {
    }

    /**
     * A key was pressed while this overlay was capturing.
     *
     * @return whether it was consumed; returning false offers it to the next
     *         overlay down, and then to the client's action binds
     */
    default boolean keyPressed(Key key, Set<Modifier> modifiers) {
        return false;
    }

    /** A character was typed. Text entry, rather than the key that produced it. */
    default boolean charTyped(char character) {
        return false;
    }

    /** @return whether the press was consumed */
    default boolean mousePressed(float x, float y, MouseButton button) {
        return false;
    }

    /** The button came up. Delivered to every capturing overlay, consumed by none. */
    default void mouseReleased(float x, float y) {
    }

    /** @return whether the wheel movement was consumed. Positive scrolls up. */
    default boolean scrolled(float amount, float x, float y) {
        return false;
    }

    /**
     * The held modifiers changed.
     *
     * <p>Delivered on both edges, so a modifier pressed or released mid-drag is
     * noticed rather than waiting for the next keystroke. What a modifier means is
     * the overlay's business; the adapter only reports which are down.
     */
    default void modifiersChanged(Set<Modifier> modifiers) {
    }
}
