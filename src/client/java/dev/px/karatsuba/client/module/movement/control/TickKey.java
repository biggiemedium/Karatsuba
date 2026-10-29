package dev.px.karatsuba.client.module.movement.control;

import dev.px.core.Core;
import dev.px.core.event.Stage;
import dev.px.core.event.impl.TickEvent;
import dev.px.karatsuba.adapter.game.player.Inputs;

/**
 * Holds a key down for one tick, the way a player tapping it would.
 *
 * <p>The movement modules act by pressing real keys &mdash; sneak to stop at a
 * ledge, jump to step up or leap a gap &mdash; because the server simulates
 * movement from the keys the client reports, and a key press is something it can
 * explain. Pressed at the start of a tick, the key is read with the keyboard that
 * tick and let go at its end.
 *
 * <p>The letting go is a plain bus subscription that runs whatever state the
 * module is in, so switching a module off mid-tick can never leave a key held. A
 * key the player is already holding is left alone.
 */
public final class TickKey {

    private final Inputs.Control control;
    private boolean held;

    public TickKey(Inputs.Control control) {
        this.control = control;
        Core.bus().on(TickEvent.class, event -> {
            if (event.getStage() == Stage.POST) {
                release();
            }
        });
    }

    /** Holds the key for this tick. Call at the start of the tick, before the keyboard is read. */
    public void press() {
        if (held || Inputs.pressed(control)) {
            return;
        }
        Inputs.hold(control);
        held = true;
    }

    /** @return whether this holds the key this tick */
    public boolean isHeld() {
        return held;
    }

    public void release() {
        if (held) {
            Inputs.release(control);
            held = false;
        }
    }
}
