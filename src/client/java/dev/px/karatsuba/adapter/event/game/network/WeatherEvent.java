package dev.px.karatsuba.adapter.event.game.network;

import dev.px.core.event.CancellableEvent;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * The server changing the weather.
 *
 * <p>The same pattern as {@link ScreenCloseEvent}. Weather travels inside a
 * general "game state changed" packet that also carries game mode changes, the
 * end credits and the elder guardian's jump scare, so a module matching on
 * {@link PacketEvent} would have to know which reason codes are weather. This is
 * that packet with everything that is not weather filtered out.
 *
 * <p>Rain does not switch on all at once: a start is followed by a run of
 * {@link Change#RAIN_LEVEL} updates fading it in, and thunder fades the same way
 * on top. Cancelling skips one step; a module holding the weather fixed cancels
 * them all, and should remember the latest values to hand back when it stops.
 *
 * <p>Posted on the game thread, after the game has marshalled the packet there.
 */
@Getter
@RequiredArgsConstructor
public final class WeatherEvent extends CancellableEvent {

    private final Change change;

    /** The new level for the two {@code _LEVEL} changes, 0 to 1. Meaningless for the others. */
    private final float value;

    /** @return whether this is the given change */
    public boolean is(Change candidate) {
        return change == candidate;
    }

    public enum Change {

        /** It has started raining (or snowing, by biome). */
        RAIN_STARTED,

        /** It has stopped. */
        RAIN_STOPPED,

        /** How heavy the rain is, as it fades in and out. */
        RAIN_LEVEL,

        /** How strong the thunderstorm is, on top of the rain. */
        THUNDER_LEVEL
    }
}
