package dev.px.karatsuba.pathing;

import dev.px.core.math.Vec3i;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * D* Lite (Koenig and Likhachev, 2002), the optimized version: a shortest-path
 * search that keeps its work, so moving along the route and blocks changing near
 * it cost a repair, not a new search.
 *
 * <pre>{@code
 * DStarLite search = new DStarLite(new WalkModel(terrain, 3));
 * search.reset(feet, goal);
 * search.compute(20_000);                    // may take several calls: it keeps its place
 * List<Vec3i> route = search.path(256);
 *
 * search.moveStart(newFeet);                 // walked along it
 * search.changed(minX, minY, minZ, maxX, maxY, maxZ);   // blocks changed, a chunk loaded
 * search.compute(20_000);                    // repairs only what changed
 * }</pre>
 *
 * <h2>How it works</h2>
 * The search runs backwards, from the goal to the player, so the cost from any
 * cell to the goal ({@code g}) stays meaningful as the player moves. Each cell
 * also keeps {@code rhs}, the best cost through its successors; a cell whose two
 * disagree is queued, keyed by
 * {@code [min(g, rhs) + h(start, s) + km; min(g, rhs)]}. {@code km} grows by how far
 * the player has moved each time changes are taken in, which keeps the keys
 * already queued valid lower bounds without re-sorting the queue: the trick that
 * makes it "lite". A cell whose costs went down is settled ({@code g = rhs}); one
 * whose costs went up is reset ({@code g = ∞}) and its predecessors look again.
 *
 * <h2>Where it is worth it</h2>
 * Long trips to one fixed goal through terrain only partly seen: unloaded chunks
 * are assumed walkable ({@link Terrain#isKnown}), and as they load the route is
 * repaired near where the player is, which is where changes almost always are.
 * For short searches, or a goal that keeps moving, a fresh A* is as good or
 * better; the research is consistent on that, and {@link #reset} is the honest
 * answer to a new goal.
 *
 * <h2>Budget</h2>
 * {@link #compute(int)} stops after so many expansions and resumes from the same
 * place next call, so a long first search can be spread over ticks or run on a
 * worker without ever holding anything up.
 *
 * <p>Not thread-safe: one thread at a time, and the terrain must not change under
 * a {@code compute} except as reported through {@link #changed}.
 */
public final class DStarLite {

    private static final double INFINITY = Double.POSITIVE_INFINITY;
    private static final double EPSILON = 1e-9d;

    /** How far a goal floating in the air or buried is looked for a cell to stand in, down then up. */
    private static final int GOAL_SNAP_DOWN = 8;
    private static final int GOAL_SNAP_UP = 4;

    private final WalkModel model;
    private final Map<Long, Node> nodes = new HashMap<>();
    private final Heap open = new Heap();

    private Vec3i start;
    private Vec3i last;
    private Vec3i goal;
    private double km;

    private long expansions;
    private int lastExpansions;

    public DStarLite(WalkModel model) {
        this.model = model;
    }

    // ----------------------------------------------------------- the search

    /**
     * Starts afresh: forgets everything and searches from {@code goal} back to
     * {@code start}. A goal that cannot be stood in is moved to the nearest cell
     * in its column that can.
     */
    public void reset(Vec3i start, Vec3i goal) {
        nodes.clear();
        open.clear();
        km = 0d;
        this.start = start;
        this.last = start;
        this.goal = snap(goal);
        Node target = node(this.goal);
        target.rhs = 0d;
        open.push(target, key(target));
        expansions = 0L;
    }

    /**
     * Searches until the cost from the start is known, or {@code budget}
     * expansions have been spent.
     *
     * @return whether it finished: false means call again to carry on
     */
    public boolean compute(int budget) {
        if (start == null) {
            throw new IllegalStateException("reset(start, goal) first");
        }
        int spent = 0;
        Node from = node(start);
        while (!open.isEmpty() && (less(open.peekKey(), key(from)) || from.rhs > from.g)) {
            if (spent >= budget) {
                lastExpansions = spent;
                return false;
            }
            spent++;
            Node u = open.peek();
            double[] old = open.peekKey();
            double[] fresh = key(u);
            if (less(old, fresh)) {
                open.update(u, fresh);
            } else if (u.g > u.rhs) {
                u.g = u.rhs;
                open.remove(u);
                for (Vec3i p : model.predecessors(u.cell)) {
                    Node s = node(p);
                    if (s != nodeOf(goal)) {
                        s.rhs = Math.min(s.rhs, model.cost(p, u.cell) + u.g);
                    }
                    update(s);
                }
            } else {
                double before = u.g;
                u.g = INFINITY;
                for (Vec3i p : model.predecessors(u.cell)) {
                    Node s = node(p);
                    if (s != nodeOf(goal) && same(s.rhs, model.cost(p, u.cell) + before)) {
                        s.rhs = bestThroughSuccessors(s);
                    }
                    update(s);
                }
                update(u);
            }
        }
        expansions += spent;
        lastExpansions = spent;
        return true;
    }

    /**
     * The player has moved to {@code start}. Cheap: nothing is searched until
     * {@link #compute}, and nothing at all unless something has also changed.
     */
    public void moveStart(Vec3i start) {
        this.start = start;
    }

    /**
     * Blocks changed in this box (inclusive), or a chunk there loaded: the moves
     * near it are looked at again, and the next {@link #compute} repairs the
     * route from them.
     */
    public void changed(int minX, int minY, int minZ, int maxX, int maxY, int maxZ) {
        km += WalkModel.heuristic(last, start);
        last = start;

        // Whether a move exists can depend on cells up to a column away from
        // either end, from a fall's height above to the floor below; one more
        // column round catches moves into a cell whose cost-to-go is known.
        int reach = 2;
        int span = model.getMaxFall() + 3;
        List<Node> near = new ArrayList<>();
        for (Node n : nodes.values()) {
            Vec3i c = n.cell;
            if (c.getX() >= minX - reach && c.getX() <= maxX + reach
                    && c.getZ() >= minZ - reach && c.getZ() <= maxZ + reach
                    && c.getY() >= minY - span && c.getY() <= maxY + span) {
                near.add(n);
            }
        }
        Node target = nodeOf(goal);
        for (Node u : near) {
            if (u != target) {
                u.rhs = bestThroughSuccessors(u);
            }
            update(u);
        }
        // Moves that now exist into a cell already costed, from cells the search
        // never had a reason to look at.
        for (Node v : near) {
            if (v.g == INFINITY) {
                continue;
            }
            for (Vec3i p : model.predecessors(v.cell)) {
                Node s = node(p);
                if (s != target) {
                    double through = model.cost(p, v.cell) + v.g;
                    if (through < s.rhs) {
                        s.rhs = through;
                        update(s);
                    }
                }
            }
        }
    }

    // --------------------------------------------------------------- reads

    /**
     * @return the cells from the start toward the goal, each the cheapest next
     *         move by the costs known, at most {@code max}; empty when no route is
     *         known. Ends at the goal, or short of it with {@code max} reached or
     *         the costs not yet computed beyond
     */
    public List<Vec3i> path(int max) {
        List<Vec3i> route = new ArrayList<>();
        Node at = nodes.get(start.asLong());
        if (at == null || at.g == INFINITY && at.rhs == INFINITY) {
            return route;
        }
        Set<Long> seen = new HashSet<>();
        Vec3i cell = start;
        route.add(cell);
        while (!cell.equals(goal) && route.size() < max && seen.add(cell.asLong())) {
            Vec3i best = null;
            double bestCost = INFINITY;
            for (Vec3i s : model.successors(cell)) {
                Node n = nodes.get(s.asLong());
                if (n == null) {
                    continue;
                }
                double through = model.cost(cell, s) + n.g;
                if (through < bestCost) {
                    bestCost = through;
                    best = s;
                }
            }
            if (best == null || bestCost == INFINITY) {
                break;
            }
            cell = best;
            route.add(cell);
        }
        return Collections.unmodifiableList(route);
    }

    /** @return the cost of the cheapest route from the start, as far as is known; infinite for none */
    public double costFromStart() {
        Node at = nodes.get(start.asLong());
        return at == null ? INFINITY : Math.min(at.g, at.rhs);
    }

    /** @return the goal searched toward, after snapping to a cell that can be stood in */
    public Vec3i getGoal() {
        return goal;
    }

    public Vec3i getStart() {
        return start;
    }

    /** @return cells the search has a cost for, which is its memory */
    public int size() {
        return nodes.size();
    }

    /** @return expansions since the last {@link #reset} */
    public long getExpansions() {
        return expansions;
    }

    /** @return expansions the last {@link #compute} made */
    public int getLastExpansions() {
        return lastExpansions;
    }

    // ------------------------------------------------------------ internals

    private double bestThroughSuccessors(Node s) {
        double best = INFINITY;
        for (Vec3i next : model.successors(s.cell)) {
            Node n = nodes.get(next.asLong());
            if (n != null && n.g != INFINITY) {
                best = Math.min(best, model.cost(s.cell, next) + n.g);
            }
        }
        return best;
    }

    private void update(Node u) {
        boolean inconsistent = !same(u.g, u.rhs);
        if (inconsistent) {
            if (u.index >= 0) {
                open.update(u, key(u));
            } else {
                open.push(u, key(u));
            }
        } else if (u.index >= 0) {
            open.remove(u);
        }
    }

    private double[] key(Node s) {
        double least = Math.min(s.g, s.rhs);
        return new double[]{least + WalkModel.heuristic(start, s.cell) + km, least};
    }

    private static boolean less(double[] a, double[] b) {
        if (a[0] < b[0] - EPSILON) {
            return true;
        }
        return Math.abs(a[0] - b[0]) <= EPSILON && a[1] < b[1] - EPSILON;
    }

    private static boolean same(double a, double b) {
        return a == b || Math.abs(a - b) <= EPSILON;
    }

    private Node node(Vec3i cell) {
        return nodes.computeIfAbsent(cell.asLong(), k -> new Node(cell));
    }

    private Node nodeOf(Vec3i cell) {
        return nodes.get(cell.asLong());
    }

    /** @return the nearest cell in the goal's column that can be stood in, down first; the goal if none */
    private Vec3i snap(Vec3i wanted) {
        int x = wanted.getX();
        int z = wanted.getZ();
        if (model.isVertex(x, wanted.getY(), z)) {
            return wanted;
        }
        for (int d = 1; d <= GOAL_SNAP_DOWN; d++) {
            if (model.isVertex(x, wanted.getY() - d, z)) {
                return Vec3i.of(x, wanted.getY() - d, z);
            }
            if (d <= GOAL_SNAP_UP && model.isVertex(x, wanted.getY() + d, z)) {
                return Vec3i.of(x, wanted.getY() + d, z);
            }
        }
        return wanted;
    }

    // ---------------------------------------------------------------- types

    private static final class Node {
        private final Vec3i cell;
        private double g = INFINITY;
        private double rhs = INFINITY;
        private double[] key;
        private int index = -1;

        private Node(Vec3i cell) {
            this.cell = cell;
        }
    }

    /** A binary min-heap that knows where each node is, for the updates and removals D* Lite needs. */
    private static final class Heap {

        private Node[] items = new Node[256];
        private int size;

        boolean isEmpty() {
            return size == 0;
        }

        void clear() {
            for (int i = 0; i < size; i++) {
                items[i].index = -1;
                items[i] = null;
            }
            size = 0;
        }

        Node peek() {
            return items[0];
        }

        double[] peekKey() {
            return items[0].key;
        }

        void push(Node n, double[] key) {
            if (size == items.length) {
                Node[] grown = new Node[size * 2];
                System.arraycopy(items, 0, grown, 0, size);
                items = grown;
            }
            n.key = key;
            n.index = size;
            items[size++] = n;
            up(n.index);
        }

        void update(Node n, double[] key) {
            double[] old = n.key;
            n.key = key;
            if (less(key, old)) {
                up(n.index);
            } else {
                down(n.index);
            }
        }

        void remove(Node n) {
            int i = n.index;
            if (i < 0) {
                return;
            }
            Node moved = items[--size];
            items[size] = null;
            n.index = -1;
            if (i < size) {
                items[i] = moved;
                moved.index = i;
                up(i);
                down(moved.index);
            }
        }

        private void up(int i) {
            Node n = items[i];
            while (i > 0) {
                int parent = (i - 1) >>> 1;
                if (!less(n.key, items[parent].key)) {
                    break;
                }
                items[i] = items[parent];
                items[i].index = i;
                i = parent;
            }
            items[i] = n;
            n.index = i;
        }

        private void down(int i) {
            Node n = items[i];
            while (true) {
                int left = 2 * i + 1;
                if (left >= size) {
                    break;
                }
                int child = left + 1 < size && less(items[left + 1].key, items[left].key) ? left + 1 : left;
                if (!less(items[child].key, n.key)) {
                    break;
                }
                items[i] = items[child];
                items[i].index = i;
                i = child;
            }
            items[i] = n;
            n.index = i;
        }
    }
}
