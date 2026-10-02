package dev.px.karatsuba.client.module.combat.velocity;

import dev.px.core.event.Subscribe;
import dev.px.core.setting.impl.BooleanSetting;
import dev.px.core.setting.impl.NumberSetting;
import dev.px.karatsuba.adapter.event.game.player.KnockbackEvent;
import dev.px.karatsuba.adapter.event.game.network.MovementPacketEvent;
import dev.px.karatsuba.client.module.combat.VelocityModule;
import dev.px.karatsuba.client.module.mode.SubMode;

/**
 * Stops telling the server where the player is for a moment after a hit.
 *
 * <p>The mode that works at a different layer from the other three, and the
 * reason {@code ModalModule} exists rather than a switch. It touches no
 * knockback value at all: the push is applied in full, and what changes is that
 * the server spends the next few ticks with no idea where the player ended up.
 * Movement packets resume afterwards and the position jumps to wherever the
 * player actually is.
 *
 * <p>It needs two events to do that &mdash; one to notice the hit and one to
 * hold the packets &mdash; and neither of them is the event the other modes use.
 * Written into a shared class those would be two more branches in two more
 * handlers.
 *
 * <h2>State that expires rather than resets</h2>
 * The hold is a deadline, not a countdown. That matters because a mode stops
 * receiving events the instant it is deselected: a tick counter would freeze
 * part-way and still be non-zero when the mode came back, so the first packets
 * after re-selecting would be swallowed for no reason. A timestamp in the past is
 * simply in the past, and there is nothing to clear.
 *
 * <h2>Why it is bounded</h2>
 * A client that stops reporting its position indefinitely is a client that has
 * stopped playing, and every server notices eventually. The cap is deliberately
 * low, and the setting's ceiling is lower than the point where it stops working.
 */
public final class BufferVelocity extends SubMode<VelocityModule.Mode> {

    private static final long MILLIS_PER_TICK = 50L;

    private final NumberSetting<Integer> ticks = integer("Hold Ticks", 4, 1, 10)
            .describe("How long to stop reporting position after a hit");

    private final BooleanSetting keepGround = bool("Keep Ground", true)
            .describe("Still send the on-ground flag, which is the part servers check hardest");

    /**
     * When the hold ends, as wall-clock time. Zero means not holding.
     *
     * <p>A deadline rather than a counter, so the state heals on its own; see the
     * note on this class.
     */
    private long holdUntil;

    public BufferVelocity() {
        super(VelocityModule.Mode.BUFFER);
    }

    /**
     * Starts the hold. The push itself is left alone.
     *
     * <p>Not cancelled and not scaled: this mode's whole effect comes from the
     * packets that follow, and quietly also reducing the knockback would make two
     * mechanisms fight for the credit.
     */
    @Subscribe
    private void onKnockback(KnockbackEvent event) {
        holdUntil = System.currentTimeMillis() + ticks.getInt() * MILLIS_PER_TICK;
    }

    @Subscribe
    private void onMovementPacket(MovementPacketEvent event) {
        if (System.currentTimeMillis() >= holdUntil) {
            return;
        }
        if (keepGround.isOn() && !event.hasPosition()) {
            // A packet carrying only the on-ground flag is cheap to let through
            // and is the one the server misses most.
            return;
        }
        event.cancel();
    }

    /** A fresh selection starts with nothing held back. */
    @Override
    protected void onSelected() {
        holdUntil = 0L;
    }
}
