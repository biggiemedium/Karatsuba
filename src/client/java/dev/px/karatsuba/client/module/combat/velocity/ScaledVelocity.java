package dev.px.karatsuba.client.module.combat.velocity;

import dev.px.core.event.Subscribe;
import dev.px.core.setting.impl.NumberSetting;
import dev.px.karatsuba.adapter.event.game.player.KnockbackEvent;
import dev.px.karatsuba.client.module.combat.VelocityModule;
import dev.px.karatsuba.client.module.mode.SubMode;

/**
 * Keeps a fraction of the push.
 *
 * <p>Horizontal and vertical separately, because they are felt differently: the
 * horizontal component is what moves you out of a fight and the vertical is what
 * breaks a jump. Taking none of one and all of the other is a common and
 * sensible setting, and a single "strength" slider cannot express it.
 *
 * <p>Percentages rather than multipliers, because that is how a player thinks
 * about it and how every other client states it. The conversion is one division
 * and it happens here rather than in the setting.
 */
public final class ScaledVelocity extends SubMode<VelocityModule.Mode> {

    private static final double PERCENT = 100d;

    private final NumberSetting<Integer> horizontal = integer("Horizontal", 0, 0, 100)
            .describe("Percent of the sideways push to keep");

    private final NumberSetting<Integer> vertical = integer("Vertical", 100, 0, 100)
            .describe("Percent of the upward push to keep");

    public ScaledVelocity() {
        super(VelocityModule.Mode.SCALED);
    }

    @Subscribe
    private void onKnockback(KnockbackEvent event) {
        event.scale(horizontal.getInt() / PERCENT, vertical.getInt() / PERCENT);
    }
}
