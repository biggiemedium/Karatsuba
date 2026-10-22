package dev.px.karatsuba.adapter.event.game.player;

import dev.px.core.event.CancellableEvent;
import dev.px.core.math.Direction;
import dev.px.core.math.Vec3i;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * The player clicked the attack or the use key in the world.
 *
 * <p>Posted from the game's own click handling ({@code doAttack} and
 * {@code doItemUse}) before it does anything, for whatever the crosshair is on: a
 * block, an entity, or nothing. Cancelling makes the click not happen at all: no
 * packet, no swing, no dig started, nothing hit, placed, opened or used. A module
 * that still wants the arm to move swings it itself.
 *
 * <p>An attack click is always the player's: a control a module holds down
 * through {@code Inputs} never counts as a fresh press. A use click can also come
 * from such a hold, since vanilla repeats use while the key is down;
 * {@link #isByPlayer()} tells them apart.
 *
 * <p>Posted on the game thread.
 */
@Getter
@RequiredArgsConstructor
public final class ClickEvent extends CancellableEvent {

    public enum Button {
        /** Left click: hit an entity, or start breaking a block. */
        ATTACK,
        /** Right click: use the held item, or place against or use a block. */
        USE
    }

    public enum Target {
        BLOCK,
        ENTITY,
        /** Nothing in reach: a use in the air, an attack at nothing. */
        NONE
    }

    private final Button button;

    private final Target target;

    /** The block under the crosshair; null unless {@link #getTarget()} is BLOCK. */
    private final Vec3i position;

    /** The face of that block the crosshair is on; null unless BLOCK. */
    private final Direction face;

    /** Whether the player's own key made the click, rather than one a module holds down. */
    private final boolean byPlayer;

    public boolean isBlock() {
        return target == Target.BLOCK;
    }
}
