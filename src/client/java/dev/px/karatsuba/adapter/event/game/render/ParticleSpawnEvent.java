package dev.px.karatsuba.adapter.event.game.render;

import dev.px.core.event.CancellableEvent;
import dev.px.core.math.Vec3;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * A particle about to be spawned, for the kinds a module has a reason to stop.
 *
 * <p>Not posted for every particle. The game spawns thousands a second in a busy
 * scene, and an event per torch flicker would cost more than the feature is
 * worth, so only the {@link Kind kinds} listed here are reported. Adding one is a
 * constant here and a line in the particle mixin.
 *
 * <p>Cancelling stops the particle being created at all, rather than hiding it,
 * so a suppressed explosion costs nothing to render.
 */
@Getter
@RequiredArgsConstructor
public final class ParticleSpawnEvent extends CancellableEvent {

    private final Kind kind;

    /** Where it would have appeared. */
    private final Vec3 position;

    /** @return whether this is the given kind */
    public boolean is(Kind candidate) {
        return kind == candidate;
    }

    public enum Kind {

        /** The smoke balls of an explosion, small and large. */
        EXPLOSION,

        /** A firework's trail, and the burst when it goes off. */
        FIREWORK
    }
}
