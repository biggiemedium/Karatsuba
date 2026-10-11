package dev.px.karatsuba.client.automation.navigation;

import dev.px.core.Core;
import dev.px.core.movement.simulation.MotionState;
import dev.px.core.movement.simulation.SimulationService;
import dev.px.core.navigation.Goal;
import dev.px.core.navigation.Route;
import dev.px.core.util.math.PhysicsProfile;
import dev.px.karatsuba.adapter.game.player.Players;
import dev.px.karatsuba.adapter.world.WorldSnapshot;
import dev.px.karatsuba.client.settings.automation.AutomationSettings;
import dev.px.navigation.plan.LocalPlanner;
import dev.px.navigation.plan.PlanStats;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

/**
 * Plans route segments: the local planner's exact keys from one state toward one
 * goal, on a worker thread so a slow plan costs the game nothing.
 *
 * <pre>{@code
 * SegmentPlanner.Plan next = segments.submit(goal, stateWhereThisSegmentEnds);   // game thread
 * ...
 * if (next.isDone()) {
 *     Route route = next.result();                                             // game thread
 * }
 * }</pre>
 *
 * <p>The {@link Driver} asks for the next segment as soon as it starts walking
 * the current one, planned from the state the current one ends in, so the next is
 * normally ready before it is needed. That is what keeps movement continuous.
 *
 * <h2>Thread safety</h2>
 * The worker reads only: a {@link WorldSnapshot} of the blocks around the start,
 * copied on the game thread as the plan is submitted; a simulation and route rules
 * of its own over that copy; values read from the settings before it starts; and
 * the goal. Goals that follow something read it through a supplier, and the
 * client's suppliers hand out immutable snapshots ({@code GameEntity}); a goal
 * built over live game state would not be safe here.
 *
 * <p>With {@code Background Planning} off, or out of a world, a plan is made on the
 * spot and comes back already done.
 */
public final class SegmentPlanner {

    /** Blocks copied beyond the planner's range, for the player's own size and a step's reach. */
    private static final int SNAPSHOT_MARGIN = 3;

    private static final int MIN_LEAD = 2;
    private static final int MAX_LEAD = 10;

    private final LocalPlanner sync;
    private final SimulationService simulation;
    private final AutomationSettings settings;
    private final ExecutorService worker = Executors.newSingleThreadExecutor(task -> {
        Thread thread = new Thread(task, "Karatsuba Planner");
        thread.setDaemon(true);
        thread.setPriority(Thread.NORM_PRIORITY - 1);
        return thread;
    });

    /** How many ticks plans have lately taken. */
    private double planTicks = 3d;

    private PlanStats lastStats;
    private double lastMillis;
    private int plans;

    /**
     * @param sync       the game-thread planner, for when background planning is off and
     *                   if a plan on the worker throws
     * @param simulation the live simulation, whose profile each plan copies
     */
    SegmentPlanner(LocalPlanner sync, SimulationService simulation, AutomationSettings settings) {
        this.sync = sync;
        this.simulation = simulation;
        this.settings = settings;
    }

    /** @return whether plans go to the worker: on, and in a world */
    boolean inBackground() {
        return settings.backgroundPlanning() && Players.present();
    }

    /**
     * Plans from {@code start} toward {@code goal}. <b>Game thread.</b>
     *
     * @return the plan, running on the worker, or already done when it was made here
     */
    Plan submit(Goal goal, MotionState start) {
        WorldSnapshot world = inBackground()
                ? WorldSnapshot.around(start.getPosition(), settings.range() + SNAPSHOT_MARGIN) : null;
        if (world == null) {
            return new Plan(goal, start, CompletableFuture.completedFuture(here(goal, start)));
        }
        // Everything the worker needs, read here on the game thread.
        PhysicsProfile profile = simulation.getProfile();
        int range = settings.range();
        int budget = settings.searchBudget();
        double maxDrop = settings.maxFall();
        boolean sprint = settings.sprint() && Players.canSprint();
        Future<Done> future = worker.submit(() -> {
            long began = System.nanoTime();
            SimulationService copy = new SimulationService(Core.log(), Core.bus());
            copy.setProfile(profile);
            copy.setCollisionSpace(world);
            RouteRules own = new RouteRules(settings, world::hurts, world::isLiquid);
            own.newTick(start.getPosition());
            LocalPlanner planner = LocalPlanner.builder()
                    .simulation(copy)
                    .range(() -> range)
                    .maxStates(() -> budget)
                    .maxDrop(() -> maxDrop)
                    .canSprint(() -> sprint)
                    .allowed(own::allows)
                    .build();
            Route route = planner.plan(goal, start);
            return new Done(route, planner.getLastStats(), System.nanoTime() - began);
        });
        return new Plan(goal, start, future);
    }

    /** @return a plan made on the game thread */
    private Done here(Goal goal, MotionState start) {
        long began = System.nanoTime();
        Route route = sync.plan(goal, start);
        return new Done(route, sync.getLastStats(), System.nanoTime() - began);
    }

    /** @return ticks a plan is likely to take, with one to spare: how far ahead to ask */
    int lead() {
        return Math.max(MIN_LEAD, Math.min(MAX_LEAD, (int) Math.ceil(planTicks) + 1));
    }

    // --------------------------------------------------------------- reads

    /** @return the last finished plan's numbers, or null before one */
    public PlanStats getLastStats() {
        return lastStats;
    }

    /** @return how long the last finished plan took, in milliseconds */
    public double getLastMillis() {
        return lastMillis;
    }

    /** @return plans finished and taken since the game started */
    public int getPlans() {
        return plans;
    }

    /** @return ticks ahead plans are being asked for */
    public int getLead() {
        return lead();
    }

    // --------------------------------------------------------------- types

    /** A finished plan: the route, or null for none, and what it cost. */
    private record Done(Route route, PlanStats stats, long nanos) {
    }

    /** One segment being planned. */
    final class Plan {

        private final Goal goal;
        private final MotionState start;
        private final Future<Done> future;
        private Done done;

        private Plan(Goal goal, MotionState start, Future<Done> future) {
            this.goal = goal;
            this.start = start;
            this.future = future;
        }

        Goal getGoal() {
            return goal;
        }

        /** @return the state this plan starts from */
        MotionState getStart() {
            return start;
        }

        boolean isDone() {
            return future.isDone();
        }

        /**
         * @return the route, or null when the planner found none. Once done; game thread.
         *         A plan that threw on the worker is made again here instead.
         */
        Route result() {
            if (done == null) {
                try {
                    done = future.get();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    done = here(goal, start);
                } catch (ExecutionException e) {
                    Core.log().warn("A background plan failed, planning on the game thread instead: " + e.getCause());
                    done = here(goal, start);
                }
                plans++;
                lastStats = done.stats();
                lastMillis = done.nanos() / 1e6d;
                planTicks = planTicks * 0.7d + (done.nanos() / 50_000_000d) * 0.3d;
            }
            return done.route();
        }

        /** @return why the planner found nothing, once done */
        String reason() {
            return done == null || done.stats() == null ? "no route" : done.stats().getReason();
        }

        void cancel() {
            future.cancel(false);
        }
    }
}
