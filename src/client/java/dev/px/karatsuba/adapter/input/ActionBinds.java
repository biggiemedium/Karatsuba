package dev.px.karatsuba.adapter.input;

import dev.px.core.Core;
import dev.px.core.input.Bind;
import dev.px.core.input.InputService;
import dev.px.core.input.Key;
import dev.px.core.input.Modifier;
import dev.px.core.input.MouseButton;

import java.util.Set;
import java.util.function.Predicate;

/**
 * Runs the client's action binds directly, rather than through the event bus.
 *
 * <p>An <b>action bind</b> is a key registered with {@code InputService.register}
 * &mdash; open the click GUI, open the HUD editor &mdash; as opposed to a
 * <b>module bind</b>, which toggles a module. Core delivers module binds itself
 * by subscribing to {@code KeyEvent}, and it would deliver actions the same way.
 * This class exists because for actions that path is wrong twice over:
 *
 * <ul>
 *   <li>{@code InputService} drops a modifier key before it matches anything, on
 *       the grounds that a modifier only qualifies a bind. That is right for a
 *       module and wrong for these: the genre convention is Right Shift for the
 *       click GUI and Right Control for the editor, both unbound in vanilla, and
 *       through the bus neither could ever fire.</li>
 *   <li>While an overlay is capturing, nothing is posted at all &mdash; the same
 *       post that reached an action would also reach every module bind, and
 *       typing in the client's own screen would toggle modules. But the bind that
 *       opened a screen still has to be the bind that closes it.</li>
 * </ul>
 *
 * <p>An action that fires here is not posted on, which is what stops
 * {@code InputService} running the same one a second time.
 */
public final class ActionBinds {

    private ActionBinds() {
    }

    /**
     * Runs every action bound to this key.
     *
     * @return whether one ran, in which case the keystroke is spent
     */
    public static boolean fire(Key key, Set<Modifier> modifiers) {
        return fire(bind -> bind.matches(key, modifiers));
    }

    /** The same, for a bind on a mouse button. */
    public static boolean fire(MouseButton button, Set<Modifier> modifiers) {
        // Left and right click are the game's own; binding them would make the
        // client unusable. InputService draws the same line.
        if (button == MouseButton.LEFT || button == MouseButton.RIGHT) {
            return false;
        }
        return fire(bind -> bind.matches(button, modifiers));
    }

    private static boolean fire(Predicate<Bind> matcher) {
        boolean fired = false;
        for (InputService.Action action : Core.input().getActions()) {
            if (matcher.test(action.getBinding().get())) {
                action.getBody().run();
                fired = true;
            }
        }
        return fired;
    }
}
