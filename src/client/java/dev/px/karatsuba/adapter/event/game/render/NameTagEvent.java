package dev.px.karatsuba.adapter.event.game.render;

import dev.px.core.event.CancellableEvent;
import dev.px.core.math.Vec3;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * The game is about to float a name over an entity.
 *
 * <p>Cancelling hides the vanilla label &mdash; the white name and, for players,
 * the scoreboard line under it &mdash; for this frame. That is what a module
 * drawing its own name tags does, so the two do not stack on top of each other.
 *
 * <p>Only posted for entities the game would actually label: players in range,
 * named mobs being looked at, and so on. An entity with no label is never asked
 * about, so this costs nothing for the crowd that has none.
 */
@Getter
@RequiredArgsConstructor
public final class NameTagEvent extends CancellableEvent {

    /** The entity, as a {@code GameEntity} carries it. */
    private final int entityId;

    private final boolean player;

    /** Where it stands, so a handler can decide by distance. */
    private final Vec3 position;
}
