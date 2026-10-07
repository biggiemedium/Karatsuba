package dev.px.karatsuba.client.service.action;

/**
 * What happened to an {@link Action}, told to its {@code then} callback at the
 * end of the tick it was submitted for.
 *
 * <p>Only {@link #SENT} means a packet went out. Everything else means nothing
 * did, and the module decides whether to ask again next tick &mdash; which for
 * {@link #TURNING} and {@link #WAITING} is usually yes.
 */
public enum Outcome {

    /** Sent, and the game accepted it: attacked, placed, used. */
    SENT,

    /** Sent to the game, which refused it: nothing to place against, nothing to use. */
    REFUSED,

    /** Another module's action had the higher priority this tick. */
    OUTRANKED,

    /** The rotation the server has does not look at the target yet. Turning toward it. */
    TURNING,

    /** Further than the player's reach for it, from the eyes the server knows. */
    OUT_OF_REACH,

    /** The item to hold is not in the hotbar or the offhand, or swapping is off. */
    NO_ITEM,

    /** The entity to attack is gone. */
    NO_TARGET,

    /** A player could not do this now: a container is open, or an item is in use. */
    BUSY,

    /** Allowed, but not yet: the delay since the last one, or one of each a tick. */
    WAITING,

    /** Not allowed by the profile: an air place on a server that refuses them. */
    NOT_ALLOWED
}
