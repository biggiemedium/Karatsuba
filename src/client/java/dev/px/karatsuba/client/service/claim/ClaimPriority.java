package dev.px.karatsuba.client.service.claim;

/**
 * Ordering anchors for a claim. Higher wins.
 *
 * <p>The same five values and the same span as Core's
 * {@code RotationPriority}, deliberately: a client with two priority scales is a
 * client where nobody remembers which one goes to 100. Any int between them
 * works, exactly as with {@code event.Priority}.
 *
 * <p>These are only anchors. What decides an argument between two modules is the
 * number, not the name &mdash; {@code NORMAL + 1} is a perfectly good priority
 * and is often the honest one.
 */
public final class ClaimPriority {

    /** Reserved for things a user asked for directly, which should beat automation. */
    public static final int HIGHEST = 100;

    public static final int HIGH = 75;

    /** Where a module belongs unless it has a reason not to. */
    public static final int NORMAL = 50;

    public static final int LOW = 25;

    /** Background work that should yield to anything at all. */
    public static final int LOWEST = 0;

    private ClaimPriority() {
    }
}
