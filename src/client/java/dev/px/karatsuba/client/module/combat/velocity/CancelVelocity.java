package dev.px.karatsuba.client.module.combat.velocity;

import dev.px.core.event.Subscribe;
import dev.px.karatsuba.adapter.event.game.player.KnockbackEvent;
import dev.px.karatsuba.client.module.combat.VelocityModule;
import dev.px.karatsuba.client.module.mode.SubMode;

/**
 * Ignores the push entirely.
 *
 * <p>The shortest mode there is, and the most conspicuous: a player who never
 * moves when hit is the first thing anyone watching notices, and the first thing
 * a server checks for. It is here because it is the honest baseline the other
 * three are measured against.
 *
 * <p>No settings, so {@code VelocityModule} declares no panel for it.
 */
public final class CancelVelocity extends SubMode<VelocityModule.Mode> {

    public CancelVelocity() {
        super(VelocityModule.Mode.CANCEL);
    }

    @Subscribe
    private void onKnockback(KnockbackEvent event) {
        event.cancel();
    }
}
