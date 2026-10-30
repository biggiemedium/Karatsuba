package dev.px.karatsuba.client.module.movement.noslow;

import dev.px.core.event.Subscribe;
import dev.px.karatsuba.adapter.event.game.player.SlowdownEvent;
import dev.px.karatsuba.client.module.mode.SubMode;
import dev.px.karatsuba.client.module.movement.NoSlowModule;

/**
 * Plain No Slow: using an item no longer slows you.
 *
 * <p>Cancels the game's 0.2 input scale ({@link SlowdownEvent}), so you walk at
 * full speed while eating, drinking, blocking or drawing a bow. Sprinting still
 * stops as vanilla stops it.
 *
 * <p>No attempt at an anticheat. Grim marks you slowed on {@code USE_ITEM}, and
 * its NoSlow check flags, with a setback, the second tick in a row you move
 * faster than that ({@code Grim.md}, "Item use").
 */
public final class VanillaNoSlow extends SubMode<NoSlowModule.Mode> {

    public VanillaNoSlow() {
        super(NoSlowModule.Mode.VANILLA);
    }

    @Subscribe
    private void onSlowdown(SlowdownEvent event) {
        event.cancel();
    }
}
