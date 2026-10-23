package dev.px.karatsuba.adapter.event.game.player;

import dev.px.core.event.CancellableEvent;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * The mouse is about to turn the player's head.
 *
 * <p>In degrees, already scaled the way the game scales the cursor (0.15&deg; a
 * unit), so a handler that takes the turn for itself applies the same numbers
 * the game would have. Cancelling leaves the player's rotation alone: nothing
 * changes, and nothing new is sent to the server. That is what a freecam wants,
 * turning its own camera instead.
 *
 * <p>Posted on the game thread, and only for the local player.
 */
@Getter
@RequiredArgsConstructor
public final class LookEvent extends CancellableEvent {

    /** The turn left or right, in degrees; positive turns right. */
    private final float deltaYaw;

    /** The turn up or down, in degrees; positive looks down. */
    private final float deltaPitch;
}
