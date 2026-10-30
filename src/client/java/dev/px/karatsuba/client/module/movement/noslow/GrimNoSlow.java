package dev.px.karatsuba.client.module.movement.noslow;

import dev.px.core.event.Subscribe;
import dev.px.karatsuba.adapter.event.game.player.ServerTeleportEvent;
import dev.px.karatsuba.adapter.event.game.player.SlowdownEvent;
import dev.px.karatsuba.adapter.game.player.Players;
import dev.px.karatsuba.client.module.mode.SubMode;
import dev.px.karatsuba.client.module.movement.NoSlowModule;

/**
 * No Slow that Grim's NoSlow check never flags: full speed every other tick.
 *
 * <p>Grim's simulation tries every move both slowed and unslowed, so only the
 * NoSlow check can see this. It flags the second checked tick in a row that
 * Grim believes you slowed but you moved faster, and a slowed tick clears it
 * ({@code Grim.md}, "The NoSlow check itself"). So: fast, slowed, fast, slowed.
 * About 0.6 of walking speed, three times vanilla's. No packets are sent.
 *
 * <p>Decided at {@link SlowdownEvent}, which only fires on a tick that is
 * slowed, with this tick's state. Whenever it can't be sure what Grim saw last,
 * it starts slowed: the first tick of every use (Grim's latch outlives a use)
 * and two ticks after a teleport (ticks Grim doesn't check leave the latch as
 * it was).
 */
public final class GrimNoSlow extends SubMode<NoSlowModule.Mode> {

    /** A sentinel no real use-tick count is one less than. */
    private static final int NO_USE = Integer.MIN_VALUE;

    public GrimNoSlow() {
        super(NoSlowModule.Mode.GRIM);
    }

    /** Slowed ticks Grim must see before the next fast one. */
    private int slowedOwed = 1;

    /** The item use tick count at the last slowdown, to tell one use going on from a new one. */
    private int lastUseTicks = NO_USE;

    @Subscribe
    private void onSlowdown(SlowdownEvent event) {
        // Not one tick on from the last slowdown: a new use, or one this mode
        // didn't see all of. Grim may still hold a fast tick from before.
        int useTicks = Players.itemUseTicks();
        if (useTicks != lastUseTicks + 1) {
            slowedOwed = Math.max(slowedOwed, 1);
        }
        lastUseTicks = useTicks;

        if (slowedOwed > 0 || !event.isMoving()) {
            // Slowed as vanilla. With no input that is also the unslowed move,
            // so Grim rewards it either way.
            slowedOwed = Math.max(slowedOwed - 1, 0);
            return;
        }
        event.cancel();
        slowedOwed = 1;
    }

    @Subscribe
    private void onTeleport(ServerTeleportEvent event) {
        slowedOwed = 2;
    }

    @Override
    protected void onSelected() {
        slowedOwed = 1;
        lastUseTicks = NO_USE;
    }
}
