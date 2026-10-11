package dev.px.karatsuba.client.automation.navigation;

import dev.px.core.flow.Control;
import dev.px.core.flow.FlowContext;
import dev.px.core.flow.Status;
import dev.px.core.flow.Step;
import dev.px.core.flow.StopReason;
import dev.px.core.math.Vec3;
import dev.px.core.movement.simulation.MotionState;
import dev.px.core.navigation.Goal;
import dev.px.core.navigation.Progress;
import dev.px.core.navigation.Route;
import dev.px.karatsuba.adapter.movement.MotionBridge;

import java.util.List;
import java.util.function.BooleanSupplier;
import java.util.function.Function;

/**
 * A flow step that gets the player to a goal: what {@link Navigation#to} hands
 * out. Its {@link Driver} walks pipelined segments, so it moves without stopping
 * between plans.
 *
 * <ul>
 *   <li><b>Arrived:</b> done.</li>
 *   <li><b>Failed:</b> no way there, as far as the planners can tell; the step
 *       fails with the reason, so a {@code firstOf}, {@code retry} or
 *       {@code orElse} can take over.</li>
 *   <li><b>Stuck:</b> keeps trying, unless {@code Give Up When Stuck} says
 *       otherwise. Its {@linkplain Step#progress progress} is the blocks left, so a
 *       flow's {@code stuckAfter} notices too.</li>
 *   <li><b>Paused:</b> stops; on resuming, the goal is asked for again and the trip
 *       begins afresh from wherever the player is.</li>
 * </ul>
 *
 * <p>Declares {@link Control#MOVEMENT}, and {@link Control#ROTATION} unless it
 * leaves the head to something beside it. Game thread only.
 */
public final class Trip extends Step {

    private final Function<FlowContext, Goal> goalOf;
    private final Driver driver;
    private final BooleanSupplier failWhenStuck;
    private String problem;

    Trip(Function<FlowContext, Goal> goalOf, String name, Driver driver, boolean turnHead,
         BooleanSupplier failWhenStuck) {
        super(name);
        this.goalOf = goalOf;
        this.driver = driver;
        this.failWhenStuck = failWhenStuck;
        uses(Control.MOVEMENT);
        if (turnHead) {
            uses(Control.ROTATION);
        }
    }

    @Override
    protected void start(FlowContext c) {
        problem = null;
        Goal goal = goalOf.apply(c);
        if (goal == null) {
            problem = "there is no goal to travel to";
            driver.stop();
            return;
        }
        driver.travel(goal);
    }

    @Override
    protected Status tick(FlowContext c) {
        if (problem != null) {
            return c.fail(problem);
        }
        MotionState state = MotionBridge.currentState();
        if (state == null) {
            return c.fail("the player's state is unknown");
        }
        Progress progress = driver.tick(c, state);
        if (progress.isArrived()) {
            return Status.DONE;
        }
        if (progress.isFailed()) {
            return c.fail(progress.getReason());
        }
        if (progress.isStuck() && failWhenStuck.getAsBoolean()) {
            return c.fail("stuck: " + progress.getReason());
        }
        return Status.RUNNING;
    }

    @Override
    protected void stop(FlowContext c, StopReason why) {
        driver.stop();
    }

    /** @return the blocks left to the goal, at least; NaN before the first tick */
    @Override
    protected double progress(FlowContext c) {
        return driver.getProgress().getRemaining();
    }

    // --------------------------------------------------------------- reads

    /** @return whether this trip is under way: a goal, not arrived, not failed */
    public boolean isTravelling() {
        return driver.isTravelling();
    }

    /** @return the goal, as the trip last started; null before */
    public Goal getGoal() {
        return driver.getGoal();
    }

    /** @return how the trip is going, as of the last tick */
    public Progress getProgress() {
        return driver.getProgress();
    }

    /** @return how often it planned, and why */
    public TripStats getStats() {
        return driver.getStats();
    }

    /** @return the segment being walked, or null; for drawing */
    public Route getSegment() {
        return driver.getSegment();
    }

    /** @return how far into the segment the player is, in ticks */
    public int getSegmentTick() {
        return driver.getSegmentTick();
    }

    /** @return the long-range waypoints still ahead; empty when the goal is within reach */
    public List<Vec3> getWaypointsAhead() {
        return driver.getWaypointsAhead();
    }
}
