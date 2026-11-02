package dev.px.karatsuba.adapter.event.game.player;

import dev.px.core.event.CancellableEvent;
import lombok.Getter;

/**
 * The game is about to slow the player for using an item: eating, drinking,
 * blocking, drawing a bow.
 *
 * <p>Vanilla scales both movement inputs by {@link #VANILLA} in
 * {@code ClientPlayerEntity.tickMovement}, straight after the keys are read and
 * corrected, every tick an item is in use on foot. Cancelling removes the
 * slowdown; {@link #setMultiplier} changes it instead.
 *
 * <p>The sprint rules for using an item ({@code shouldStopSprinting},
 * {@code canStartSprinting}) are untouched unless {@link #allowSprint()} is called.
 *
 * <p>Posted on the game thread, once a tick, only while an item is in use on foot:
 * after {@code Input.tick()}, Move Fix and the movement claims, and before
 * vanilla's first sprint check.
 */
@Getter
public final class SlowdownEvent extends CancellableEvent {

    /** What vanilla multiplies the inputs by while an item is in use. */
    public static final float VANILLA = 0.2f;

    /**
     * Whether this tick's movement input is not zero, after Move Fix and the
     * movement claims. With no input the slowdown changes nothing: the move is
     * the same slowed or not.
     */
    private final boolean moving;

    /** What the inputs are multiplied by this tick. Ignored when cancelled. */
    private float multiplier = VANILLA;

    /** Whether vanilla's sprint rules ignore the item this tick; see {@link #allowSprint()}. */
    private boolean sprintAllowed;

    public SlowdownEvent(boolean moving) {
        this.moving = moving;
    }

    /**
     * Lets the player sprint this tick although an item is in use: vanilla's
     * {@code shouldStopSprinting} and {@code canStartSprinting} skip their
     * item-use test. Every other sprint rule still applies, and sprint still has to
     * be asked for (the key, Sprint, Strafe). On a tick that doesn't allow it,
     * vanilla stops the sprint itself.
     */
    public void allowSprint() {
        this.sprintAllowed = true;
    }

    public void setMultiplier(float multiplier) {
        this.multiplier = multiplier;
    }

    /** @return the multiplier the game should use: 1 when cancelled */
    public float applied() {
        return isCancelled() ? 1f : multiplier;
    }
}
