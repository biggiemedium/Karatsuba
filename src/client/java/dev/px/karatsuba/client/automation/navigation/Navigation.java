package dev.px.karatsuba.client.automation.navigation;

import dev.px.core.flow.Condition;
import dev.px.core.flow.FlowContext;
import dev.px.core.math.Vec3i;
import dev.px.core.movement.simulation.MotionState;
import dev.px.core.movement.simulation.SimulationService;
import dev.px.core.navigation.Goal;
import dev.px.core.navigation.PathProvider;
import dev.px.karatsuba.adapter.game.player.Players;
import dev.px.karatsuba.adapter.movement.MotionBridge;
import dev.px.karatsuba.client.settings.automation.AutomationSettings;
import dev.px.navigation.plan.LocalPlanner;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.WeakHashMap;
import java.util.function.Function;

/**
 * Getting the player somewhere.
 *
 * <p>Tasks never build a planner or a driver. They ask this for a travel step
 * &mdash; {@link #to(Goal)} &mdash; and put it in their flow like any other step:
 *
 * <pre>{@code
 * Step there = navigation.to(Goal.block(x, y, z));
 * Step chase = Flow.loop(navigation.to(Goal.near(target, 3)));
 * }</pre>
 *
 * <p>Every step it makes plans and walks under {@link AutomationSettings}, read
 * live: how far to fall, what to keep out of, whether to sprint, how the head
 * turns, how long before counting as stuck. The player's state comes from the
 * adapter's {@link MotionBridge}, the world from Core's simulation, and the keys
 * and the head go out through Core's controls and rotations at the flow's
 * priority &mdash; which the adapter writes, Grim-consistently.
 *
 * <h2>One algorithm per job</h2>
 * <ul>
 *   <li><b>Short range:</b> the library's {@link LocalPlanner}, key for key with
 *       every move played through Core's simulation, run on a worker by the
 *       {@link SegmentPlanner}.</li>
 *   <li><b>Walking it:</b> each {@link Trip}'s {@link Driver}, which asks for the
 *       next segment while the current one is walked and splices it in, so
 *       movement does not stop between plans.</li>
 *   <li><b>Long range:</b> the {@link LongRangePlanner}, D* Lite over the chunks
 *       seen, repaired as the player walks and the world changes, on its own
 *       thread; or whatever {@link #setLongRange} was given instead. The driver
 *       aims each segment along its waypoints.</li>
 * </ul>
 *
 * <p><b>Game thread only.</b>
 */
public final class Navigation {

    /** Blocks kept inside the player's reach when walking up to something. */
    private static final double REACH_MARGIN = 1.5d;

    private final SimulationService simulation;
    private final AutomationSettings settings;
    private final RouteRules rules;
    private final LocalPlanner planner;
    private final SegmentPlanner segments;
    private final LongRangePlanner far;

    /** Every trip made, for drawing the ones under way. Weak: a finished flow's trips go with it. */
    private final Set<Trip> trips = Collections.newSetFromMap(new WeakHashMap<>());

    private PathProvider longRange;

    public Navigation(SimulationService simulation, AutomationSettings settings) {
        this.simulation = simulation;
        this.settings = settings;
        this.rules = new RouteRules(settings);
        this.planner = LocalPlanner.builder()
                .simulation(simulation)
                .range(settings::range)
                .maxStates(settings::searchBudget)
                .maxDrop(() -> (double) settings.maxFall())
                .canSprint(() -> settings.sprint() && Players.canSprint())
                .allowed(rules::allows)
                .build();
        this.segments = new SegmentPlanner(planner, simulation, settings);
        this.far = new LongRangePlanner(settings);
    }

    // -------------------------------------------------------------- steps

    /** @return a step that walks to {@code goal}, finishing once there */
    public Trip to(Goal goal) {
        return to(c -> goal, "travel to " + goal);
    }

    /** @return a step that walks to the goal its flow's keys give, asked each time it starts */
    public Trip to(Function<FlowContext, Goal> goal) {
        return to(goal, "travel");
    }

    /**
     * @return a step that walks until {@code block} is within comfortable reach,
     *         a little inside the player's own, so a click or a break at the
     *         end does not depend on the last centimetre; done at once if it is
     */
    public Trip toReach(Vec3i block) {
        return to(Goal.near(block.center(), Math.max(1d, Players.blockReach() - REACH_MARGIN)));
    }

    /**
     * @return a step that walks to {@code goal} holding only the keys, leaving the
     *         head to a step running beside it: chasing someone while aiming at
     *         them. Close range only, no long-range route; the keys are corrected
     *         to wherever the head faces, so keep the head roughly the way it goes
     */
    public Trip alongside(Goal goal) {
        return remember(new Trip(c -> goal, "alongside " + goal, driver(false, false), false,
                settings::giveUpWhenStuck));
    }

    /** @return a condition that holds while the player's feet are in {@code goal}, for {@code ensures} */
    public Condition at(Goal goal) {
        return c -> {
            MotionState state = MotionBridge.currentState();
            return state != null && goal.isMet(state.getPosition());
        };
    }

    private Trip to(Function<FlowContext, Goal> goal, String name) {
        return remember(new Trip(goal, name, driver(true, true), true, settings::giveUpWhenStuck));
    }

    private Driver driver(boolean turnHead, boolean farGoals) {
        return new Driver(segments, this::longRangeProvider, simulation, rules, settings, turnHead, farGoals);
    }

    private Trip remember(Trip trip) {
        trips.add(trip);
        return trip;
    }

    // ---------------------------------------------------------- providers

    /**
     * Hands long distances to another pathfinder, or back to the D* Lite planner
     * with null. A provider that only plans gives waypoints the driver aims
     * along; one that drives is not supported by the driver yet.
     */
    public void setLongRange(PathProvider provider) {
        this.longRange = provider;
    }

    /** @return the long-range pathfinder set, or null while the D* Lite planner has far goals */
    public PathProvider getLongRange() {
        return longRange;
    }

    private PathProvider longRangeProvider() {
        return longRange != null ? longRange : far;
    }

    /** @return the long-range searches made so far, and the last one's cost */
    public CoarseStats getCoarseStats() {
        return new CoarseStats(far.getComputes(), far.getLastExpansions(), far.getLastMillis(), far.getNodes());
    }

    /** @return the segment planner every trip plans with, for its numbers */
    public SegmentPlanner getSegments() {
        return segments;
    }

    /** @return the game-thread planner, for its last plan's stats when background planning is off */
    public LocalPlanner getPlanner() {
        return planner;
    }

    // -------------------------------------------------------------- state

    /** @return the trips under way now, for drawing their routes or reading their progress */
    public List<Trip> getActiveTrips() {
        List<Trip> active = new ArrayList<>(1);
        for (Trip trip : trips) {
            if (trip.isTravelling()) {
                active.add(trip);
            }
        }
        return active;
    }

    /**
     * A new tick: blocks may have changed, so what the route rules knew is
     * dropped, and where the player stands is noted as never blocking a way out.
     */
    public void newTick() {
        rules.newTick(Players.present() ? Players.position() : null);
    }

    /**
     * The long-range planner's work, all of it on its own thread.
     *
     * @param plans        search turns run since the game started: first searches and repairs
     * @param lastExpanded cells the last turn expanded
     * @param lastMillis   how long the last turn took, on the worker
     * @param nodes        cells the search remembers
     */
    public record CoarseStats(int plans, int lastExpanded, double lastMillis, int nodes) {
    }
}
