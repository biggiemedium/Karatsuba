package dev.px.karatsuba.adapter.input;

import dev.px.core.Core;
import dev.px.core.event.impl.CharTypedEvent;
import dev.px.core.event.impl.KeyEvent;
import dev.px.core.event.impl.MouseEvent;
import dev.px.core.event.impl.ScrollEvent;
import dev.px.core.input.Key;
import dev.px.core.input.Modifier;
import dev.px.core.input.MouseButton;
import dev.px.karatsuba.adapter.event.Events;
import dev.px.karatsuba.adapter.screen.CoreScreen;
import dev.px.karatsuba.adapter.screen.Overlay;
import dev.px.karatsuba.adapter.screen.OverlayHost;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.Screen;
import org.lwjgl.glfw.GLFW;

import java.util.Set;

/**
 * Raw input, from GLFW's callbacks to Core and to the client's overlays.
 *
 * <p>Called from the mixins on {@code Keyboard} and {@code Mouse} rather than from
 * a screen, so there is exactly one place that turns a physical key into something
 * acted on. Module keybinds work in-game, the client's screens get the same input,
 * and neither path can drift from the other.
 *
 * <h2>The screen gate</h2>
 * Core has no idea what a chat box is. If every keystroke reached it, typing "f"
 * into chat would toggle whatever is bound to F. So input is only forwarded when
 * no screen is open, or when the open screen is {@link CoreScreen} &mdash; ours.
 * Vanilla screens keep their keystrokes, which is what makes typing safe without
 * Core needing to learn about text fields.
 *
 * <h2>Two destinations, never both</h2>
 * Every method below is the same shape, and the branch is always
 * {@link OverlayHost#isCapturing()}:
 *
 * <ol>
 *   <li><b>Nothing capturing.</b> Action binds get first refusal, then the input
 *       is posted on the event bus for modules and Core's own services.</li>
 *   <li><b>Something capturing.</b> The input is handed to the overlays and
 *       <em>nothing is posted</em>. That is what stops a keystroke both typing
 *       into a text field and toggling a module, with nothing having to cancel
 *       anything.</li>
 * </ol>
 *
 * <h2>Who gets a key first</h2>
 * Overlays, then action binds, then the floor. An overlay that is showing owns its
 * own keys &mdash; a focused text field owns every keystroke, the HUD editor owns
 * its arrows &mdash; because that is what being in a mode means. Everything an
 * overlay does not claim falls through to the action binds, which is what keeps
 * the bind that opened a screen able to close it. Whatever neither wants is
 * swallowed, because a screen of ours is up and the game must not see it.
 *
 * <p>This class decides that order and nothing else. Which keys an overlay claims,
 * and what they do, is written in {@code dev.px.karatsuba.client}.
 */
public final class InputBridge {

    private InputBridge() {
    }

    // ------------------------------------------------------------- keyboard

    /**
     * @return whether the keystroke was consumed, in which case the game must not
     *         see it
     */
    public static boolean onKey(int glfwKey, int action, int glfwMods) {
        if (action == GLFW.GLFW_REPEAT || !shouldForward()) {
            return false;
        }
        Key key = Keys.fromGlfw(glfwKey);
        Set<Modifier> modifiers = Keys.modifiers(glfwMods);
        boolean pressed = action == GLFW.GLFW_PRESS;

        // On both edges, so a modifier let go mid-drag is noticed rather than
        // waiting for the next press.
        OverlayHost.modifiersChanged(modifiers);

        if (!OverlayHost.isCapturing()) {
            if (pressed && ActionBinds.fire(key, modifiers)) {
                return true;
            }
            return Core.bus().post(new KeyEvent(key, modifiers, pressed)).isCancelled();
        }

        // An overlay owns the keyboard while it is up: a release has nowhere to go,
        // and module binds must not fire behind it. Either way the game sees none
        // of it, which is why every path below returns true.
        if (pressed && !OverlayHost.keyPressed(key, modifiers)) {
            ActionBinds.fire(key, modifiers);
        }
        return true;
    }

    /** Text entry: the character the layout produced, rather than the key struck. */
    public static boolean onChar(int codePoint) {
        if (!shouldForward() || Character.isISOControl(codePoint)) {
            return false;
        }
        char character = (char) codePoint;

        if (OverlayHost.isCapturing()) {
            OverlayHost.charTyped(character);
            return true;
        }
        return Core.bus().post(new CharTypedEvent(character)).isCancelled();
    }

    // ---------------------------------------------------------------- mouse

    public static boolean onMouseButton(int glfwButton, int action, int glfwMods) {
        if (!shouldForward()) {
            return false;
        }
        MouseButton button = MouseButton.byIndex(glfwButton);
        Set<Modifier> modifiers = Keys.modifiers(glfwMods);
        boolean pressed = action == GLFW.GLFW_PRESS;
        float x = Core.platform().getMouseX();
        float y = Core.platform().getMouseY();

        OverlayHost.modifiersChanged(modifiers);

        // Same rule as the keyboard, one step earlier: a button bound to an overlay
        // has to be able to leave it, and a click inside one is not where that
        // decision can be made.
        if (pressed && ActionBinds.fire(button, modifiers)) {
            return true;
        }

        if (OverlayHost.isCapturing()) {
            if (pressed) {
                OverlayHost.mousePressed(x, y, button);
            } else {
                OverlayHost.mouseReleased(x, y);
            }
            return true;
        }
        return Core.bus().post(new MouseEvent(button, modifiers, pressed, x, y)).isCancelled();
    }

    public static boolean onScroll(double vertical) {
        if (!shouldForward() || vertical == 0d) {
            return false;
        }
        // Core wants a direction and a magnitude, not the platform's raw delta,
        // which is a different number on a trackpad than on a wheel.
        float amount = (float) Math.signum(vertical);
        float x = Core.platform().getMouseX();
        float y = Core.platform().getMouseY();

        if (OverlayHost.isCapturing()) {
            OverlayHost.scrolled(amount, x, y);
            return true;
        }
        return Core.bus().post(new ScrollEvent(amount, x, y)).isCancelled();
    }

    // ------------------------------------------------------------- internal

    /**
     * @return whether input should reach the client at all
     *
     * <p>False until Core is up, and false whenever a screen the game owns is
     * showing &mdash; chat, the inventory, a sign editor. {@link Overlay}s are
     * shown inside {@link CoreScreen}, which is ours and therefore allowed.
     */
    private static boolean shouldForward() {
        if (!Events.isArmed()) {
            return false;
        }
        Screen showing = MinecraftClient.getInstance().currentScreen;
        return showing == null || showing instanceof CoreScreen;
    }
}
