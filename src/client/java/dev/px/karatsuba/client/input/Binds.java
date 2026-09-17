package dev.px.karatsuba.client.input;

import dev.px.core.Core;
import dev.px.core.input.Bind;
import dev.px.core.input.Key;
import dev.px.karatsuba.adapter.screen.CoreScreen;
import dev.px.karatsuba.client.gui.ProfilesScreen;

/**
 * Keyboard actions that are not modules.
 *
 * <p>Core persists each of these as an ordinary {@code BindSetting}, so they are
 * rebindable from the click GUI and survive a restart like any other setting. The
 * defaults below are only what a fresh install starts with &mdash; a profile that
 * already names a bind wins over them, which is why changing a default does
 * nothing for a config that has one saved.
 *
 * <h2>Right Shift, and why {@code InputBridge} fires these</h2>
 * The genre convention is Right Shift for the click GUI, and Core will not deliver
 * it. {@code InputService} drops a modifier key before it matches anything, on the
 * grounds that a modifier only qualifies a bind &mdash; right for a module, wrong
 * for these, so through the event bus neither of the binds below could ever fire.
 *
 * <p>So {@code InputBridge} runs action binds itself and posts nothing when one
 * fires. That is also what makes each of these a toggle rather than a one-way
 * door: a screen of ours swallows the keyboard while it is up, and the adapter
 * offers the keystroke to a focused text field first, then to the actions, then to
 * the screen. Typing an R into a search box does not close the GUI, and the bind
 * that opened it still closes it.
 *
 * <h2>Why each toggles rather than opens</h2>
 * The GUI and the editor are drawn into whatever screen the game is showing, and
 * neither is drawn unless one is. {@code CoreScreen} is that screen, and its
 * toggle helpers put it up before asking Core to open, then leave taking it down
 * to the frame loop &mdash; the same path Escape uses.
 */
public final class Binds {

    /** The convention, and free in vanilla: sneak is Left Shift. */
    private static final Key CLICK_GUI = Key.RIGHT_SHIFT;

    /** Its neighbour, and likewise unbound by vanilla. */
    private static final Key HUD_EDITOR = Key.RIGHT_CONTROL;

    private Binds() {
    }

    /**
     * Registers every action.
     *
     * <p>Before {@code Core.start()}, so the saved binds are loaded over these
     * defaults rather than after them.
     */
    public static void registerAll(Core core) {
        var input = core.getInputService();

        input.register("Click GUI", Bind.of(CLICK_GUI), CoreScreen::toggleClickGui);
        input.register("HUD Editor", Bind.of(HUD_EDITOR), CoreScreen::toggleHudEditor);

        // Unbound by default: a screen most people open from the GUI, not a key.
        input.register("Profiles", Bind.NONE, () -> {
            CoreScreen.open();
            Core.gui().open(new ProfilesScreen());
        });
    }
}
