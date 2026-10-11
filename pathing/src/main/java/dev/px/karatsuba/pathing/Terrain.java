package dev.px.karatsuba.pathing;

/**
 * What a pathfinder may ask about the world, cell by cell.
 *
 * <p>Richer than Core's {@code PathSpace}, which only says whether a cell can be
 * stood in: walking moves also need to know whether a body fits through a cell
 * without standing there (the column a fall drops down, the headroom over a
 * jump), and a planner for terrain it has not seen needs to know what it has
 * seen at all.
 *
 * <p>Every method should be O(1): a search asks them tens of thousands of times.
 * Implementations are read from whichever thread the search runs on.
 */
public interface Terrain {

    /** @return whether a body fits in this cell: it and the one above have nothing to collide with */
    boolean isOpen(int x, int y, int z);

    /** @return whether a body can stand in this cell: open, with something solid to stand on below */
    boolean isStandable(int x, int y, int z);

    /**
     * @return a multiplier on the cost of entering this cell, at least 1:
     *         higher for anything slow or dangerous to walk through
     *
     * <p>Never below 1. D* Lite's heuristic assumes every move costs at least its
     * distance, and a cheaper cell breaks that.
     */
    default double costOf(int x, int y, int z) {
        return 1d;
    }

    /**
     * @return whether this column has been seen: in Minecraft, whether its chunk
     *         is loaded. An unseen column is assumed to be level, open ground until
     *         it is seen, the free-space assumption D* Lite is built around
     */
    default boolean isKnown(int x, int z) {
        return true;
    }
}
