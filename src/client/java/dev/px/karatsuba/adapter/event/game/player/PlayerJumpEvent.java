package dev.px.karatsuba.adapter.event.game.player;

import dev.px.core.event.CancellableEvent;
import dev.px.core.math.Vec3;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * The player is about to jump.
 *
 * <p>Posted from the jump itself rather than from the key, so it fires for every
 * jump the game performs and not only the ones a player asked for. Cancelling
 * suppresses it outright, which is what a no-jump or a freecam wants; changing
 * the height is done by editing the velocity on the following
 * {@link PlayerMoveEvent}.
 *
 * <p>Posted on the game thread, and only for the local player &mdash; the same
 * call site runs for every mob in the world, and the adapter filters.
 */
@Getter
@RequiredArgsConstructor
public final class PlayerJumpEvent extends CancellableEvent {

    /** Feet position at the moment of the jump. */
    private final Vec3 position;

    /** Whether the player was sprinting, which is what decides the forward boost. */
    private final boolean sprinting;
}
