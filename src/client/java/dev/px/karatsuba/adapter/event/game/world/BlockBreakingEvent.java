package dev.px.karatsuba.adapter.event.game.world;

import dev.px.core.event.Event;
import dev.px.core.math.Vec3i;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * Someone's progress breaking a block: the cracks the game draws on it.
 *
 * <p>Other players' progress comes from the server, which sends a new stage each
 * time a miner's crack grows (ten stages, 0 to 9), so it arrives in steps rather
 * than smoothly. The player's own progress comes from the client's own mining,
 * every tick. Posted on the game thread.
 *
 * <p>Each breaker has one block at a time: a new position for the same entity
 * means it moved on. A crack outside 0 to 9 means it stopped (gave up, or the
 * block broke).
 */
@Getter
@RequiredArgsConstructor
public final class BlockBreakingEvent extends Event {

    /** The entity doing the breaking: a player, almost always. */
    private final int breakerId;

    private final Vec3i position;

    /** 0 to 9 while breaking; anything else when it stopped. */
    private final int crack;

    /** @return whether the breaker stopped rather than progressed */
    public boolean isStopped() {
        return crack < 0 || crack > 9;
    }
}
