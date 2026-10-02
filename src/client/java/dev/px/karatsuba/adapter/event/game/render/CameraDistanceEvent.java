package dev.px.karatsuba.adapter.event.game.render;

import dev.px.core.event.Event;
import lombok.Getter;

/**
 * How far behind the player the third-person camera wants to sit, and whether
 * walls get a say.
 *
 * <p>Posted from the game's wall check, which only runs in third person, once a
 * frame. The game asks for a distance (4 blocks, scaled with the entity) and
 * then pulls the camera in until nothing solid is between it and the player. A
 * handler can change either half:
 *
 * <ul>
 *   <li>{@link #setDistance(float)} moves the camera further out or closer in.
 *       Walls still pull it in unless clipping is also turned off.</li>
 *   <li>{@link #setClipping(boolean)} with {@code false} skips the wall check, so
 *       the camera sits at exactly the distance asked for, through whatever is in
 *       the way.</li>
 * </ul>
 *
 * <p>Not cancellable: there is always a distance. {@code CameraEvent} still fires
 * afterwards, with the position this produced.
 */
@Getter
public final class CameraDistanceEvent extends Event {

    /** The distance the camera will be placed at, before any wall check. */
    private float distance;

    /** Whether walls between the camera and the player pull it in. */
    private boolean clipping = true;

    public CameraDistanceEvent(float distance) {
        this.distance = distance;
    }

    /** Replaces the distance. Negative values are clamped to 0. */
    public void setDistance(float replacement) {
        this.distance = Math.max(0f, replacement);
    }

    /** {@code false} places the camera through walls rather than in front of them. */
    public void setClipping(boolean clip) {
        this.clipping = clip;
    }
}
