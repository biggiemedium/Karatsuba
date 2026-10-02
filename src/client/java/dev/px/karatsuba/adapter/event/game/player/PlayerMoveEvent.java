package dev.px.karatsuba.adapter.event.game.player;

import dev.px.core.event.Stage;
import dev.px.core.event.StagedEvent;
import dev.px.core.math.Vec3;
import lombok.Getter;

/**
 * The player's movement for this tick, with a chance to change it.
 *
 * <p>Posted around {@code tickMovement}, which is where the game turns held keys
 * into velocity. {@link Stage#PRE} is before that happens and is where a handler
 * influences the result; {@link Stage#POST} is after, and is where one reads what
 * the movement actually came out as.
 *
 * <p>Two ways to act on it, and they are not the same:
 *
 * <ul>
 *   <li>{@link #setVelocity} replaces the motion. This is how a speed, a glide or
 *       a launch is written, and the game carries on normally around it.</li>
 *   <li>{@link #cancel()} skips the game's movement tick entirely. The player
 *       stops dead &mdash; no gravity, no friction, no input &mdash; which is
 *       what a freecam or a freeze wants and almost nothing else does.</li>
 * </ul>
 *
 * <pre>{@code
 * @Subscribe(stage = Stage.PRE)
 * private void onMove(PlayerMoveEvent event) {
 *     Vec3 motion = event.getVelocity();
 *     event.setVelocity(Vec3.of(motion.getX() * 1.2d, motion.getY(), motion.getZ() * 1.2d));
 * }
 * }</pre>
 *
 * <p>Cancelling PRE means POST is never posted, because the movement it would
 * report never happened.
 *
 * <p>Posted on the game thread, once per tick, only while there is a player.
 */
@Getter
public final class PlayerMoveEvent extends StagedEvent {

    /** Feet position as the stage began. */
    private final Vec3 position;

    /**
     * The motion the game is about to apply, or applied.
     *
     * <p>Blocks per tick, not per second. Y carries gravity, so a handler that
     * replaces the whole vector without keeping it will make the player float.
     */
    private Vec3 velocity;

    /** Whether a handler replaced the velocity, so the adapter knows to write it back. */
    private boolean modified;

    public PlayerMoveEvent(Stage stage, Vec3 position, Vec3 velocity) {
        super(stage);
        this.position = position;
        this.velocity = velocity;
    }

    /** Replaces the motion for this tick. Ignored if null. */
    public void setVelocity(Vec3 replacement) {
        if (replacement != null) {
            this.velocity = replacement;
            this.modified = true;
        }
    }
}
