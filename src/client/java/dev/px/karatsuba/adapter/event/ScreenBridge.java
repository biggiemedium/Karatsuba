package dev.px.karatsuba.adapter.event;

import dev.px.core.event.impl.ScreenEvent;

/**
 * Screen changes, from the game's one setter to Core's {@code ScreenEvent}.
 *
 * <p>The last Core event nothing was posting. {@code ScreenEvent} exists so a HUD
 * can hide itself behind the inventory, a module can react to the death screen,
 * and a GUI can drop focus when the game takes the screen away; without a
 * publisher, every one of those had to be rewritten as a per-frame check of
 * {@code currentScreen}, which is how a client ends up polling for something the
 * game already announces.
 *
 * <h2>Why a mixin rather than Fabric's ScreenEvents</h2>
 * {@code ScreenEvents.BEFORE_INIT} only fires for a screen that is being
 * initialised, so it never sees {@code setScreen(null)} &mdash; the single most
 * useful case, a screen closing back to the world. It also fires after the game
 * has already committed to the change, so it cannot be refused, and
 * {@code ScreenEvent} is cancellable. {@code MinecraftClient.setScreen} is the
 * one place both halves are still decidable, and it is the only place the game
 * changes screens.
 *
 * <h2>Cancellation</h2>
 * A screen change is one action, so either event vetoing it leaves the current
 * screen exactly where it was. Refusing a close and refusing an open are the
 * same outcome, which is why they are posted together and answered once.
 *
 * <p>Cancel carefully. Vetoing {@code setScreen(null)} keeps a screen up that the
 * game has decided is finished, and vetoing the disconnect screen keeps the
 * player in a world that is no longer there.
 */
public final class ScreenBridge {

    private ScreenBridge() {
    }

    /**
     * @param outgoing the screen being replaced, or null if none was showing
     * @param incoming the screen being opened, or null if the game is returning
     *                 to the world
     * @return whether the change was refused, in which case the game must not
     *         apply it
     */
    public static boolean onScreenChange(Object outgoing, Object incoming) {
        if (!Events.isArmed() || outgoing == incoming) {
            return false;
        }
        // Two posts rather than one so a subscriber can match on the screen it
        // cares about without also having to work out which side of the swap it
        // is on. Both run even if the first cancels: a listener that released
        // state on close must not be skipped because something else vetoed.
        boolean cancelled = false;
        if (outgoing != null) {
            cancelled = Events.cancelled(new ScreenEvent(outgoing, false));
        }
        if (incoming != null) {
            cancelled |= Events.cancelled(new ScreenEvent(incoming, true));
        }
        return cancelled;
    }
}
