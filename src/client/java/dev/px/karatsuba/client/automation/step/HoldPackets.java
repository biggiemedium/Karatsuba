package dev.px.karatsuba.client.automation.step;

import dev.px.core.flow.FlowContext;
import dev.px.core.flow.Status;
import dev.px.core.flow.Step;
import dev.px.core.flow.StopReason;
import dev.px.karatsuba.adapter.network.PacketHold;

/**
 * Holds outgoing packets for as long as the step runs, and lets them all go when
 * it stops: a blink, as a step.
 *
 * <p>The hold is the caller's, set up with the kinds and limit it wants; this
 * only turns it on and off. It is on from start to stop, whatever stops it: an
 * {@code until} coming true, a cancel, a pause (leaving the world). A resume
 * turns it on again.
 *
 * <p>Done when the hold is no longer on: its limit ran out, or something let it
 * go. Uses no controls, so no reflex ever pauses it to take them.
 *
 * <pre>{@code
 * Step blink = HoldPackets.with(hold).until(c -> safe());
 * }</pre>
 */
public final class HoldPackets extends Step {

    private final PacketHold.Hold hold;

    private HoldPackets(PacketHold.Hold hold) {
        super("hold packets");
        this.hold = hold;
    }

    /** @return a step that keeps {@code hold} on while it runs */
    public static HoldPackets with(PacketHold.Hold hold) {
        return new HoldPackets(hold);
    }

    @Override
    protected void start(FlowContext c) {
        hold.start();
    }

    @Override
    protected Status tick(FlowContext c) {
        return hold.isActive() ? Status.RUNNING : Status.DONE;
    }

    @Override
    protected void stop(FlowContext c, StopReason why) {
        hold.release();
    }
}
