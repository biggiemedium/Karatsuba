package dev.px.karatsuba.client.module.movement;

import dev.px.core.Core;
import dev.px.core.math.Vec3;
import dev.px.core.math.Vec3i;
import dev.px.core.movement.simulation.MotionState;
import dev.px.core.movement.simulation.MovementInput;
import dev.px.karatsuba.adapter.game.player.Inputs;
import dev.px.karatsuba.adapter.game.player.Players;
import dev.px.karatsuba.adapter.movement.MotionBridge;
import dev.px.karatsuba.adapter.world.Blocks;

import java.util.List;

/**
 * What happens if the player keeps doing what they are doing: the movement
 * modules' shared question, answered by Core's movement simulation.
 *
 * <p>The simulation steps the player's own physics (their attributes, the block
 * underfoot, every collision box nearby) forward from where they stand, with the
 * keys they hold. So "will I walk off this edge?" and "is a jump enough to get up
 * there?" are answered by the same rules the server simulates, not by guessing
 * from the blocks in front of the player.
 *
 * <p>Asked at the start of a tick, before the move, from where the last tick left
 * the player. The keys are read at the camera's yaw: under a silent rotation the
 * Move Fix rewrites them to walk the same way, to within a key step, so the
 * direction is the same.
 *
 * <p>Game thread only.
 */
public final class Ledges {

    private Ledges() {
    }

    /** @return the player's motion now, or null out of a world */
    public static MotionState now() {
        return MotionBridge.currentState();
    }

    /** @return whether any of the four movement keys is held */
    public static boolean moving() {
        return Inputs.anyMovement();
    }

    /**
     * The keys held now, as Core's input, at the camera's yaw.
     *
     * @param jump  whether to simulate a jump as well
     * @param sneak whether to simulate sneaking
     */
    public static MovementInput held(boolean jump, boolean sneak) {
        double forward = axis(Inputs.pressed(Inputs.Control.FORWARD), Inputs.pressed(Inputs.Control.BACK));
        double strafe = axis(Inputs.pressed(Inputs.Control.LEFT), Inputs.pressed(Inputs.Control.RIGHT));
        boolean sprint = Players.sprinting() || Inputs.pressed(Inputs.Control.SPRINT);
        return MovementInput.of(Players.rotation().getYaw(), forward, strafe)
                .withJump(jump)
                .withSneak(sneak)
                .withSprint(sprint && !sneak);
    }

    /**
     * @return the states of the next {@code ticks} ticks with these keys held,
     *         first tick first; empty out of a world
     */
    public static List<MotionState> ahead(MovementInput input, int ticks) {
        MotionState now = now();
        return now == null ? List.of() : Core.simulation().trace(now, input, ticks);
    }

    /**
     * @return how many blocks of fall lie below a position: the cells of air
     *         straight down from the feet before something solid, up to {@code max}
     *         (so {@code max} means "at least that far")
     */
    public static int dropBelow(Vec3 feet, int max) {
        int x = (int) Math.floor(feet.getX());
        int z = (int) Math.floor(feet.getZ());
        int top = (int) Math.floor(feet.getY() - 1.0E-3d);
        for (int depth = 0; depth < max; depth++) {
            if (Blocks.isSolid(Vec3i.of(x, top - depth, z))) {
                return depth;
            }
        }
        return max;
    }

    /** @return the horizontal distance between two positions */
    public static double horizontal(Vec3 from, Vec3 to) {
        double dx = to.getX() - from.getX();
        double dz = to.getZ() - from.getZ();
        return Math.sqrt(dx * dx + dz * dz);
    }

    private static double axis(boolean positive, boolean negative) {
        return (positive ? 1d : 0d) - (negative ? 1d : 0d);
    }
}
