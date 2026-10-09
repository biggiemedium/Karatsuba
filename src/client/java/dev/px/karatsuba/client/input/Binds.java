package dev.px.karatsuba.client.input;

import dev.px.core.Core;
import dev.px.core.input.Bind;
import dev.px.core.input.Key;
import dev.px.karatsuba.client.gui.AccountsScreen;
import dev.px.karatsuba.client.gui.ClientSettingsScreen;
import dev.px.karatsuba.client.gui.shell.section.hud.HudSection;
import dev.px.karatsuba.client.gui.shell.section.layout.LayoutSection;
import dev.px.karatsuba.client.gui.ProfilesScreen;
import dev.px.karatsuba.client.screen.Overlays;

/**
 * Keyboard actions that are not modules.
 *
 * <p>Core persists each of these as an ordinary {@code BindSetting}, so they are
 * rebindable from the click GUI and survive a restart like any other setting. The
 * defaults below are only what a fresh install starts with &mdash; a profile that
 * already names a bind wins over them, which is why changing a default does
 * nothing for a config that has one saved.
 *
 * <h2>Right Shift, and why these are not module binds</h2>
 * The genre convention is Right Shift for the click GUI, and Core will not deliver
 * it. {@code InputService} drops a modifier key before it matches anything, on the
 * grounds that a modifier only qualifies a bind &mdash; right for a module, wrong
 * for these. The adapter runs action binds itself for exactly that reason; see
 * {@code adapter.input.ActionBinds}.
 *
 * <h2>Why each toggles rather than opens</h2>
 * An overlay swallows the keyboard while it is up, and the adapter offers a
 * keystroke to the overlays before the actions here. Whatever an overlay does not
 * claim reaches this list, which is what keeps the bind that opened a screen able
 * to close it: typing an R into a search box does not close the GUI, but Right
 * Shift still does.
 */
public final class Binds {

    /** The convention, and free in vanilla: sneak is Left Shift. */
    private static final Key CLICK_GUI = Key.RIGHT_SHIFT;

    /** Its neighbour, and likewise unbound by vanilla. */
    private static final Key HUD_EDITOR = Key.RIGHT_CONTROL;

    /** The HUD elements screen: Insert, unused by vanilla. */
    private static final Key HUD_ELEMENTS = Key.INSERT;

    /** The Client Settings screen: Home, beside Insert, also unused by vanilla. */
    private static final Key CLIENT_SETTINGS = Key.HOME;

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

        input.register("Click GUI", Bind.of(CLICK_GUI), () -> Overlays.gui().toggle());
        // The old window-per-category click GUI, kept reachable while the new one beds in.
        input.register("Legacy Click GUI", Bind.NONE, () -> Overlays.clickGui().toggle());
        input.register("HUD Editor", Bind.of(HUD_EDITOR), () -> Overlays.gui().open(LayoutSection.ID));
        input.register("HUD Elements", Bind.of(HUD_ELEMENTS), () -> Overlays.gui().open(HudSection.ID));
        input.register("Client Settings", Bind.of(CLIENT_SETTINGS), ClientSettingsScreen::toggle);

        // Unbound by default: a screen most people open from the GUI, not a key.
        input.register("Profiles", Bind.NONE, () -> Overlays.clickGui().open(new ProfilesScreen()));

        // Also unbound: switching accounts is rare enough that a key would be one
        // more thing to press by accident mid-game.
        input.register("Accounts", Bind.NONE, () -> Overlays.clickGui().open(new AccountsScreen()));
    }
}
