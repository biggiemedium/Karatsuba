package dev.px.karatsuba.client.module.combat.velocity;

import dev.px.core.Core;
import dev.px.core.event.Stage;
import dev.px.core.event.Subscribe;
import dev.px.core.event.impl.TickEvent;
import dev.px.core.setting.impl.NumberSetting;
import dev.px.karatsuba.adapter.event.game.player.KnockbackEvent;
import dev.px.karatsuba.adapter.game.player.Inputs;
import dev.px.karatsuba.adapter.game.player.Players;
import dev.px.karatsuba.client.module.combat.VelocityModule;
import dev.px.karatsuba.client.module.mode.SubMode;

/**
 * Jumps when hit, which bleeds the push off through the game's own friction.
 *
 * <p>What a good player does by hand: leaving the ground on the tick the
 * knockback lands means the horizontal component is spent against air drag
 * rather than carried, and landing kills the rest. Nothing is cancelled and
 * nothing is scaled: the push is taken in full, so an anticheat that checks the
 * knockback was taken (Grim does) finds that it was.
 *
 * <h2>Why it presses jump</h2>
 * Since 1.21.2 the client tells the server which keys it holds, and Grim
 * simulates movement from those keys. A jump made by setting the vertical
 * velocity is a jump the reported keys do not explain. So this holds the real
 * jump key for one tick: the game jumps exactly as it would for the player, and
 * reports it.
 *
 * <p>The knockback arrives between ticks, on the game thread, before the tick it
 * applies to; the jump is held at the start of that tick, when the game reads
 * the keyboard, and let go at its end. The letting go is a plain subscription
 * that runs whether or not this mode is still selected, so switching the module
 * off mid-tick can never leave the key held.
 */
public final class JumpResetVelocity extends SubMode<VelocityModule.Mode> {

    private final NumberSetting<Integer> chance = integer("Chance", 100, 10, 100)
            .describe("Percent of hits to jump on; below 100 looks less mechanical");

    /** Jump this coming tick. */
    private boolean owed;

    /** The jump key is held by us this tick, and must be let go at its end. */
    private boolean holding;

    public JumpResetVelocity() {
        super(VelocityModule.Mode.JUMP_RESET);
        Core.bus().on(TickEvent.class, event -> {
            if (event.getStage() == Stage.POST) {
                letGo();
            }
        });
    }

    @Subscribe
    private void onKnockback(KnockbackEvent event) {
        // Only from the ground. Jumping mid-air does nothing except look wrong,
        // and this mode's whole argument is that it does not look wrong.
        if (Players.onGround() && Math.random() * 100d < chance.getInt()) {
            owed = true;
        }
    }

    @Subscribe(stage = Stage.PRE)
    private void onTickStart(TickEvent event) {
        if (!owed) {
            return;
        }
        owed = false;
        if (!Players.onGround() || Inputs.pressed(Inputs.Control.JUMP)) {
            // Already airborne, or the player is jumping anyway: nothing to add.
            return;
        }
        Inputs.hold(Inputs.Control.JUMP);
        holding = true;
    }

    @Override
    protected void onSelected() {
        owed = false;
        letGo();
    }

    private void letGo() {
        if (holding) {
            Inputs.release(Inputs.Control.JUMP);
            holding = false;
        }
    }
}
