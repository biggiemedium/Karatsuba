package dev.px.karatsuba.client.automation.navigation;

import dev.px.core.flow.Condition;
import dev.px.core.flow.FlowContext;
import dev.px.core.math.Vec2;
import dev.px.core.math.Vec3i;
import dev.px.core.movement.rotation.RotationRequest;
import dev.px.core.movement.simulation.SimulationService;
import dev.px.core.navigation.Goal;
import dev.px.core.navigation.PathProvider;
import dev.px.karatsuba.adapter.game.player.Players;
import dev.px.karatsuba.adapter.movement.MotionBridge;
import dev.px.karatsuba.client.settings.automation.AutomationSettings;
import dev.px.navigation.Navigator;
import dev.px.navigation.Travel;
import dev.px.navigation.plan.LocalPlanner;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.WeakHashMap;
import java.util.function.Function;

/**
 * Getting the player somewhere: the navigation library, wired to this client.
 *
 * <p>Tasks never build a planner or a navigator. They ask this for a travel step
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
 * <h2>Who plans</h2>
 * <ul>
 *   <li><b>Now:</b> the library's {@link LocalPlanner} plans every stretch
 *       precisely, key for key, each move played through Core's simulation. A
 *       goal beyond its range is reached a plan at a time: it walks the closest
 *       partial route it found, then plans again from there.</li>
 *   <li><b>With a long-range provider</b> ({@link #setLongRange}): that provider
 *       finds the way and the local planner walks it a stretch at a time, if it
 *       only plans; or it drives the player itself, if it drives. This is the seam
 *       Baritone plugs into. Nothing else changes for a task.</li>
 * </ul>
 *
 * <p><b>Game thread only.</b>
 */
public final class Navigation {

    /** Blocks kept inside the player's reach when walking up to something. */
    private static final double REACH_MARGIN = 1.5d;

    private final AutomationSettings settings;
    private final RouteRules rules;
    private final LocalPlanner planner;

    /** Every trip made, for drawing the ones under way. Weak: a finished flow's trips go with it. */
    private final Set<Travel.Trip> trips = Collections.newSetFromMap(new WeakHashMap<>());

    /** Close work beside something else that has the head: the local planner only, keys only. */
    private final Travel alongside;

    private PathProvider longRange;
    private Travel travel;

    public Navigation(SimulationService simulation, AutomationSettings settings) {
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
        this.travel = travel();
        this.alongside = Travel.builder()
                .provider(planner)
                .player(MotionBridge::currentState)
                .turnHead(false)
                .navigator(navigator -> navigator.stuckTicks(settings::stuckTicks))
                .build();
    }

    // -------------------------------------------------------------- steps

    /** @return a step that walks to {@code goal}, finishing once there */
    public Travel.Trip to(Goal goal) {
        return remember(travel.to(goal));
    }

    /** @return a step that walks to the goal its flow's keys give, asked each time it starts */
    public Travel.Trip to(Function<FlowContext, Goal> goal) {
        return remember(travel.to(goal));
    }

    /**
     * @return a step that walks until {@code block} is within comfortable reach,
     *         a little inside the player's own, so a click or a break at the
     *         end does not depend on the last centimetre; done at once if it is
     */
    public Travel.Trip toReach(Vec3i block) {
        return to(Goal.near(block.center(), Math.max(1d, Players.blockReach() - REACH_MARGIN)));
    }

    /**
     * @return a step that walks to {@code goal} holding only the keys, leaving the
     *         head to a step running beside it: chasing someone while aiming at
     *         them. Close range only, by the local planner; the keys are corrected
     *         to wherever the head faces, so keep the head roughly the way it goes
     */
    public Travel.Trip alongside(Goal goal) {
        return remember(alongside.to(goal));
    }

    /** @return a condition that holds while the player's feet are in {@code goal}, for {@code ensures} */
    public Condition at(Goal goal) {
        return travel.at(goal);
    }

    // ---------------------------------------------------------- providers

    /**
     * Hands long distances to another pathfinder, or back to the local planner
     * with null. Steps made afterwards use it; steps already made keep theirs.
     */
    public void setLongRange(PathProvider provider) {
        this.longRange = provider;
        this.travel = travel();
    }

    /** @return the long-range pathfinder, or null while the local planner plans everything */
    public PathProvider getLongRange() {
        return longRange;
    }

    /** @return the planner every stretch is walked by, for its last plan's stats */
    public LocalPlanner getPlanner() {
        return planner;
    }

    // -------------------------------------------------------------- state

    /** @return the trips under way now, for drawing their routes or reading their progress */
    public List<Travel.Trip> getActiveTrips() {
        List<Travel.Trip> active = new ArrayList<>(1);
        for (Travel.Trip trip : trips) {
            Navigator navigator = trip.getNavigator();
            if (navigator != null && navigator.isTravelling()) {
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

    // ----------------------------------------------------------- building

    private Travel.Trip remember(Travel.Trip trip) {
        trips.add(trip);
        return trip;
    }

    /**
     * The step factory, for the providers as they are now.
     *
     * <p>The turn is read as each trip's navigator is built, on its first start:
     * a change to the rotation mode or speed applies to the next trip.
     */
    private Travel travel() {
        PathProvider provider = longRange != null ? longRange : planner;
        boolean stitched = longRange != null && !longRange.drives();
        return Travel.builder()
                .provider(provider)
                .player(MotionBridge::currentState)
                .failWhenStuck(settings::giveUpWhenStuck)
                .navigator(navigator -> {
                    navigator.stuckTicks(settings::stuckTicks)
                            .turn(RotationRequest.at(Vec2.ZERO)
                                    .step(settings.turnSpeed())
                                    .mode(settings.rotationMode()));
                    if (stitched) {
                        navigator.local(planner);
                    }
                })
                .build();
    }
}
