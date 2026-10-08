package dev.px.karatsuba.adapter.event.game.world;

import dev.px.core.event.Event;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * A chunk of the client world arrived or went away.
 *
 * <p>Posted on the game thread. A loaded chunk is complete: every block in it can
 * be read straight away, which is when a block search scans it. An unloaded one
 * is gone, and anything remembered about it should go too.
 */
@Getter
@RequiredArgsConstructor
public final class ChunkEvent extends Event {

    /** Chunk coordinates: block x and z divided by 16. */
    private final int chunkX;
    private final int chunkZ;

    /** True for a chunk that arrived, false for one that went away. */
    private final boolean loaded;
}
