package dev.px.karatsuba.adapter.event.game.network;

import dev.px.core.event.Event;
import dev.px.core.math.Vec3i;
import dev.px.karatsuba.adapter.world.PlacementResult;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * A block the client placed ahead of the server has been settled: confirmed,
 * rejected, or given up on.
 *
 * <p>The named form of the server's sequence acknowledgement
 * ({@code PlayerActionResponseS2CPacket}), which says only "everything up to
 * number N is answered". This says which cell, and whether the block is there.
 * Posted at the start of the tick after the answer was applied, on the game
 * thread, for every placement {@code Interactions.click} made, whoever made it.
 */
@Getter
@RequiredArgsConstructor
public final class PlacementResultEvent extends Event {

    /** The cell the block was predicted into. */
    private final Vec3i cell;

    private final PlacementResult result;
}
