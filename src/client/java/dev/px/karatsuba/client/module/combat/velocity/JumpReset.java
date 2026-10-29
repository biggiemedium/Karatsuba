package dev.px.karatsuba.client.module.combat.velocity;

import dev.px.karatsuba.adapter.event.game.player.KnockbackEvent;
import dev.px.karatsuba.adapter.game.player.Inputs;
import dev.px.karatsuba.adapter.game.player.Players;
import dev.px.karatsuba.client.module.movement.control.TickKey;

/**
 * Jumps on the tick a hit lands, with the real jump key; see {@link JumpResetVelocity}.
 *
 * <p>Shared by the modes that jump-reset hits, so each one is two calls: the
 * knockback it was handed, and the start of the tick. The key is a
 * {@link TickKey}, so it is let go at the end of the tick whatever happens to
 * the mode in between.
 */
final class JumpReset {

    private final TickKey jump = new TickKey(Inputs.Control.JUMP);

    /** Jump this coming tick. */
    private boolean owed;

    /**
     * Marks a jump for the coming tick, for a hit taken on the ground.
     *
     * @param chance percent of hits to jump on
     */
    void onKnockback(KnockbackEvent event, int chance) {
        // An explosion's push is added to the motion, and a jump only raises the
        // vertical to at least the jump's, so jumping on one changes nothing.
        if (event.isExplosion()) {
            return;
        }
        // Only from the ground. Jumping mid-air does nothing except look wrong,
        // and the whole argument is that it does not look wrong.
        if (Players.onGround() && Math.random() * 100d < chance) {
            owed = true;
        }
    }

    /** At the start of the tick, before the game reads the keyboard. */
    void onTickStart() {
        if (!owed) {
            return;
        }
        owed = false;
        // Already airborne: nothing to add. A player already holding jump is
        // left alone by the key itself.
        if (Players.onGround()) {
            jump.press();
        }
    }

    void reset() {
        owed = false;
        jump.release();
    }
}
