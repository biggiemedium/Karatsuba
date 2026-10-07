package dev.px.karatsuba.client.service.action;

import dev.px.core.movement.rotation.RotationMode;
import dev.px.karatsuba.adapter.game.player.Interactions;

/**
 * How careful {@code ActionService} is, for the server being played on.
 *
 * <p>One profile for every module, so a crystal aura and a surround never
 * disagree about what the server allows. The defaults here are for Grim: look
 * at what you click, from within reach, one attack and one right-click a tick,
 * never while eating or in a container. The Interaction section of Client Settings is the
 * player's way to change them; until it is registered these are what applies.
 *
 * <p>Read every tick, so a changed setting applies at once.
 */
public interface ActionProfile {

    /**
     * Whether the checks run at all: reach, look, busy hands and screens. Off,
     * a winning action is sent as soon as it has its item.
     */
    default boolean checks() {
        return true;
    }

    /** Whether the service turns the player toward what an action aims at. */
    default boolean rotate() {
        return true;
    }

    default RotationMode rotationMode() {
        return RotationMode.SILENT;
    }

    /** Degrees a tick the turn may cover; 180 snaps. */
    default float turnSpeed() {
        return 180f;
    }

    /**
     * Whether a click or attack waits until the rotation the server has looks
     * at it, and a click is sent at the point that look lands on.
     */
    default boolean raytrace() {
        return true;
    }

    default SwapMode swap() {
        return SwapMode.SILENT;
    }

    default Interactions.Swing swing() {
        return Interactions.Swing.CLIENT;
    }

    /** Ticks at least between two right-clicks; 0 allows one every tick. */
    default int clickDelay() {
        return 0;
    }

    /** Ticks at least between two attacks; 0 allows one every tick. */
    default int attackDelay() {
        return 0;
    }

    /** Whether an attack and a right-click may both go out in one tick, as vanilla allows. */
    default boolean sameTick() {
        return true;
    }

    /** Whether to act while an item is in use: eating, drinking, blocking. */
    default boolean multitask() {
        return false;
    }

    /** Whether a click against nothing (an air place) may be sent. */
    default boolean airPlace() {
        return false;
    }
}
