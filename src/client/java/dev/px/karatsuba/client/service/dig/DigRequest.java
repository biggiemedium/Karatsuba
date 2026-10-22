package dev.px.karatsuba.client.service.dig;

import dev.px.core.math.Vec3i;
import dev.px.karatsuba.adapter.game.item.Inventories;
import dev.px.karatsuba.client.service.claim.ClaimPriority;

/**
 * One block a caller wants dug this tick, and with what.
 *
 * <pre>{@code
 * Services.digging().dig(this, DigRequest.of(block)
 *         .tool(Inventories.bestToolSlotFor(Blocks.idAt(block)))
 *         .priority(ClaimPriority.NORMAL));
 * }</pre>
 *
 * <p>Asked again every tick for as long as it should go on, like every claim.
 */
public final class DigRequest {

    private final Vec3i block;
    private int tool = Inventories.NOT_FOUND;
    private int priority = ClaimPriority.NORMAL;

    private DigRequest(Vec3i block) {
        this.block = block;
    }

    public static DigRequest of(Vec3i block) {
        return new DigRequest(block);
    }

    /**
     * Hold this hotbar slot (0 to 8) for the dig; the dig waits until it is in hand.
     * {@link Inventories#NOT_FOUND}, the default, leaves the hotbar alone.
     */
    public DigRequest tool(int hotbarSlot) {
        this.tool = hotbarSlot;
        return this;
    }

    /** Against other callers for the one dig, and for the head and hotbar it takes. */
    public DigRequest priority(int priority) {
        this.priority = priority;
        return this;
    }

    public Vec3i getBlock() {
        return block;
    }

    public int getTool() {
        return tool;
    }

    public int getPriority() {
        return priority;
    }
}
