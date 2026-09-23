package dev.px.karatsuba.adapter.event.game;

import dev.px.core.event.Event;
import dev.px.core.math.Vec2;
import dev.px.core.math.Vec3;
import lombok.Getter;

/**
 * Where the camera is about to be, with a chance to move it.
 *
 * <p>Posted once per frame after the game has positioned the camera and before
 * anything is drawn with it. Not cancellable &mdash; there is no such thing as
 * "no camera" &mdash; so a handler changes it instead:
 *
 * <pre>{@code
 * @Subscribe
 * private void onCamera(CameraEvent event) {
 *     event.setPosition(freePosition);
 * }
 * }</pre>
 *
 * <p>A handler that sets nothing leaves the frame exactly as the game built it,
 * which is what {@link #isModified()} is for: the adapter only writes back when
 * something asked it to, so an idle client costs one event post and no camera
 * work at all.
 *
 * <p><b>The camera, not the player.</b> Moving it detaches the view and nothing
 * else: the body stays where it was, the server is told nothing, and the world
 * still culls and renders around the new position. A module that wants the player
 * to stay put while the camera travels has to say so separately &mdash; see
 * {@link PlayerMoveEvent}.
 *
 * <p>Posted on the render thread, inside the frame. Cheap work only.
 */
@Getter
public final class CameraEvent extends Event {

    /** The frame's interpolation between ticks, for anything smoothing a motion. */
    private final float tickDelta;

    /** Whether the game is in one of its third-person perspectives. */
    private final boolean thirdPerson;

    /** Eye position the game resolved, or whatever a handler replaced it with. */
    private Vec3 position;

    /** Yaw and pitch the game resolved, or whatever a handler replaced them with. */
    private Vec2 rotation;

    /**
     * Whether anything actually changed.
     *
     * <p>Raised by the setters rather than compared afterwards, so a handler that
     * assigns the value the camera already had still counts as a write. That is
     * the honest reading: it asked for that position, and it happens to match.
     */
    private boolean modified;

    public CameraEvent(float tickDelta, boolean thirdPerson, Vec3 position, Vec2 rotation) {
        this.tickDelta = tickDelta;
        this.thirdPerson = thirdPerson;
        this.position = position;
        this.rotation = rotation;
    }

    /** Moves the camera. Ignored if null, so a handler need not guard its own state. */
    public void setPosition(Vec3 replacement) {
        if (replacement != null) {
            this.position = replacement;
            this.modified = true;
        }
    }

    /** Turns the camera. Ignored if null. */
    public void setRotation(Vec2 replacement) {
        if (replacement != null) {
            this.rotation = replacement;
            this.modified = true;
        }
    }
}
