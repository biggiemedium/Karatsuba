package dev.px.karatsuba.client.automation.step;

import dev.px.core.math.Vec2;
import dev.px.core.math.Vec3;
import dev.px.core.movement.rotation.RotationMode;
import dev.px.core.movement.rotation.RotationRequest;
import dev.px.core.util.math.RotationMath;
import dev.px.karatsuba.adapter.game.player.Players;
import dev.px.karatsuba.client.settings.ClientSettings;

/**
 * How a step turns the head: the way a hand on a mouse does.
 *
 * <p>A fixed number of degrees a tick reads as a machine &mdash; the same speed
 * at the start of a turn as at the end, then a dead stop. A person flicks most of
 * the way and settles the rest. So each tick takes a share of what is left to
 * turn ({@link #EASE}), never less than {@link #MIN_STEP} so it arrives, and never
 * more than the Automation settings' Turn Speed. Core then rounds every turn onto
 * the steps the player's own mouse sensitivity can make.
 *
 * <pre>{@code
 * c.look(Gaze.toward(eyes.rotationTo(target)));     // in the settings' rotation mode
 * c.look(Gaze.camera(eyes.rotationTo(block)));      // the camera itself: the crosshair must be on it
 * if (Gaze.serverFaces(wanted, 2f)) { ... }          // the server has been told it
 * }</pre>
 */
public final class Gaze {

    /** The share of the remaining turn taken each tick. */
    private static final float EASE = 0.5f;

    /** Degrees: the smallest step, so a turn finishes rather than creeping. */
    private static final float MIN_STEP = 4f;

    private Gaze() {
    }

    /** @return a look toward {@code target}, eased, in the Automation settings' rotation mode */
    public static RotationRequest toward(Vec2 target) {
        return eased(target, ClientSettings.automation().rotationMode());
    }

    /** @return a look toward {@code point}, from the eyes */
    public static RotationRequest toward(Vec3 point) {
        return toward(Players.eyePosition().rotationTo(point));
    }

    /**
     * @return a look that turns the camera itself, eased: for anything the game
     *         does where the crosshair is, such as breaking a block
     */
    public static RotationRequest camera(Vec2 target) {
        return eased(target, RotationMode.CLIENT);
    }

    /** @return a camera look toward {@code point}, from the eyes */
    public static RotationRequest camera(Vec3 point) {
        return camera(Players.eyePosition().rotationTo(point));
    }

    /** @return whether the rotation last sent to the server is within {@code degrees} of {@code target} */
    public static boolean serverFaces(Vec2 target, float degrees) {
        return RotationMath.difference(Players.serverRotation(), target) <= degrees;
    }

    private static RotationRequest eased(Vec2 target, RotationMode mode) {
        float remaining = RotationMath.difference(Players.serverRotation(), target);
        float limit = ClientSettings.automation().turnSpeed();
        float step = Math.max(MIN_STEP, Math.min(limit, remaining * EASE));
        return RotationRequest.at(target).step(step).mode(mode);
    }
}
