package dev.px.karatsuba.adapter.event.game.combat;

import dev.px.core.event.Event;
import dev.px.core.math.Vec3;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * A totem of undying saved someone: the server's status 35 for an entity.
 *
 * <p>Posted on the game thread as the status is handled, after the combat kit's
 * damage monitor has been told (a pop proves an explosion did at least everything
 * the target had). For pop counters and kill logic.
 */
@Getter
@RequiredArgsConstructor
public final class TotemPopEvent extends Event {

    /** The entity's id. */
    private final int id;

    /** Its name, as the game displays it. */
    private final String name;

    /** Where it was. */
    private final Vec3 position;

    /** Whether it was the local player. */
    private final boolean self;
}
