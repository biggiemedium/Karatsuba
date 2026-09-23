package dev.px.karatsuba.client.module.combat.velocity;

import dev.px.core.event.Stage;
import dev.px.core.event.Subscribe;
import dev.px.core.event.impl.TickEvent;
import dev.px.core.setting.impl.NumberSetting;
import dev.px.karatsuba.adapter.event.game.KnockbackEvent;
import dev.px.karatsuba.adapter.game.player.Players;
import dev.px.karatsuba.client.module.combat.VelocityModule;
import dev.px.karatsuba.client.module.mode.SubMode;
import dev.px.karatsuba.client.service.Services;
import dev.px.karatsuba.client.service.claim.ClaimPriority;

/**
 * Jumps when hit, which bleeds the push off through the game's own friction.
 *
 * <p>What a good player does by hand: leaving the ground on the tick the
 * knockback lands means the horizontal component is spent against air drag
 * rather than carried, and landing kills the rest. Nothing is cancelled and
 * nothing is scaled, so there is no discrepancy for anything to find &mdash; the
 * server sees a player who jumped.
 *
 * <p>The third layer this module works at. Cancel and Scaled change the push,
 * Buffer changes what is reported, and this changes what the player does about
 * it &mdash; through {@code MotionService}, so a movement module that also wants
 * the vertical axis can outrank it rather than silently losing.
 *
 * <h2>Why it claims rather than pressing jump</h2>
 * Holding the jump control would work and would fight anything else driving the
 * controls, with the winner decided by whichever released last. A vertical
 * motion claim is arbitrated, expires on its own, and says in one line what it
 * wants.
 */
public final class JumpResetVelocity extends SubMode<VelocityModule.Mode> {

    /** Vanilla's jump velocity, in blocks per tick. */
    private static final double JUMP_VELOCITY = 0.42d;

    private final NumberSetting<Integer> chance = integer("Chance", 100, 10, 100)
            .describe("Percent of hits to jump on; below 100 looks less mechanical");

    private final NumberSetting<Double> strength = decimal("Strength", 1d, 0.5d, 1d)
            .step(0.05d)
            .describe("Fraction of a full jump, for a smaller hop");

    /**
     * Whether a jump is owed.
     *
     * <p>Set when the hit arrives and spent on the next tick, because the
     * knockback lands part-way through the tick and the velocity claim has to be
     * filed before the movement code reads it. Deliberately a single boolean: a
     * second hit before the first jump has been spent should not queue two.
     */
    private boolean owed;

    public JumpResetVelocity() {
        super(VelocityModule.Mode.JUMP_RESET);
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
    private void onTick(TickEvent event) {
        if (!owed) {
            return;
        }
        owed = false;

        if (!Players.onGround()) {
            // Already airborne by the time the tick came round; the hit did the
            // leaving for us and a claim now would be a second jump.
            return;
        }
        Services.motion().setVertical(this, JUMP_VELOCITY * strength.getDouble(), ClaimPriority.HIGH);
    }

    @Override
    protected void onSelected() {
        owed = false;
    }
}
