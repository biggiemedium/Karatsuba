package dev.px.karatsuba.pathing.test;

import dev.px.core.math.Vec3i;
import dev.px.karatsuba.pathing.DStarLite;
import dev.px.karatsuba.pathing.WalkModel;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.Random;

import static dev.px.core.test.harness.Checks.check;
import static dev.px.core.test.harness.Checks.section;

/**
 * D* Lite against a plain Dijkstra over the same moves: every answer it gives,
 * fresh or repaired, must cost exactly what a search from scratch costs.
 */
final class DStarLiteTests {

    private static final int BUDGET = 1_000_000;

    private DStarLiteTests() {
    }

    static void run() {
        openGround();
        wallWithAGap();
        stepsAndFalls();
        budgetResumes();
        repairsMatchScratch();
        unknownGroundIsRepaired();
        slowCellsAreAvoided();
    }

    private static void openGround() {
        section("D* Lite: open ground");
        GridTerrain grid = GridTerrain.floor(-5, -5, 20, 20);
        WalkModel model = new WalkModel(grid, 3);
        DStarLite search = new DStarLite(model);
        Vec3i start = Vec3i.of(0, 1, 0);
        Vec3i goal = Vec3i.of(10, 1, 5);
        search.reset(start, goal);
        check("finishes", search.compute(BUDGET));
        double expected = 5 * WalkModel.DIAGONAL + 5;
        check("costs the octile distance", near(search.costFromStart(), expected));
        List<Vec3i> path = search.path(100);
        check("path starts at the start", path.get(0).equals(start));
        check("path ends at the goal", path.get(path.size() - 1).equals(goal));
        check("path is walkable", walkable(model, path));
        check("path costs what was promised", near(cost(model, path), expected));
    }

    private static void wallWithAGap() {
        section("D* Lite: wall with a gap");
        GridTerrain grid = GridTerrain.floor(-12, -12, 20, 12);
        for (int z = -10; z <= 10; z++) {
            if (z != 7) {
                grid.pillar(5, z, 3);
            }
        }
        WalkModel model = new WalkModel(grid, 3);
        DStarLite search = new DStarLite(model);
        search.reset(Vec3i.of(0, 1, 0), Vec3i.of(10, 1, 0));
        search.compute(BUDGET);
        List<Vec3i> path = search.path(200);
        check("goes through the gap", path.contains(Vec3i.of(5, 1, 7)));
        check("matches Dijkstra", near(search.costFromStart(), dijkstra(model, Vec3i.of(0, 1, 0), Vec3i.of(10, 1, 0))));
        check("path is walkable", walkable(model, path));
    }

    private static void stepsAndFalls() {
        section("D* Lite: steps up and falls");
        GridTerrain grid = GridTerrain.floor(-3, -3, 12, 3);
        grid.pillar(3, 0, 1);                 // a step of one: climbable
        grid.pillar(8, 0, 2);                 // two high: not from the ground beside it
        WalkModel model = new WalkModel(grid, 3);
        DStarLite search = new DStarLite(model);

        search.reset(Vec3i.of(0, 1, 0), Vec3i.of(3, 2, 0));
        search.compute(BUDGET);
        check("steps up one block", search.costFromStart() < Double.POSITIVE_INFINITY);

        search.reset(Vec3i.of(0, 1, 0), Vec3i.of(8, 3, 0));
        search.compute(BUDGET);
        check("does not climb two", search.costFromStart() == Double.POSITIVE_INFINITY);

        GridTerrain cliff = GridTerrain.floor(-3, -3, 12, 3);
        for (int x = -3; x <= 3; x++) {
            for (int z = -3; z <= 3; z++) {
                cliff.pillar(x, z, 4);       // a ledge, feet at y = 5
            }
        }
        DStarLite three = new DStarLite(new WalkModel(cliff, 4));
        three.reset(Vec3i.of(0, 5, 0), Vec3i.of(8, 1, 0));
        three.compute(BUDGET);
        check("falls four with a max of four", three.costFromStart() < Double.POSITIVE_INFINITY);
        DStarLite two = new DStarLite(new WalkModel(cliff, 3));
        two.reset(Vec3i.of(0, 5, 0), Vec3i.of(8, 1, 0));
        two.compute(BUDGET);
        check("will not fall four with a max of three", two.costFromStart() == Double.POSITIVE_INFINITY);
    }

    private static void budgetResumes() {
        section("D* Lite: a budget resumes where it stopped");
        GridTerrain grid = maze(new Random(7), 40, 0.3d);
        WalkModel model = new WalkModel(grid, 3);
        Vec3i start = Vec3i.of(0, 1, 0);
        Vec3i goal = Vec3i.of(39, 1, 39);
        clear(grid, start);
        clear(grid, goal);
        DStarLite whole = new DStarLite(model);
        whole.reset(start, goal);
        whole.compute(BUDGET);

        DStarLite sliced = new DStarLite(model);
        sliced.reset(start, goal);
        int calls = 1;
        while (!sliced.compute(25)) {
            calls++;
        }
        check("needed several calls", calls > 1);
        check("same cost as in one go", near(sliced.costFromStart(), whole.costFromStart()));
    }

    /** The heart of it: repairs after walking and changing blocks cost what a fresh search costs. */
    private static void repairsMatchScratch() {
        section("D* Lite: repairs match a search from scratch");
        int mismatches = 0;
        int rounds = 0;
        int reachable = 0;
        for (int seed = 1; seed <= 12; seed++) {
            Random random = new Random(seed);
            GridTerrain grid = maze(random, 30, 0.2d);
            WalkModel model = new WalkModel(grid, 3);
            Vec3i start = Vec3i.of(0, 1, 0);
            Vec3i goal = Vec3i.of(29, 1, 29);
            clear(grid, start);
            clear(grid, goal);
            DStarLite search = new DStarLite(model);
            search.reset(start, goal);
            search.compute(BUDGET);
            for (int round = 0; round < 20; round++) {
                rounds++;
                List<Vec3i> path = search.path(400);
                if (path.size() > 3) {
                    start = path.get(Math.min(3, path.size() - 1));
                    search.moveStart(start);
                }
                // Toggle a few blocks near the route, the way the world changes near a player.
                for (int i = 0; i < 4; i++) {
                    Vec3i around = path.isEmpty() ? start : path.get(random.nextInt(path.size()));
                    int x = around.getX() + random.nextInt(7) - 3;
                    int z = around.getZ() + random.nextInt(7) - 3;
                    if ((x == start.getX() && z == start.getZ()) || (x == goal.getX() && z == goal.getZ())) {
                        continue;
                    }
                    int height = random.nextInt(3);
                    for (int y = 1; y <= 2; y++) {
                        grid.set(x, y, z, y <= height);
                    }
                    search.changed(x, 1, z, x, 2, z);
                }
                search.compute(BUDGET);
                double repaired = search.costFromStart();
                double scratch = dijkstra(model, start, goal);
                if (scratch < Double.POSITIVE_INFINITY) {
                    reachable++;
                }
                if (!near(repaired, scratch)) {
                    mismatches++;
                }
                List<Vec3i> route = search.path(400);
                if (scratch < Double.POSITIVE_INFINITY && (!walkable(model, route)
                        || !route.get(route.size() - 1).equals(goal))) {
                    mismatches++;
                }
            }
        }
        check("every repair matched (" + rounds + " rounds, " + reachable + " reachable)", mismatches == 0);
        check("most rounds had a route, so the comparison means something", reachable > rounds / 2);
    }

    private static void unknownGroundIsRepaired() {
        section("D* Lite: unseen ground, then seen");
        GridTerrain grid = GridTerrain.floor(-5, -15, 40, 15);
        for (int z = -14; z <= 12; z++) {
            grid.pillar(20, z, 3);            // a wall the planner cannot see yet
        }
        int[] seenUpTo = {10};
        grid.known((x, z) -> x <= seenUpTo[0]);
        WalkModel model = new WalkModel(grid, 3);
        Vec3i start = Vec3i.of(0, 1, 0);
        Vec3i goal = Vec3i.of(35, 1, 0);
        DStarLite search = new DStarLite(model);
        search.reset(start, goal);
        search.compute(BUDGET);
        check("plans straight through what it has not seen", near(search.costFromStart(), 35d));

        seenUpTo[0] = 40;
        search.changed(11, 0, -15, 40, 4, 15);
        search.compute(BUDGET);
        double scratch = dijkstra(model, start, goal);
        check("once seen, goes round the wall", search.costFromStart() > 35.5d);
        check("and matches Dijkstra", near(search.costFromStart(), scratch));
        check("through the end of the wall", search.path(200).stream().anyMatch(c -> c.getX() == 20 && c.getZ() > 12));
    }

    private static void slowCellsAreAvoided() {
        section("D* Lite: slow cells cost more");
        GridTerrain grid = GridTerrain.floor(-5, -5, 15, 5);
        for (int z = -1; z <= 1; z++) {
            grid.cost(5, 1, z, 1000d);        // lava, say, across the straight line
        }
        WalkModel model = new WalkModel(grid, 3);
        DStarLite search = new DStarLite(model);
        search.reset(Vec3i.of(0, 1, 0), Vec3i.of(10, 1, 0));
        search.compute(BUDGET);
        check("walks round", search.path(100).stream().noneMatch(c -> c.getX() == 5 && Math.abs(c.getZ()) <= 1));
        check("matches Dijkstra", near(search.costFromStart(), dijkstra(model, Vec3i.of(0, 1, 0), Vec3i.of(10, 1, 0))));
    }

    // ------------------------------------------------------------ helpers

    /** A floor with random pillars of one or two blocks. */
    private static GridTerrain maze(Random random, int size, double density) {
        GridTerrain grid = GridTerrain.floor(-1, -1, size, size);
        for (int x = 0; x < size; x++) {
            for (int z = 0; z < size; z++) {
                if (random.nextDouble() < density) {
                    grid.pillar(x, z, 1 + random.nextInt(2));
                }
            }
        }
        return grid;
    }

    private static void clear(GridTerrain grid, Vec3i cell) {
        for (int y = 1; y <= 3; y++) {
            grid.set(cell.getX(), y, cell.getZ(), false);
        }
    }

    /** Forward Dijkstra over the model's own moves: the reference every answer is held to. */
    static double dijkstra(WalkModel model, Vec3i start, Vec3i goal) {
        Map<Long, Double> best = new HashMap<>();
        PriorityQueue<Object[]> open = new PriorityQueue<>((a, b) -> Double.compare((double) a[1], (double) b[1]));
        best.put(start.asLong(), 0d);
        open.add(new Object[]{start, 0d});
        while (!open.isEmpty()) {
            Object[] top = open.poll();
            Vec3i cell = (Vec3i) top[0];
            double cost = (double) top[1];
            if (cost > best.getOrDefault(cell.asLong(), Double.POSITIVE_INFINITY)) {
                continue;
            }
            if (cell.equals(goal)) {
                return cost;
            }
            for (Vec3i next : model.successors(cell)) {
                if (Math.abs(next.getX()) > 60 || Math.abs(next.getZ()) > 60) {
                    continue;
                }
                double through = cost + model.cost(cell, next);
                if (through < best.getOrDefault(next.asLong(), Double.POSITIVE_INFINITY)) {
                    best.put(next.asLong(), through);
                    open.add(new Object[]{next, through});
                }
            }
        }
        return Double.POSITIVE_INFINITY;
    }

    private static boolean walkable(WalkModel model, List<Vec3i> path) {
        for (int i = 1; i < path.size(); i++) {
            if (model.cost(path.get(i - 1), path.get(i)) == Double.POSITIVE_INFINITY) {
                return false;
            }
        }
        return !path.isEmpty();
    }

    private static double cost(WalkModel model, List<Vec3i> path) {
        double total = 0d;
        for (int i = 1; i < path.size(); i++) {
            total += model.cost(path.get(i - 1), path.get(i));
        }
        return total;
    }

    private static boolean near(double a, double b) {
        return a == b || Math.abs(a - b) < 1e-6d;
    }
}
