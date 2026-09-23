package dev.px.karatsuba.adapter.screen;

import dev.px.core.Core;
import dev.px.core.input.Key;
import dev.px.core.input.Modifier;
import dev.px.core.input.MouseButton;

import java.util.List;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * The {@link Overlay} list, and the one place the bridges ask about it.
 *
 * <p>{@code GameEventBridge} draws through this and {@code InputBridge} routes
 * through it, so neither of them names a single thing the client shows. Adding a
 * screen to the client is a {@link #register} call and no change here.
 *
 * <p>Empty until the client registers something, and inert while empty: nothing
 * captures, nothing draws, and every keystroke goes to the event bus as it would
 * with no interface at all. That is what lets the adapter be loaded before the
 * client has booted.
 */
public final class OverlayHost {

    /**
     * Copy-on-write because it is read from the render thread on every frame and
     * every keystroke, and written once at boot.
     */
    private static final List<Overlay> OVERLAYS = new CopyOnWriteArrayList<>();

    private OverlayHost() {
    }

    /**
     * Adds an overlay.
     *
     * <p>Registration order is draw order, bottom first. Input is offered in the
     * reverse, so whatever is registered last is drawn on top and asked first.
     */
    public static void register(Overlay overlay) {
        if (overlay != null && !OVERLAYS.contains(overlay)) {
            OVERLAYS.add(overlay);
        }
    }

    /**
     * @return whether anything currently needs the screen and the input
     *
     * <p>The adapter's single test for "is the client showing something". While
     * this is true a {@link CoreScreen} is kept up and input is routed rather than
     * posted; the frame after it goes false, the screen is taken down.
     */
    public static boolean isCapturing() {
        for (Overlay overlay : OVERLAYS) {
            if (overlay.isCapturing()) {
                return true;
            }
        }
        return false;
    }

    /**
     * Draws every overlay, bottom first.
     *
     * <p>One that throws is logged once and skipped, rather than being allowed to
     * take the rest of the frame &mdash; and the rest of the HUD &mdash; with it.
     */
    public static void render(float screenWidth, float screenHeight) {
        for (Overlay overlay : OVERLAYS) {
            try {
                overlay.render(screenWidth, screenHeight);
            } catch (RuntimeException failure) {
                Failures.reportOnce(overlay, failure);
            }
        }
    }

    /** Closes everything. Called when the game takes the screen away. */
    public static void dismissAll() {
        for (Overlay overlay : OVERLAYS) {
            overlay.dismiss();
        }
    }

    // ----------------------------------------------------------------- input

    public static boolean keyPressed(Key key, Set<Modifier> modifiers) {
        for (int i = OVERLAYS.size() - 1; i >= 0; i--) {
            Overlay overlay = OVERLAYS.get(i);
            if (overlay.isCapturing() && overlay.keyPressed(key, modifiers)) {
                return true;
            }
        }
        return false;
    }

    public static boolean charTyped(char character) {
        for (int i = OVERLAYS.size() - 1; i >= 0; i--) {
            Overlay overlay = OVERLAYS.get(i);
            if (overlay.isCapturing() && overlay.charTyped(character)) {
                return true;
            }
        }
        return false;
    }

    public static boolean mousePressed(float x, float y, MouseButton button) {
        for (int i = OVERLAYS.size() - 1; i >= 0; i--) {
            Overlay overlay = OVERLAYS.get(i);
            if (overlay.isCapturing() && overlay.mousePressed(x, y, button)) {
                return true;
            }
        }
        return false;
    }

    /** Delivered to every capturing overlay: a release ends a drag rather than being claimed. */
    public static void mouseReleased(float x, float y) {
        for (Overlay overlay : OVERLAYS) {
            if (overlay.isCapturing()) {
                overlay.mouseReleased(x, y);
            }
        }
    }

    public static boolean scrolled(float amount, float x, float y) {
        for (int i = OVERLAYS.size() - 1; i >= 0; i--) {
            Overlay overlay = OVERLAYS.get(i);
            if (overlay.isCapturing() && overlay.scrolled(amount, x, y)) {
                return true;
            }
        }
        return false;
    }

    /** Delivered to every capturing overlay: more than one may care what is held. */
    public static void modifiersChanged(Set<Modifier> modifiers) {
        for (Overlay overlay : OVERLAYS) {
            if (overlay.isCapturing()) {
                overlay.modifiersChanged(modifiers);
            }
        }
    }

    /**
     * Remembers which overlays have already thrown.
     *
     * <p>A broken overlay throws every frame, and sixty stack traces a second in
     * {@code latest.log} is worse than the bug it is reporting.
     */
    private static final class Failures {

        private static final Set<Class<?>> REPORTED =
                java.util.concurrent.ConcurrentHashMap.newKeySet();

        private Failures() {
        }

        static void reportOnce(Overlay overlay, RuntimeException failure) {
            if (REPORTED.add(overlay.getClass())) {
                Core.log().error("Overlay " + overlay.getClass().getSimpleName()
                        + " threw while drawing; further failures will not be logged", failure);
            }
        }
    }
}
