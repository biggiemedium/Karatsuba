package dev.px.karatsuba.client.module.combat.velocity;

import dev.px.core.event.Stage;
import dev.px.core.event.Subscribe;
import dev.px.core.event.impl.TickEvent;
import dev.px.core.setting.impl.NumberSetting;
import dev.px.karatsuba.adapter.event.game.player.KnockbackEvent;
import dev.px.karatsuba.client.module.combat.VelocityModule;
import dev.px.karatsuba.client.module.mode.SubMode;

/**
 * Jumps when hit, with the real jump key.
 *
 * <p>What a good player does by hand: leaving the ground on the tick the
 * knockback lands. Nothing is cancelled and nothing is scaled: the push is taken
 * in full, so an anticheat that checks the knockback was taken (Grim does) finds
 * that it was. The jump only raises the vertical to the jump's own, so the
 * vertical is not reduced; see {@code anticheat/Grim.md} for what is.
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
 * the keyboard, and let go at its end ({@link JumpReset}).
 *
 * <p>Hits only: a jump does nothing against an explosion's push.
 */
public final class JumpResetVelocity extends SubMode<VelocityModule.Mode> {

    private final NumberSetting<Integer> chance = integer("Chance", 100, 10, 100)
            .describe("Percent of hits to jump on; below 100 looks less mechanical");

    private final JumpReset jumpReset = new JumpReset();

    public JumpResetVelocity() {
        super(VelocityModule.Mode.JUMP_RESET);
    }

    @Subscribe
    private void onKnockback(KnockbackEvent event) {
        jumpReset.onKnockback(event, chance.getInt());
    }

    @Subscribe(stage = Stage.PRE)
    private void onTickStart(TickEvent event) {
        jumpReset.onTickStart();
    }

    @Override
    protected void onSelected() {
        jumpReset.reset();
    }
}
