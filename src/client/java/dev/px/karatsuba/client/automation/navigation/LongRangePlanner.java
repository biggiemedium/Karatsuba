package dev.px.karatsuba.client.automation.navigation;

import dev.px.core.math.Vec3;
import dev.px.core.math.Vec3i;
import dev.px.core.movement.simulation.MotionState;
import dev.px.core.navigation.Goal;
import dev.px.core.navigation.PathProvider;
import dev.px.core.navigation.Route;
import dev.px.karatsuba.adapter.world.ChunkTerrain;
import dev.px.karatsuba.client.settings.automation.AutomationSettings;
import dev.px.karatsuba.pathing.DStarLite;
import dev.px.karatsuba.pathing.WalkModel;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Long range: D* Lite over the chunks the client has seen, on a thread of its
 * own, kept repaired as the player walks, chunks load and blocks change.
 *
 * <p>The {@link Driver} asks for a route each time it plans a segment, and gets
 * whatever the search last published at once: this never makes the game wait.
 * Behind that, the search
 *
 * <ul>
 *   <li>is started when the target changes, and resumed across several worker
 *       turns if {@code Long Range Budget} runs out before it finishes;</li>
 *   <li>follows the player ({@link DStarLite#moveStart}), which costs nothing
 *       until something changes;</li>
 *   <li>is told of every chunk that loads and every block that changes
 *       ({@link ChunkTerrain}), and repairs the route from there instead of
 *       searching again, publishing the repaired route.</li>
 * </ul>
 *
 * <p>Ground the client has not loaded is assumed walkable and level, so the first
 * route heads straight through it and is bent round what is found as it loads.
 *
 * <h2>The horizon</h2>
 * A goal thousands of blocks off is not searched toward directly: the search
 * aims at a point {@value #HORIZON} blocks along the way, kept until the player
 * is within {@value #RETARGET_WITHIN} of it. The same idea as Baritone's
 * segments, and what keeps the search's memory bounded. A goal that moves (a
 * player being followed) is searched for again once it has moved
 * {@value #GOAL_MOVED} blocks; D* Lite is at its best for a goal that stays put.
 */
final class LongRangePlanner implements PathProvider {

    /** Blocks: the furthest a single search aims. */
    static final int HORIZON = 256;
    /** Blocks: within this of the horizon, aim a new one further on. */
    static final int RETARGET_WITHIN = 96;
    /** Blocks a goal may move before the search starts again. */
    static final double GOAL_MOVED = 4d;
    /** Cells of route published at most. */
    private static final int MAX_PATH = 600;
    /** Cells the search may remember before it starts again: its memory. */
    private static final int MAX_NODES = 400_000;

    private final AutomationSettings settings;
    private final ExecutorService worker = Executors.newSingleThreadExecutor(task -> {
        Thread thread = new Thread(task, "Karatsuba Long Range");
        thread.setDaemon(true);
        thread.setPriority(Thread.NORM_PRIORITY - 1);
        return thread;
    });
    private final ChunkTerrain terrain;

    // ---- game thread ------------------------------------------------------------

    private Vec3i target;
    private volatile Vec3i wantedTarget;
    private volatile Vec3i wantedStart;
    private volatile int wantedFall;
    private volatile int wantedBudget;
    private volatile Published published;

    // ---- worker thread ----------------------------------------------------------

    private DStarLite search;
    private Vec3i searchTarget;
    private int searchFall = -1;
    private boolean scheduled;

    // ---- read anywhere ----------------------------------------------------------

    private volatile int computes;
    private volatile int lastExpansions;
    private volatile double lastMillis;
    private volatile int nodes;

    LongRangePlanner(AutomationSettings settings) {
        this.settings = settings;
        this.terrain = ChunkTerrain.start(worker, new ChunkTerrain.Listener() {
            @Override
            public void changed(int minX, int minY, int minZ, int maxX, int maxY, int maxZ) {
                if (search != null) {
                    search.changed(minX, minY, minZ, maxX, maxY, maxZ);
                    schedule();
                }
            }

            @Override
            public void cleared() {
                search = null;
                searchTarget = null;
                published = null;
            }
        });
    }

    /**
     * @return the latest route toward {@code goal} as waypoints, or null while
     *         there is none yet, the goal has no point, or {@code Long Range} is off.
     *         Game thread; never waits
     */
    @Override
    public Route plan(Goal goal, MotionState from) {
        Vec3 anchor = goal.anchor();
        if (!settings.longRange() || anchor == null) {
            return null;
        }
        Vec3i feet = cellOf(from.getPosition());
        Vec3i goalCell = cellOf(anchor);
        target = aim(goalCell, feet);
        wantedTarget = target;
        wantedStart = feet;
        wantedFall = settings.maxFall();
        wantedBudget = settings.longRangeBudget();
        worker.execute(this::schedule);

        Published latest = published;
        if (latest == null || !latest.target.equals(target) || latest.waypoints.isEmpty()) {
            return null;
        }
        return Route.coarse(goal, latest.waypoints, latest.complete && target.equals(goalCell));
    }

    /** @return the cell to search toward: the goal, or a horizon on the way to it */
    private Vec3i aim(Vec3i goal, Vec3i feet) {
        if (horizontal(feet, goal) <= HORIZON) {
            boolean keep = target != null && distance(target, goal) <= GOAL_MOVED;
            return keep ? target : goal;
        }
        boolean keep = target != null && !target.equals(goal)
                && horizontal(feet, target) > RETARGET_WITHIN
                && horizontal(target, goal) < horizontal(feet, goal);
        if (keep) {
            return target;
        }
        double dx = goal.getX() - feet.getX();
        double dz = goal.getZ() - feet.getZ();
        double length = Math.hypot(dx, dz);
        return Vec3i.of(feet.getX() + (int) Math.round(dx / length * HORIZON), feet.getY(),
                feet.getZ() + (int) Math.round(dz / length * HORIZON));
    }

    // -------------------------------------------------------------- worker

    /** Queues a turn of the search, once. Worker thread. */
    private void schedule() {
        if (!scheduled) {
            scheduled = true;
            worker.execute(this::turn);
        }
    }

    /** One turn: follow the player, start again if the target changed, search within the budget. */
    private void turn() {
        scheduled = false;
        Vec3i aimAt = wantedTarget;
        Vec3i feet = wantedStart;
        if (aimAt == null || feet == null) {
            return;
        }
        int fall = wantedFall;
        if (search == null || fall != searchFall || !aimAt.equals(searchTarget) || search.size() > MAX_NODES) {
            WalkModel model = new WalkModel(terrain, fall);
            search = new DStarLite(model);
            search.reset(stand(model, feet), aimAt);
            searchTarget = aimAt;
            searchFall = fall;
        } else {
            search.moveStart(stand(new WalkModel(terrain, fall), feet));
        }
        long began = System.nanoTime();
        boolean finished = search.compute(Math.max(1, wantedBudget));
        lastMillis = (System.nanoTime() - began) / 1e6d;
        lastExpansions = search.getLastExpansions();
        nodes = search.size();
        computes++;
        if (finished) {
            publish();
        } else {
            schedule();
        }
    }

    private void publish() {
        List<Vec3i> cells = search.path(MAX_PATH);
        List<Vec3> waypoints = new ArrayList<>(Math.max(0, cells.size() - 1));
        for (int i = 1; i < cells.size(); i++) {
            Vec3i cell = cells.get(i);
            waypoints.add(Vec3.of(cell.getX() + 0.5d, cell.getY(), cell.getZ() + 0.5d));
        }
        boolean complete = !cells.isEmpty() && cells.get(cells.size() - 1).equals(search.getGoal());
        published = new Published(searchTarget, Collections.unmodifiableList(waypoints), complete);
    }

    /** @return a cell the feet can stand in at or just below {@code feet}: mid-jump, the ground under it */
    private static Vec3i stand(WalkModel model, Vec3i feet) {
        for (int down = 0; down <= 3; down++) {
            if (model.isVertex(feet.getX(), feet.getY() - down, feet.getZ())) {
                return Vec3i.of(feet.getX(), feet.getY() - down, feet.getZ());
            }
        }
        return feet;
    }

    // --------------------------------------------------------------- reads

    /** @return searches and repairs run */
    int getComputes() {
        return computes;
    }

    /** @return cells the last turn expanded */
    int getLastExpansions() {
        return lastExpansions;
    }

    /** @return how long the last turn took on the worker, in milliseconds */
    double getLastMillis() {
        return lastMillis;
    }

    /** @return cells the search remembers */
    int getNodes() {
        return nodes;
    }

    // ------------------------------------------------------------- helpers

    private static Vec3i cellOf(Vec3 point) {
        return Vec3i.of((int) Math.floor(point.getX()), (int) Math.floor(point.getY()), (int) Math.floor(point.getZ()));
    }

    private static double horizontal(Vec3i a, Vec3i b) {
        return Math.hypot(a.getX() - b.getX(), a.getZ() - b.getZ());
    }

    private static double distance(Vec3i a, Vec3i b) {
        double dy = a.getY() - b.getY();
        return Math.sqrt(Math.pow(horizontal(a, b), 2) + dy * dy);
    }

    /** A route the search finished, for the target it was searching toward. */
    private record Published(Vec3i target, List<Vec3> waypoints, boolean complete) {
    }
}
