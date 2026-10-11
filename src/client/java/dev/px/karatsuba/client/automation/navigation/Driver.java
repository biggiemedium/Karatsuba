package dev.px.karatsuba.client.automation.navigation;

import dev.px.core.flow.FlowContext;
import dev.px.core.math.Vec2;
import dev.px.core.math.Vec3;
import dev.px.core.movement.rotation.RotationRequest;
import dev.px.core.movement.simulation.MotionState;
import dev.px.core.movement.simulation.MovementInput;
import dev.px.core.movement.simulation.SimulationService;
import dev.px.core.navigation.Goal;
import dev.px.core.navigation.PathProvider;
import dev.px.core.navigation.Progress;
import dev.px.core.navigation.Route;
import dev.px.karatsuba.adapter.game.player.Players;
import dev.px.karatsuba.client.settings.automation.AutomationSettings;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/**
 * Walks the player to a goal, one planned segment after another, without
 * stopping between them.
 *
 * <h2>Pipelined segments</h2>
 * A segment is a precise route from the local planner: the keys for every tick
 * and the state expected after each. The moment a segment starts being walked,
 * the next is asked for from the state this one <em>ends</em> in, on the
 * {@link SegmentPlanner}'s worker. By the time the player reaches that state the
 * next segment is normally waiting, and is spliced in on the same tick, so the
 * keys never let go. This is how Baritone keeps moving too: it computes the next
 * segment while the current one is being executed.
 *
 * <p>Only when a plan is genuinely late, slower than the segment is long, does
 * movement pause: the player is braked to a stop and the next segment planned
 * from standing, which stays valid however long it takes.
 *
 * <h2>Replanning without stopping</h2>
 * <ul>
 *   <li><b>A followed goal moves:</b> a segment is asked for from a state a few
 *       ticks ahead on the current one, toward where the goal is now, and spliced
 *       in there.</li>
 *   <li><b>Knocked off the segment</b> (drift), or <b>the world changed ahead</b>
 *       (the rest replayed every few ticks no longer lands where it did): there is
 *       no state ahead to trust, so the player coasts a few ticks (walking on if
 *       that is safe, braking if not) while the new segment is planned from where
 *       the coast ends.</li>
 * </ul>
 *
 * <h2>Long range</h2>
 * Past the planner's range, a long-range route (block waypoints) is asked for
 * and each segment heads for the furthest waypoint within reach of where it will
 * start. A route that stops short is extended from where it ends, the same way.
 *
 * <p>Game thread only; one per {@link Trip}.
 */
final class Driver {

    /** Blocks between where a segment expected the player and where they are before it is given up. */
    private static final double DRIFT = 0.3d;
    /** Blocks a followed goal may move before a segment is aimed at it again. */
    private static final double GOAL_MOVED = 1.5d;
    /** Blocks from a long-range waypoint that count as having passed it. */
    private static final double WAYPOINT_REACHED = 0.75d;
    /** Blocks ahead along a long-range route each segment heads for. */
    private static final double SECTION_REACH = 16d;
    /** Blocks the remaining distance must fall by to count as getting closer. */
    private static final double CLOSER_BY = 0.25d;
    /** Ticks between replaying the rest of a segment through the world. */
    private static final int RECHECK_TICKS = 10;
    /** Ticks a brake may take before giving up on stopping: a long fall. */
    private static final int MAX_BRAKE = 20;
    /** Horizontal speed, blocks per tick, that counts as standing still. */
    private static final double AT_REST = 0.003d;
    /** Empty segments in a row before the trip is called failed: a goal the planner cannot leave. */
    private static final int EMPTY_LIMIT = 3;

    private final SegmentPlanner segments;
    private final Supplier<PathProvider> longRange;
    private final SimulationService simulation;
    private final RouteRules rules;
    private final AutomationSettings settings;
    private final boolean turnHead;
    private final boolean farGoals;
    private final TripStats stats = new TripStats();

    private Goal goal;
    private Progress failure;
    private Progress progress = Progress.failed("not travelling");

    /** The long-range route, or null while the goal is within the planner's reach. */
    private List<Vec3> waypoints;
    private boolean waypointsComplete;
    private int waypoint;
    private Vec3 anchorAtPlan;

    private Segment segment;
    private int index;
    private int sinceCheck;
    private Pending next;
    private int emptyInARow;

    private double bestRemaining;
    private int sinceCloser;

    /**
     * @param longRange the long-range provider to ask now, read each time
     * @param turnHead  whether to turn the head to face the keys
     * @param farGoals  whether to ask for long-range routes at all; off for close work
     */
    Driver(SegmentPlanner segments, Supplier<PathProvider> longRange, SimulationService simulation,
           RouteRules rules, AutomationSettings settings, boolean turnHead, boolean farGoals) {
        this.segments = segments;
        this.longRange = longRange;
        this.simulation = simulation;
        this.rules = rules;
        this.settings = settings;
        this.turnHead = turnHead;
        this.farGoals = farGoals;
    }

    // ---------------------------------------------------------------- trip

    /** Sets off for {@code goal}, replacing any trip under way. Nothing is planned until the next tick. */
    void travel(Goal goal) {
        stop();
        this.goal = goal;
        this.anchorAtPlan = goal.anchor();
        this.progress = Progress.running(Double.NaN);
        stats.reset();
    }

    /** Ends the trip. Claims lapse on their own once this stops filing them. */
    void stop() {
        dropNext();
        goal = null;
        failure = null;
        waypoints = null;
        waypoint = 0;
        segment = null;
        index = 0;
        sinceCheck = 0;
        emptyInARow = 0;
        bestRemaining = Double.POSITIVE_INFINITY;
        sinceCloser = 0;
        progress = Progress.failed("not travelling");
    }

    /**
     * One tick closer. Call once a tick at {@code TickEvent} PRE, with the state
     * the tick starts from, as a flow step's tick is.
     */
    Progress tick(FlowContext c, MotionState player) {
        if (goal == null) {
            return progress;
        }
        if (failure != null) {
            return failure;
        }
        Vec3 feet = player.getPosition();
        if (goal.isMet(feet)) {
            dropNext();
            segment = null;
            progress = Progress.arrived();
            return progress;
        }
        passWaypoints(feet);

        if (segment == null) {
            restart(player, Replan.START);
        } else {
            Replan off = check(player);
            if (off != null) {
                restart(player, off);
            } else if (goalMoved()) {
                anchorAtPlan = goal.anchor();
                waypoints = null;
                dropNext();
                ask(Math.min(segment.route.getTicks(), index + segments.lead()), Replan.GOAL_MOVED);
            }
        }
        if (failure != null) {
            return failure;
        }

        if (next != null && index >= next.spliceAt) {
            if (next.plan.isDone()) {
                if (!adopt(player)) {
                    return failure != null ? failure : measure(feet);
                }
            } else if (index >= segment.route.getTicks()) {
                if (segment.restAtEnd) {
                    // Standing where the next segment starts: wait there.
                    stats.waited();
                    c.move(MovementInput.none(c.rotations().getRotation().getYaw()));
                    return measure(feet);
                }
                restart(player, Replan.LATE);
            } else {
                // Not ready where it was to start; there is more of this one to walk, so ask again further on.
                dropNext();
                ask(Math.min(segment.route.getTicks(), index + segments.lead()), Replan.LATE);
            }
        }
        if (failure != null) {
            return failure;
        }
        if (index >= segment.route.getTicks()) {
            // Ran out with nothing to splice: the end of a partial route, or a last
            // segment that ended short. Plan on from here.
            restart(player, Replan.ROUTE_ENDED);
            if (failure != null) {
                return failure;
            }
        }

        follow(c, segment.route.inputAt(index++));
        return measure(feet);
    }

    // ------------------------------------------------------------ segments

    /** Takes the plan that was ready to splice in. @return false if the trip failed or must wait a tick */
    private boolean adopt(MotionState player) {
        Pending ready = next;
        next = null;
        Route route = ready.plan.result();
        stats.planned(ready.why);
        if (route == null) {
            failure = Progress.failed("found no way to " + ready.plan.getGoal() + ": " + ready.plan.reason(),
                    goal.gap(player.getPosition()).distance());
            progress = failure;
            return false;
        }
        if (route.getTicks() == 0) {
            // Already where that segment was going: aim further on from here.
            if (++emptyInARow >= EMPTY_LIMIT) {
                failure = Progress.failed("the planner keeps finding nothing to do toward " + goal,
                        goal.gap(player.getPosition()).distance());
                progress = failure;
                return false;
            }
            restart(player, Replan.ROUTE_ENDED);
            return failure == null;
        }
        emptyInARow = 0;
        stats.spliced();
        boolean last = route.isComplete() && ready.plan.getGoal() == goal;
        begin(new Segment(route, last, false));
        return true;
    }

    /** Starts walking {@code segment}, and asks for the one after it from where it ends. */
    private void begin(Segment started) {
        segment = started;
        index = 0;
        sinceCheck = 0;
        if (!started.last) {
            ask(started.route.getTicks(), started.restAtEnd ? null : Replan.NEXT);
        }
    }

    /**
     * Asks for a segment starting at tick {@code spliceAt} of the current one.
     *
     * @param why why it was asked for; null to keep the reason a restart gave
     */
    private void ask(int spliceAt, Replan why) {
        MotionState start = spliceAt == 0 ? segment.route.getStart() : segment.route.stateAt(spliceAt - 1);
        Goal toward = aimFrom(start);
        if (toward == null) {
            return;
        }
        next = new Pending(segments.submit(toward, start), spliceAt, why != null ? why : pendingWhy);
    }

    /** Why the segment a restart asks for is being asked for. */
    private Replan pendingWhy = Replan.START;

    /**
     * Gives up on the current segment from here: a coast while the next is
     * planned from where it ends, or, with planning on the game thread, the next
     * segment at once.
     */
    private void restart(MotionState player, Replan why) {
        dropNext();
        if (!segments.inBackground()) {
            Goal toward = aimFrom(player);
            if (toward == null) {
                return;
            }
            segment = new Segment(Route.precise(goal, player, List.of(), List.of(), false), false, false);
            index = 0;
            next = new Pending(segments.submit(toward, player), 0, why);
            adopt(player);
            return;
        }
        Goal toward = aimFrom(player);
        if (toward == null) {
            return;
        }
        Coast coast = walkOn(toward, player, segments.lead());
        if (coast == null) {
            coast = brake(player);
        }
        pendingWhy = why;
        begin(new Segment(coast.route, false, coast.atRest));
    }

    private void dropNext() {
        if (next != null) {
            next.plan.cancel();
            next = null;
        }
    }

    /** @return why the current segment should be given up, or null to carry on */
    private Replan check(MotionState player) {
        Route route = segment.route;
        MotionState expected = index == 0 ? route.getStart() : route.stateAt(index - 1);
        double off = expected.getPosition().distanceTo(player.getPosition());
        if (off > DRIFT) {
            stats.drifted(off);
            return Replan.DRIFT;
        }
        if (++sinceCheck >= RECHECK_TICKS && index < route.getTicks()) {
            sinceCheck = 0;
            MotionState state = expected;
            for (int tick = index; tick < route.getTicks(); tick++) {
                state = simulation.step(state, route.inputAt(tick));
            }
            if (state.getPosition().distanceTo(route.stateAt(route.getTicks() - 1).getPosition()) > DRIFT) {
                return Replan.BLOCKS_CHANGED;
            }
        }
        return null;
    }

    private boolean goalMoved() {
        Vec3 now = goal.anchor();
        return now != null && anchorAtPlan != null && now.distanceTo(anchorAtPlan) > GOAL_MOVED;
    }

    // ---------------------------------------------------------- long range

    /**
     * @return what a segment starting at {@code start} should head for: the goal
     *         itself when it is close, or the furthest long-range waypoint within
     *         reach. Null if the trip failed working that out
     */
    private Goal aimFrom(MotionState start) {
        Vec3 from = start.getPosition();
        Vec3 anchor = goal.anchor();
        boolean far = farGoals && settings.longRange() && anchor != null
                && anchor.horizontalDistanceTo(from) > settings.range();
        if (!far) {
            waypoints = null;
            return goal;
        }
        // Asked every segment: the long-range planner hands back its latest,
        // repaired route at once. Without a new one, the last is kept.
        boolean fresh = route(start);
        if (waypoints == null || (!fresh && exhausted(from))) {
            return goal;
        }
        int last = Math.min(waypoint, waypoints.size() - 1);
        while (last + 1 < waypoints.size() && waypoints.get(last + 1).distanceTo(from) <= SECTION_REACH) {
            last++;
        }
        if (last == waypoints.size() - 1 && waypointsComplete) {
            return goal;
        }
        return Goal.near(waypoints.get(last), WAYPOINT_REACHED);
    }

    /** @return whether an incomplete long-range route has been walked to within reach of its end */
    private boolean exhausted(Vec3 from) {
        return !waypointsComplete && waypoints.get(waypoints.size() - 1).distanceTo(from) <= SECTION_REACH / 2d;
    }

    /** Asks the long-range provider for waypoints from {@code start}. @return false if it had nothing new */
    private boolean route(MotionState start) {
        PathProvider provider = longRange.get();
        Route route = provider == null || provider.drives() ? null : provider.plan(goal, start);
        if (route == null || route.isPrecise() || route.getWaypoints().isEmpty()) {
            return false;
        }
        waypoints = new ArrayList<>(route.getWaypoints());
        waypointsComplete = route.isComplete();
        waypoint = nearest(waypoints, start.getPosition());
        return true;
    }

    /** @return the index of the waypoint closest to {@code feet}: where a fresh route picks up */
    private static int nearest(List<Vec3> points, Vec3 feet) {
        int best = 0;
        double bestDistance = Double.POSITIVE_INFINITY;
        for (int i = 0; i < points.size(); i++) {
            double distance = points.get(i).distanceTo(feet);
            if (distance < bestDistance) {
                bestDistance = distance;
                best = i;
            }
        }
        return best;
    }

    private void passWaypoints(Vec3 feet) {
        if (waypoints == null) {
            return;
        }
        while (waypoint < waypoints.size() && reached(waypoints.get(waypoint), feet)) {
            waypoint++;
        }
    }

    private static boolean reached(Vec3 waypoint, Vec3 feet) {
        return waypoint.horizontalDistanceTo(feet) <= WAYPOINT_REACHED && Math.abs(waypoint.getY() - feet.getY()) < 1d;
    }

    // -------------------------------------------------------------- coasts

    /** @return walking on toward {@code toward} for {@code ticks}, or null if that is unsafe or goes nowhere */
    private Coast walkOn(Goal toward, MotionState from, int ticks) {
        Vec3 anchor = toward.anchor();
        if (anchor == null || anchor.horizontalDistanceTo(from.getPosition()) < 1d) {
            return null;
        }
        MovementInput input = MovementInput.forward(from.getPosition().rotationTo(anchor).getYaw());
        List<MovementInput> inputs = new ArrayList<>(ticks);
        List<MotionState> states = new ArrayList<>(ticks);
        MotionState state = from;
        double peak = from.getPosition().getY();
        for (int i = 0; i < ticks; i++) {
            state = simulation.step(state, input);
            if (!rules.allows(state)) {
                return null;
            }
            double y = state.getPosition().getY();
            if (state.isOnGround()) {
                if (peak - y > settings.maxFall()) {
                    return null;
                }
                peak = y;
            } else {
                peak = Math.max(peak, y);
            }
            inputs.add(input);
            states.add(state);
        }
        return new Coast(Route.precise(toward, from, inputs, states, false), false);
    }

    /** @return letting go of the keys until the player stands still */
    private Coast brake(MotionState from) {
        MovementInput input = MovementInput.none(Players.rotation().getYaw());
        List<MovementInput> inputs = new ArrayList<>();
        List<MotionState> states = new ArrayList<>();
        MotionState state = from;
        do {
            state = simulation.step(state, input);
            inputs.add(input);
            states.add(state);
        } while (inputs.size() < MAX_BRAKE && !atRest(state));
        return new Coast(Route.precise(goal, from, inputs, states, false), atRest(state));
    }

    private static boolean atRest(MotionState state) {
        Vec3 velocity = state.getVelocity();
        return state.isOnGround() && Math.hypot(velocity.getX(), velocity.getZ()) < AT_REST;
    }

    // ------------------------------------------------------------- driving

    private void follow(FlowContext c, MovementInput input) {
        c.move(input);
        if (turnHead) {
            float pitch = c.rotations().getRotation().getPitch();
            c.look(RotationRequest.at(Vec2.rotation(input.getYaw(), pitch))
                    .step(settings.turnSpeed())
                    .mode(settings.rotationMode()));
        }
    }

    private Progress measure(Vec3 feet) {
        double remaining = goal.gap(feet).distance();
        if (remaining < bestRemaining - CLOSER_BY) {
            bestRemaining = remaining;
            sinceCloser = 0;
        } else {
            sinceCloser++;
        }
        int stuck = settings.stuckTicks();
        progress = stuck > 0 && sinceCloser >= stuck
                ? Progress.stuck(String.format("no closer to %s in %d ticks (%.1f blocks left at best)",
                goal, sinceCloser, bestRemaining), remaining)
                : Progress.running(remaining);
        return progress;
    }

    // --------------------------------------------------------------- reads

    Goal getGoal() {
        return goal;
    }

    Progress getProgress() {
        return progress;
    }

    TripStats getStats() {
        return stats;
    }

    /** @return the segment being walked, or null */
    Route getSegment() {
        return segment == null ? null : segment.route;
    }

    /** @return the tick of the segment being walked */
    int getSegmentTick() {
        return index;
    }

    /** @return the long-range route's waypoints not yet passed; empty when there is none */
    List<Vec3> getWaypointsAhead() {
        return waypoints == null ? List.of() : waypoints.subList(Math.min(waypoint, waypoints.size()), waypoints.size());
    }

    boolean isTravelling() {
        return goal != null && failure == null && !progress.isArrived();
    }

    // --------------------------------------------------------------- types

    /**
     * @param last      whether it ends at the trip's goal, so nothing follows it
     * @param restAtEnd whether it ends standing still, where waiting keeps the next one valid
     */
    private record Segment(Route route, boolean last, boolean restAtEnd) {
    }

    /** The next segment being planned, and the tick of the current one it takes over at. */
    private record Pending(SegmentPlanner.Plan plan, int spliceAt, Replan why) {
    }

    private record Coast(Route route, boolean atRest) {
    }
}
