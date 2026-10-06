package dev.px.karatsuba.adapter.event.game.player;

import dev.px.core.event.Event;
import dev.px.core.math.Vec3;
import lombok.Getter;

/**
 * The server set the player's position.
 *
 * <p>Posted on the game thread after the game has applied the teleport, with the
 * position the player was at just before and the one they were moved to. Most
 * of these in play are <b>setbacks</b>: an anticheat, or the server's own
 * movement check, refusing where the client said it went and putting it back.
 * That is what a rubberband is, and {@link #distance()} is how far it pulled.
 *
 * <p>Not every teleport is one. The server also places the player this way on
 * joining and on every respawn or dimension change; {@link #isSpawn()} marks
 * those, because only the adapter can tell which packet followed which. A
 * command or plugin teleport looks identical to a setback on the wire, so a
 * reader deciding what counts as a rubberband usually also caps the distance.
 *
 * <p>Informational: the teleport has already happened, and the game has already
 * confirmed it to the server.
 */
@Getter
public final class ServerTeleportEvent extends Event {

    /** Where the player was just before. */
    private final Vec3 from;

    /** Where the server put them. */
    private final Vec3 to;

    /** The server's id for this teleport, which the client's confirmation echoes. */
    private final int teleportId;

    /** Whether this is the placement that follows joining, respawning or changing dimension. */
    private final boolean spawn;

    public ServerTeleportEvent(Vec3 from, Vec3 to, int teleportId, boolean spawn) {
        this.from = from;
        this.to = to;
        this.teleportId = teleportId;
        this.spawn = spawn;
    }

    /** @return how far the player was moved, in blocks */
    public double distance() {
        return from.distanceTo(to);
    }
}
