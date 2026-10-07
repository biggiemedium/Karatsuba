package dev.px.karatsuba.adapter.world;

/**
 * What the server made of a block the client placed ahead of it.
 *
 * <p>Read through {@link PlacementSync}. Only {@link #CONFIRMED} means the block is
 * really there; after the other two the client shows what the server has.
 */
public enum PlacementResult {

    /** The server answered, and the block is there. */
    CONFIRMED,

    /** The server answered, and the block is not: refused, or something else is there. Reverted. */
    REJECTED,

    /**
     * No answer in time: the click was most likely dropped on the way. The
     * client's prediction was taken back, as an answer would have, so the cell
     * shows what the server last said and can be tried again.
     */
    TIMED_OUT
}
