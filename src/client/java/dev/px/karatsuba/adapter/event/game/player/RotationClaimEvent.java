package dev.px.karatsuba.adapter.event.game.player;

import dev.px.core.event.Event;

/**
 * The last moment to claim this tick's rotation.
 *
 * <p>Posted at the head of the game's {@code tickMovement}, just before Core's
 * rotation service resolves the tick's rotation: it is in place for the move and
 * goes out in this tick's movement packet. (If the move is skipped, it is posted
 * just before the packets instead.)
 *
 * <p>The player has <em>not</em> moved yet this tick. The server will measure the
 * next click from where they are after the move, so an aim worked out here is
 * best taken from {@code Players.eyePosition()} plus {@code Players.velocity()}:
 * about where the eyes will be once this tick's move is done.
 *
 * <p>A claim filed here applies this tick; one filed after it waits for the next.
 * Game thread.
 */
public final class RotationClaimEvent extends Event {
}
