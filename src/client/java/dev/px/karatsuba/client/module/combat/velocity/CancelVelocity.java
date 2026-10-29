package dev.px.karatsuba.client.module.combat.velocity;

import dev.px.core.event.Subscribe;
import dev.px.core.setting.impl.BooleanSetting;
import dev.px.core.setting.impl.RangeSetting;
import dev.px.karatsuba.adapter.event.game.player.KnockbackEvent;
import dev.px.karatsuba.client.module.combat.VelocityModule;
import dev.px.karatsuba.client.module.mode.SubMode;

/**
 * Ignores the push entirely, or with Scaled on, takes a random share of it.
 *
 * <p>Ignoring it all is the most conspicuous thing there is: a player who never
 * moves when hit is the first thing anyone watching notices, and the first thing
 * a server checks for. Scaled takes a share picked afresh each push from the
 * Horizontal and Vertical ranges, so no two hits move the player by the same
 * fraction. On Grim both are set back; see {@code anticheat/Grim.md}.
 *
 * <p>Hits always; explosions (crystals, anchors, TNT) unless switched off.
 */
public final class CancelVelocity extends SubMode<VelocityModule.Mode> {

    private final BooleanSetting explosions = bool("Explosions", true)
            .describe("Also act on explosion knockback");

    private final BooleanSetting scaled = bool("Scaled", false)
            .describe("Take a random share of the push instead of none");

    private final RangeSetting horizontal = range("Horizontal", 40d, 60d, 0d, 100d)
            .step(1d)
            .describe("Percent of the sideways push to take, picked each hit")
            .visibleWhen(scaled::get);

    private final RangeSetting vertical = range("Vertical", 80d, 100d, 0d, 100d)
            .step(1d)
            .describe("Percent of the upward push to take, picked each hit")
            .visibleWhen(scaled::get);

    public CancelVelocity() {
        super(VelocityModule.Mode.CANCEL);
    }

    @Subscribe
    private void onKnockback(KnockbackEvent event) {
        if (event.isExplosion() && !explosions.get()) {
            return;
        }
        if (scaled.get()) {
            event.scale(horizontal.randomValue() / 100d, vertical.randomValue() / 100d);
        } else {
            event.cancel();
        }
    }
}
