package dev.px.karatsuba.adapter.event.game.network;

import dev.px.core.event.CancellableEvent;
import dev.px.core.math.Vec2;
import dev.px.core.math.Vec3;
import lombok.AccessLevel;
import lombok.Getter;

/**
 * The position and on-ground flag the client is about to send, with a chance to
 * change them.
 *
 * <p>The movement packet is what the server believes about the player, and it is
 * the last thing a movement module can influence before that belief is formed.
 * Everything else &mdash; velocity, input, collision &mdash; happens upstream of
 * this and arrives here as three doubles and a boolean.
 *
 * <pre>{@code
 * @Subscribe
 * private void onMovementPacket(MovementPacketEvent event) {
 *     if (!event.isOnGround()) {
 *         event.setOnGround(true);        // the server stops charging for the sprint
 *     }
 * }
 * }</pre>
 *
 * <p>Cancelling suppresses the packet entirely. That is a blunt instrument: the
 * server keeps its previous idea of where the player is, and doing it for more
 * than a tick or two reads as a stall.
 *
 * <h2>Rotation is readable and not writable, on purpose</h2>
 * The yaw and pitch are here because a handler often needs to know them, and
 * they are deliberately not settable. Rotation already has an owner in Core's
 * {@code RotationService}, which arbitrates between modules and eases back when
 * the last claim expires. A second way to write it from down here would be a
 * bypass around all of that &mdash; two modules could disagree with no way to
 * settle it, which is the exact failure the arbiters exist to prevent.
 *
 * <p>To move the head, file a rotation claim. It reaches this packet anyway,
 * because {@code Rotations} swaps the resolved value in before the game builds
 * it.
 *
 * <h2>Not every packet carries everything</h2>
 * The game sends a smaller packet when only part of the state changed:
 * {@link #hasPosition()} and {@link #hasRotation()} say which fields are real.
 * Setting a position on a packet that carries none is ignored, because the
 * alternative is inventing a field the server will not read.
 *
 * <p>Posted on the game thread, from the send path.
 */
@Getter
public final class MovementPacketEvent extends CancellableEvent {

    /**
     * Whether this packet actually carries a position.
     *
     * <p>Hand-written accessor rather than a generated one: Lombok would name it
     * {@code isHasPosition()}, and this is read at every call site that writes a
     * position.
     */
    @Getter(AccessLevel.NONE)
    private final boolean hasPosition;

    /** Whether this packet actually carries a rotation. */
    @Getter(AccessLevel.NONE)
    private final boolean hasRotation;

    /** Where the client says it is. Meaningless unless {@link #hasPosition()}. */
    private Vec3 position;

    /** Where the client says it is looking. Read-only; see the class note. */
    private final Vec2 rotation;

    private boolean onGround;

    /** Whether a handler changed anything, so the adapter knows to write back. */
    private boolean modified;

    public MovementPacketEvent(boolean hasPosition, boolean hasRotation,
                               Vec3 position, Vec2 rotation, boolean onGround) {
        this.hasPosition = hasPosition;
        this.hasRotation = hasRotation;
        this.position = position;
        this.rotation = rotation;
        this.onGround = onGround;
    }

    /** @return whether {@link #getPosition()} means anything on this packet */
    public boolean hasPosition() {
        return hasPosition;
    }

    /** @return whether {@link #getRotation()} means anything on this packet */
    public boolean hasRotation() {
        return hasRotation;
    }

    /**
     * Moves what the server is told, without moving the player.
     *
     * <p>Ignored on a packet that carries no position, and ignored for null. The
     * client stays where it was; only the claim changes, which is why anything
     * the server can check will notice a large one.
     */
    public void setPosition(Vec3 replacement) {
        if (hasPosition && replacement != null) {
            this.position = replacement;
            this.modified = true;
        }
    }

    /**
     * Changes the on-ground claim.
     *
     * <p>Present on every movement packet, so unlike the position this always
     * takes effect.
     */
    public void setOnGround(boolean onGround) {
        this.onGround = onGround;
        this.modified = true;
    }
}
