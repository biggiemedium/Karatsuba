package dev.px.karatsuba.adapter.event.game.world;

import dev.px.core.event.Event;
import dev.px.core.math.Vec3i;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * A block in a loaded chunk became a different block: placed, broken, or changed
 * by the server.
 *
 * <p>Posted on the game thread, after the change, for the client world only.
 * Only for a change of block, not of state: a door opening or a crop growing
 * is not one, so a search keyed on block ids hears nothing it does not need.
 * Chunks arriving are {@link ChunkEvent}s, not a flood of these.
 */
@Getter
@RequiredArgsConstructor
public final class BlockChangeEvent extends Event {

    private final Vec3i position;

    /** The block id it was, such as {@code minecraft:stone}. */
    private final String previous;

    /** The block id it is now. */
    private final String current;
}
