package dev.px.karatsuba.pathing.test;

import dev.px.karatsuba.pathing.Terrain;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.function.BiPredicate;

/** A block grid drawn by hand: solid cells, slow cells, and which columns have been seen. */
final class GridTerrain implements Terrain {

    private final Set<Long> solid = new HashSet<>();
    private final Map<Long, Double> costs = new HashMap<>();
    private BiPredicate<Integer, Integer> known = (x, z) -> true;

    /** A floor at {@code y = 0} from (x0, z0) to (x1, z1) inclusive: feet stand at {@code y = 1}. */
    static GridTerrain floor(int x0, int z0, int x1, int z1) {
        GridTerrain grid = new GridTerrain();
        for (int x = x0; x <= x1; x++) {
            for (int z = z0; z <= z1; z++) {
                grid.set(x, 0, z, true);
            }
        }
        return grid;
    }

    GridTerrain set(int x, int y, int z, boolean block) {
        if (block) {
            solid.add(key(x, y, z));
        } else {
            solid.remove(key(x, y, z));
        }
        return this;
    }

    GridTerrain pillar(int x, int z, int height) {
        for (int y = 1; y <= height; y++) {
            set(x, y, z, true);
        }
        return this;
    }

    GridTerrain cost(int x, int y, int z, double multiplier) {
        costs.put(key(x, y, z), multiplier);
        return this;
    }

    GridTerrain known(BiPredicate<Integer, Integer> seen) {
        this.known = seen;
        return this;
    }

    boolean isSolid(int x, int y, int z) {
        return solid.contains(key(x, y, z));
    }

    @Override
    public boolean isOpen(int x, int y, int z) {
        return !isSolid(x, y, z) && !isSolid(x, y + 1, z);
    }

    @Override
    public boolean isStandable(int x, int y, int z) {
        return isOpen(x, y, z) && isSolid(x, y - 1, z);
    }

    @Override
    public double costOf(int x, int y, int z) {
        return costs.getOrDefault(key(x, y, z), 1d);
    }

    @Override
    public boolean isKnown(int x, int z) {
        return known.test(x, z);
    }

    private static long key(int x, int y, int z) {
        return ((long) (x & 0x3FFFFFF) << 38) | ((long) (z & 0x3FFFFFF) << 12) | (y & 0xFFF);
    }
}
