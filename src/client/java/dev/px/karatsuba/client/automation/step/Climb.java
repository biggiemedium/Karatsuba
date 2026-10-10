package dev.px.karatsuba.client.automation.step;

import dev.px.core.flow.Control;
import dev.px.core.flow.FlowContext;
import dev.px.core.flow.Status;
import dev.px.core.flow.Step;
import dev.px.core.math.Vec2;
import dev.px.core.math.Vec3;
import dev.px.core.movement.simulation.MovementInput;
import dev.px.karatsuba.adapter.game.player.Players;

/**
 * Climbs a {@link Ladder} up or down, as a player would.
 *
 * <p>Core's simulation does not model climbing, so routes stop at the foot or the
 * head of a ladder and this takes over. It only holds keys and turns the head;
 * the climbing is the game's own physics, so the movement Grim simulates is the
 * movement it sees.
 *
 * <ul>
 *   <li><b>Up:</b> step onto the column, face the wall, look up it, and walk
 *       forward: pressing into a ladder is what climbs it. Forward also carries
 *       the player off the top onto what the ladder leads to. Done once standing
 *       above the column.</li>
 *   <li><b>Down:</b> step forward off the head of the column, away from the
 *       wall, to where nothing holds the feet up &mdash; not even the ladder's own
 *       plate &mdash; then let go of the keys, facing the wall and looking down it:
 *       a ladder lets the player down slowly. Done once standing at its foot.</li>
 * </ul>
 *
 * <p>Never sprints, and never walks backwards: every move here is forward, at
 * the yaw the head is turning to. Its progress is the height, so a flow's
 * {@code stuckAfter} notices a climb that has stopped.
 */
public final class Climb extends Step {

    /** How far off the column's middle the feet may be and still be on it. */
    private static final double ON_COLUMN = 0.3d;

    /** Degrees: looking up the ladder while climbing, down it while descending. */
    private static final float LOOK_UP = -30f;
    private static final float LOOK_DOWN = 40f;

    /**
     * Blocks toward the wall the feet may be and the body still clear the ladder's
     * plate: half a block, less the plate (3/16) and half the body (0.3).
     */
    private static final double CLEAR_OF_PLATE = 0.5d - 0.1875d - 0.3d;

    /** Blocks: close enough to the end height to count as there. */
    private static final double ARRIVED = 0.05d;

    private final Ladder ladder;
    private final boolean up;

    private Climb(Ladder ladder, boolean up) {
        super((up ? "climb up " : "climb down ") + ladder);
        this.ladder = ladder;
        this.up = up;
        uses(Control.MOVEMENT, Control.ROTATION);
    }

    /** @return a step that climbs {@code ladder} from its foot to the top */
    public static Climb up(Ladder ladder) {
        return new Climb(ladder, true);
    }

    /** @return a step that climbs down {@code ladder} from its head to the foot */
    public static Climb down(Ladder ladder) {
        return new Climb(ladder, false);
    }

    @Override
    protected Status tick(FlowContext c) {
        Vec3 feet = Players.position();
        if (arrived(feet)) {
            return Status.DONE;
        }
        return up ? climbUp(c, feet) : climbDown(c, feet);
    }

    private Status climbUp(FlowContext c, Vec3 feet) {
        if (!ladder.holds(feet, ON_COLUMN)) {
            // Onto the column first: walk forward to its middle, looking where we go.
            float yaw = ladder.yawToMiddle(feet);
            c.look(Gaze.toward(Vec2.rotation(yaw, 0f)));
            c.move(MovementInput.forward(yaw));
            return Status.RUNNING;
        }
        float yaw = ladder.climbYaw(feet);
        c.look(Gaze.toward(Vec2.rotation(yaw, LOOK_UP)));
        c.move(MovementInput.forward(yaw));
        return Status.RUNNING;
    }

    private Status climbDown(FlowContext c, Vec3 feet) {
        boolean clear = ladder.holds(feet, ON_COLUMN) && ladder.towardWall(feet) <= CLEAR_OF_PLATE;
        if (Players.onGround() && !clear) {
            // Standing at the head, on the wall or on the ladder's own plate: step
            // forward to where nothing holds the feet up, away from the wall.
            float yaw = feet.rotationTo(ladder.descent(feet.getY())).getYaw();
            c.look(Gaze.toward(Vec2.rotation(yaw, LOOK_DOWN)));
            c.move(MovementInput.forward(yaw));
            return Status.RUNNING;
        }
        // On the ladder: no keys, and it lets the player down slowly.
        float yaw = ladder.climbYaw(feet);
        c.look(Gaze.toward(Vec2.rotation(yaw, LOOK_DOWN)));
        c.move(MovementInput.none(yaw));
        return Status.RUNNING;
    }

    /** @return the height climbed, rising either way, for a flow's stuck check */
    @Override
    protected double progress(FlowContext c) {
        double y = Players.position().getY();
        return up ? y : -y;
    }

    private boolean arrived(Vec3 feet) {
        if (!Players.onGround()) {
            return false;
        }
        return up
                ? feet.getY() >= ladder.getTopY() + 1d - ARRIVED
                : feet.getY() <= ladder.getBottom().getY() + ARRIVED;
    }
}
