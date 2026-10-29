package dev.px.karatsuba.adapter.event.game.player;

import dev.px.core.event.CancellableEvent;
import dev.px.core.math.Vec3;
import dev.px.karatsuba.adapter.event.game.network.PacketEvent;
import lombok.Getter;

/**
 * The server is pushing the player.
 *
 * <p>Posted when a velocity update arrives for the local player &mdash; a hit,
 * an explosion, a piston. Cancelling ignores it outright; scaling it takes a
 * fraction, which is the setting most people actually want because taking none
 * at all is both obvious to anyone watching and occasionally worse to play with.
 *
 * <pre>{@code
 * @Subscribe
 * private void onKnockback(KnockbackEvent event) {
 *     event.scale(0.5d, 0.8d);      // half the push, most of the lift
 * }
 * }</pre>
 *
 * <h2>On the game thread, unlike the packet it comes from</h2>
 * {@link PacketEvent.Receive} fires on a Netty worker, which is the wrong place
 * to decide anything that depends on game state. This is posted from the
 * handler the game runs <em>after</em> handing the packet to the main thread, so
 * a module can read the world, check who hit it, and look at its own settings
 * without marshalling.
 *
 * <p>That is the general shape for anything inbound and semantic: the raw packet
 * is available early and off-thread for the rare module that must cancel before
 * the game sees it, and the meaningful event is posted late and on-thread for
 * everything else.
 *
 * <p>Only ever posted for the local player. Velocity updates for other entities
 * pass through untouched.
 *
 * <h2>Two sources, applied differently</h2>
 * A velocity packet <em>replaces</em> the player's motion; an explosion's
 * knockback is <em>added</em> to it ({@code Entity.addVelocityInternal}). Either
 * way {@link #getVelocity()} is what the packet carried and what the game would
 * apply, so scaling and cancelling mean the same thing for both. A mode that
 * should only act on one checks {@link #getSource()}.
 */
@Getter
public final class KnockbackEvent extends CancellableEvent {

    /** Where the push came from. */
    public enum Source {
        /** A velocity packet: a hit, a plugin, a wind charge. Replaces the motion. */
        VELOCITY,
        /** An explosion's knockback: a crystal, an anchor, TNT. Added to the motion. */
        EXPLOSION
    }

    private final Source source;

    /** The push the server sent, in blocks per tick. */
    private Vec3 velocity;

    /** Whether a handler replaced it, so the adapter knows to apply its own. */
    private boolean modified;

    public KnockbackEvent(Source source, Vec3 velocity) {
        this.source = source;
        this.velocity = velocity;
    }

    /** @return whether this is an explosion's knockback rather than a velocity packet */
    public boolean isExplosion() {
        return source == Source.EXPLOSION;
    }

    /** Replaces the push outright. Ignored if null. */
    public void setVelocity(Vec3 replacement) {
        if (replacement != null) {
            this.velocity = replacement;
            this.modified = true;
        }
    }

    /**
     * Takes a fraction of the push.
     *
     * <p>Horizontal and vertical separately, because they are felt differently:
     * the horizontal component is what moves you out of a fight and the vertical
     * is what breaks a jump. Taking all of one and none of the other is a common
     * and sensible setting.
     *
     * @param horizontal multiplier for X and Z; 0 ignores the push, 1 keeps it
     * @param vertical   multiplier for Y
     */
    public void scale(double horizontal, double vertical) {
        setVelocity(Vec3.of(
                velocity.getX() * horizontal,
                velocity.getY() * vertical,
                velocity.getZ() * horizontal));
    }
}
