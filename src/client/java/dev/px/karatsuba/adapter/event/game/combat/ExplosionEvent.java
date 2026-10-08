package dev.px.karatsuba.adapter.event.game.combat;

import dev.px.core.event.Event;
import dev.px.core.math.Vec3;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * An end crystal, a bed or a respawn anchor exploded.
 *
 * <p>Posted on the game thread as the server's explosion packet is handled,
 * before the blocks it destroyed are removed, so the world still looks as it did
 * when the blast went off. Only explosions the client can name are posted: TNT,
 * creepers and the rest are not a profile it keeps.
 */
@Getter
@RequiredArgsConstructor
public final class ExplosionEvent extends Event {

    /** Where it went off from: the crystal's position, or the block's centre. */
    private final Vec3 origin;

    /** "end crystal", "bed" or "respawn anchor". */
    private final String kind;

    /** Its explosion power: 6 for a crystal, 5 for a bed or anchor. */
    private final double power;
}
