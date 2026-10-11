package dev.px.karatsuba.client.automation.navigation;

/** Why a {@link Driver} asked for a segment. */
public enum Replan {

    /** The trip began, or began again after a pause. */
    START,

    /** The next segment, asked for as the current one began: the usual reason, and a free one. */
    NEXT,

    /** The goal follows something, and it moved far enough to aim again. */
    GOAL_MOVED,

    /** The player was not where the segment expected: knocked back, slowed, or the simulation is off. */
    DRIFT,

    /** Played again through the world as it is now, the rest of the segment no longer ended where it did. */
    BLOCKS_CHANGED,

    /** The next segment was not ready when the current one ran out. */
    LATE,

    /** The last segment ended without the goal being met: a partial route's end. */
    ROUTE_ENDED
}
