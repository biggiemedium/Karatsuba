package dev.px.karatsuba.adapter.event.game.network;

import dev.px.core.event.CancellableEvent;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * The server correcting the client's clock.
 *
 * <p>Sent about once a second. Between these the client advances its own time of
 * day, so this is a correction rather than the only source of time; cancelling
 * it leaves the client's clock wherever it was last put, which is how a module
 * holds the sky at a fixed time without it snapping back every second.
 *
 * <p>Posted on the game thread, after the game has marshalled the packet there.
 * A module that suppresses these should remember {@link #getTimeOfDay()} so it
 * can hand the real time back when it stops.
 */
@Getter
@RequiredArgsConstructor
public final class WorldTimeEvent extends CancellableEvent {

    /** Ticks since the world was created. Never goes backwards. */
    private final long time;

    /**
     * The time of day, in ticks. 0 is sunrise, 6000 noon, 12000 sunset, 18000
     * midnight; it keeps counting past 24000 rather than wrapping.
     */
    private final long timeOfDay;
}
