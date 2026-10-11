package dev.px.karatsuba.pathing;

import dev.px.core.math.Vec3i;

import java.util.ArrayList;
import java.util.List;

/**
 * How a walking player moves across the block grid: the graph a pathfinder
 * searches.
 *
 * <p>A vertex is a cell the feet stand in. From one, a step in each of the eight
 * compass directions lands where a player would:
 *
 * <ul>
 *   <li><b>level</b>, if the next cell can be stood in;</li>
 *   <li>else <b>a step up</b> of one, if that can be stood in and there is
 *       headroom over the start for the jump;</li>
 *   <li>else <b>a fall</b> down the next column to the first cell that can be
 *       stood in, if every cell on the way down is open and it is no further than
 *       {@code maxFall}.</li>
 * </ul>
 *
 * <p>A diagonal also needs both corners it passes open at the start's height, or
 * the body clips the join between two blocks. The landing is the first of these
 * that works, so each direction has at most one move, which is what lets
 * {@link #cost(Vec3i, Vec3i)} answer for a single pair.
 *
 * <p><b>Cost</b>: {@code (diagonal ? √2 : 1) + |Δy|}, times the landing cell's
 * {@link Terrain#costOf}. The same as Core's {@code AStar}.
 *
 * <p><b>Unseen columns</b> ({@link Terrain#isKnown}) are level ground: a move into
 * one lands at the same height. Optimistic on purpose: the planner heads through
 * them and repairs the route when they are seen.
 *
 * <p>Immutable, and as thread-safe as the terrain it reads.
 */
public final class WalkModel {

    public static final double DIAGONAL = Math.sqrt(2d);

    private static final int[][] DIRECTIONS = {
            { 1, 0 }, { -1, 0 }, { 0, 1 }, { 0, -1 },
            { 1, 1 }, { 1, -1 }, { -1, 1 }, { -1, -1 }
    };

    private final Terrain terrain;
    private final int maxFall;

    public WalkModel(Terrain terrain, int maxFall) {
        if (maxFall < 0) {
            throw new IllegalArgumentException("maxFall must not be negative");
        }
        this.terrain = terrain;
        this.maxFall = maxFall;
    }

    public Terrain getTerrain() {
        return terrain;
    }

    public int getMaxFall() {
        return maxFall;
    }

    /** @return whether the feet can be in this cell at all: it can be stood in, or its column is unseen */
    public boolean isVertex(int x, int y, int z) {
        return !terrain.isKnown(x, z) || terrain.isStandable(x, y, z);
    }

    /** @return every cell one move from {@code from} */
    public List<Vec3i> successors(Vec3i from) {
        List<Vec3i> found = new ArrayList<>(8);
        if (!isVertex(from.getX(), from.getY(), from.getZ())) {
            return found;
        }
        for (int[] d : DIRECTIONS) {
            Vec3i landing = landing(from, d[0], d[1]);
            if (landing != null) {
                found.add(landing);
            }
        }
        return found;
    }

    /**
     * @return every cell one move from which lands on {@code to}: the cells around
     *         it level with it, one below (a step up into it) and up to
     *         {@code maxFall} above (a fall into it)
     */
    public List<Vec3i> predecessors(Vec3i to) {
        List<Vec3i> found = new ArrayList<>(16);
        for (int[] d : DIRECTIONS) {
            int x = to.getX() - d[0];
            int z = to.getZ() - d[1];
            for (int dy = -1; dy <= maxFall; dy++) {
                Vec3i from = Vec3i.of(x, to.getY() + dy, z);
                Vec3i landing = landing(from, d[0], d[1]);
                if (landing != null && landing.equals(to) && isVertex(x, from.getY(), z)) {
                    found.add(from);
                }
            }
        }
        return found;
    }

    /** @return the cost of the move from {@code from} to {@code to}, or infinity if there is no such move */
    public double cost(Vec3i from, Vec3i to) {
        int dx = to.getX() - from.getX();
        int dz = to.getZ() - from.getZ();
        if (Math.abs(dx) > 1 || Math.abs(dz) > 1 || (dx == 0 && dz == 0)
                || !isVertex(from.getX(), from.getY(), from.getZ())) {
            return Double.POSITIVE_INFINITY;
        }
        Vec3i landing = landing(from, dx, dz);
        return landing != null && landing.equals(to) ? stepCost(from, to) : Double.POSITIVE_INFINITY;
    }

    /**
     * @return the least a route from {@code a} to {@code b} can cost: octile
     *         across, plus the height. Consistent with {@link #cost}
     */
    public static double heuristic(Vec3i a, Vec3i b) {
        int dx = Math.abs(a.getX() - b.getX());
        int dy = Math.abs(a.getY() - b.getY());
        int dz = Math.abs(a.getZ() - b.getZ());
        return (dx + dz) + (DIAGONAL - 2d) * Math.min(dx, dz) + dy;
    }

    // ------------------------------------------------------------- moves

    private Vec3i landing(Vec3i from, int dx, int dz) {
        int x = from.getX() + dx;
        int y = from.getY();
        int z = from.getZ() + dz;
        if (dx != 0 && dz != 0 && !corners(from, dx, dz)) {
            return null;
        }
        if (!terrain.isKnown(x, z)) {
            return Vec3i.of(x, y, z);
        }
        if (terrain.isStandable(x, y, z)) {
            return Vec3i.of(x, y, z);
        }
        boolean headroom = !terrain.isKnown(from.getX(), from.getZ())
                || terrain.isOpen(from.getX(), y + 1, from.getZ());
        if (headroom && terrain.isStandable(x, y + 1, z)) {
            return Vec3i.of(x, y + 1, z);
        }
        if (!terrain.isOpen(x, y, z)) {
            return null;
        }
        for (int drop = 1; drop <= maxFall; drop++) {
            if (terrain.isStandable(x, y - drop, z)) {
                return Vec3i.of(x, y - drop, z);
            }
            if (!terrain.isOpen(x, y - drop, z)) {
                return null;
            }
        }
        return null;
    }

    /** @return whether a diagonal's two corners are open at the start's height */
    private boolean corners(Vec3i from, int dx, int dz) {
        int y = from.getY();
        return open(from.getX() + dx, y, from.getZ()) && open(from.getX(), y, from.getZ() + dz);
    }

    private boolean open(int x, int y, int z) {
        return !terrain.isKnown(x, z) || terrain.isOpen(x, y, z);
    }

    private double stepCost(Vec3i from, Vec3i to) {
        boolean diagonal = from.getX() != to.getX() && from.getZ() != to.getZ();
        double base = (diagonal ? DIAGONAL : 1d) + Math.abs(from.getY() - to.getY());
        double multiplier = terrain.isKnown(to.getX(), to.getZ())
                ? Math.max(1d, terrain.costOf(to.getX(), to.getY(), to.getZ())) : 1d;
        return base * multiplier;
    }
}
