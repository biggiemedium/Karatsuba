package dev.px.karatsuba.adapter.event;

import dev.px.core.Core;
import dev.px.core.event.impl.CharTypedEvent;
import dev.px.core.event.impl.KeyEvent;
import dev.px.core.event.impl.MouseEvent;
import dev.px.core.event.impl.ScrollEvent;
import dev.px.core.input.Bind;
import dev.px.core.input.InputService;
import dev.px.core.input.Key;
import dev.px.core.input.Modifier;
import dev.px.core.input.MouseButton;
import dev.px.karatsuba.adapter.screen.CoreScreen;
import dev.px.karatsuba.adapter.screen.HudEditorHost;
import net.minecraft.client.MinecraftClient;
import org.lwjgl.glfw.GLFW;

import java.util.Set;
import java.util.function.Predicate;

/**
 * Raw input, from GLFW's callbacks to Core.
 *
 * <p>Called from the mixins on {@code Keyboard} and {@code Mouse} rather than from
 * a screen, so there is exactly one place that turns a physical key into something
 * Core acts on. Module keybinds work in-game, the click GUI and the HUD editor get
 * the same input, and neither path can drift from the other.
 *
 * <h2>The screen gate</h2>
 * Core has no idea what a chat box is. If every keystroke reached it, typing "f"
 * into chat would toggle whatever is bound to F. So input is only forwarded when
 * no screen is open, or when the open screen is {@link CoreScreen} &mdash; our own.
 * Vanilla screens keep their keystrokes, which is what makes typing safe without
 * Core needing to learn about text fields.
 *
 * <h2>Why a screen is routed rather than posted</h2>
 * Core subscribes to nothing: a GUI is hosted inside the game's own screen, which
 * already owns the mouse and keyboard while it is showing, so there is no event to
 * intercept and nothing to cancel. With one of ours open the input is handed
 * straight to {@code GuiService} or to the editor, and the event bus is not posted
 * to at all &mdash; which is what stops a keystroke both typing into a text box and
 * toggling a module, without anything having to cancel anything.
 *
 * <p>{@link #routeToScreen} is where the order that follows from it lives, and the
 * order is the whole point: a focused text field first, then global actions, then
 * the screen. A field being edited owns every keystroke, including the one bound to
 * the screen it is sitting in, so typing an R into a search box must not close the
 * GUI. With nothing focused the bind that opened a screen has to be the bind that
 * closes it, so actions come before the screen's own keys.
 */
public final class InputBridge {

    private static volatile boolean armed;

    private InputBridge() {
    }

    /**
     * Starts forwarding.
     *
     * <p>The mixins are installed by the loader long before Core exists, and
     * {@code Core.get()} throws rather than returning null before {@code build()},
     * so a keystroke during startup has to find this switch off.
     */
    public static void arm() {
        armed = true;
    }

    /**
     * @return whether Core consumed the key, in which case the game should not see it
     */
    public static boolean onKey(int glfwKey, int action, int glfwMods) {
        if (action == GLFW.GLFW_REPEAT || !shouldForward()) {
            return false;
        }
        Key key = Keys.fromGlfw(glfwKey);
        Set<Modifier> modifiers = Keys.modifiers(glfwMods);
        boolean pressed = action == GLFW.GLFW_PRESS;

        if (Core.hud().isEditing()) {
            // Tracked on both edges, so a modifier pressed or let go mid-drag is
            // noticed rather than waiting for the next press.
            HudEditorHost.trackModifiers(modifiers);
        }

        if (!screenOpen()) {
            if (pressed && fireKeyActions(key, modifiers)) {
                return true;
            }
            return Core.bus().post(new KeyEvent(key, modifiers, pressed)).isCancelled();
        }
        // A screen of ours owns the keyboard while it is up: a release has nowhere
        // to go, and module binds must not fire behind it.
        return !pressed || routeToScreen(key, modifiers);
    }

    /**
     * Offers a press to the open screen, in the order described on this class.
     *
     * @return whether it was consumed, which with a screen open is always
     */
    private static boolean routeToScreen(Key key, Set<Modifier> modifiers) {
        // A focused text field, or a keybind button waiting for a key, owns the
        // keystroke outright.
        if (Core.gui().isOpen() && Core.gui().keyPressed(key, modifiers)) {
            return true;
        }
        // Global actions next, so the bind that opened a screen also closes it.
        if (fireKeyActions(key, modifiers)) {
            return true;
        }
        if (Core.hud().isEditing()) {
            HudEditorHost.keyPressed(key, modifiers);
            return true;
        }
        if (key == Key.ESCAPE) {
            // Core leaves Escape to the host: what an idle screen does with it is a
            // client's decision, and here it is the usual one.
            Core.gui().close();
        }
        return true;
    }

    /**
     * Runs any registered action bound to this key.
     *
     * <p><b>Action binds are this adapter's, module binds are the bus's.</b> The
     * split is not arbitrary tidying &mdash; it is forced twice over:
     *
     * <ul>
     *   <li>{@code InputService} drops a modifier key before it matches anything,
     *       on the grounds that a modifier only qualifies a bind. That is right
     *       for a module, and wrong for the two actions here: the click GUI is on
     *       Right Shift and the editor on Right Control, both unbound in vanilla
     *       and both exactly the genre convention. Through the bus they could
     *       never fire at all.</li>
     *   <li>With a screen open nothing is posted, because the same post that
     *       reached an action would also reach every module bind. The bind that
     *       opened a screen still has to be the bind that closes it.</li>
     * </ul>
     *
     * <p>An action that fires is not posted on, which is what keeps
     * {@code InputService} from running the same one a second time.
     *
     * @return whether an action ran
     */
    private static boolean fireKeyActions(Key key, Set<Modifier> modifiers) {
        return fireActions(bind -> bind.matches(key, modifiers));
    }

    /** The same, for a bind on a mouse button. */
    private static boolean fireMouseActions(MouseButton button, Set<Modifier> modifiers) {
        // Left and right click are the game's own; binding them would make the
        // client unusable. InputService draws the same line.
        if (button == MouseButton.LEFT || button == MouseButton.RIGHT) {
            return false;
        }
        return fireActions(bind -> bind.matches(button, modifiers));
    }

    private static boolean fireActions(Predicate<Bind> matcher) {
        boolean fired = false;
        for (InputService.Action action : Core.input().getActions()) {
            if (matcher.test(action.getBinding().get())) {
                action.getBody().run();
                fired = true;
            }
        }
        return fired;
    }

    /** Text entry, which is the character the layout produced rather than the key struck. */
    public static boolean onChar(int codePoint) {
        if (!shouldForward() || Character.isISOControl(codePoint)) {
            return false;
        }
        if (screenOpen()) {
            Core.gui().charTyped((char) codePoint);
            return true;
        }
        return Core.bus().post(new CharTypedEvent((char) codePoint)).isCancelled();
    }

    public static boolean onMouseButton(int glfwButton, int action, int glfwMods) {
        if (!shouldForward()) {
            return false;
        }
        MouseButton button = MouseButton.byIndex(glfwButton);
        Set<Modifier> modifiers = Keys.modifiers(glfwMods);
        boolean pressed = action == GLFW.GLFW_PRESS;
        float x = Core.platform().getMouseX();
        float y = Core.platform().getMouseY();

        // Same rule as the keyboard: an action bind is ours, and firing one is the
        // end of that press. A button bound to the editor has to be able to leave it.
        if (pressed && fireMouseActions(button, modifiers)) {
            return true;
        }

        if (Core.hud().isEditing()) {
            HudEditorHost.trackModifiers(modifiers);
            if (pressed) {
                return HudEditorHost.mousePressed(x, y,
                        button == MouseButton.LEFT, button == MouseButton.RIGHT);
            }
            HudEditorHost.mouseReleased();
            return true;
        }
        if (Core.gui().isOpen()) {
            if (pressed) {
                Core.gui().mousePressed(x, y, button);
            } else {
                Core.gui().mouseReleased(x, y);
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

        if (Core.hud().isEditing()) {
            HudEditorHost.scrolled(amount);
            return true;
        }
        if (Core.gui().isOpen()) {
            Core.gui().scrolled(amount, x, y);
            return true;
        }
        return Core.bus().post(new ScrollEvent(amount, x, y)).isCancelled();
    }

    /** @return whether one of Core's own screens is showing inside {@link CoreScreen} */
    private static boolean screenOpen() {
        return Core.gui().isOpen() || Core.hud().isEditing();
    }

    private static boolean shouldForward() {
        if (!armed) {
            return false;
        }
        MinecraftClient client = MinecraftClient.getInstance();
        return client.currentScreen == null || client.currentScreen instanceof CoreScreen;
    }
}
