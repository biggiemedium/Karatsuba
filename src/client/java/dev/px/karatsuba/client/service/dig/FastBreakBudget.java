package dev.px.karatsuba.client.service.dig;

/**
 * The client's copy of Grim's FastBreak balance, so a block is only ever finished
 * early by as much as Grim lets pass.
 *
 * <p>Grim 2.3.73, on every FINISHED (see {@code Grim.md}, "FastBreak in full"):
 * {@code early = ceil(1 / damage) * 50 - (finish - start)} in milliseconds, where
 * damage is the best per-tick share seen since START. Under 25 ms early, the
 * balance shrinks by a tenth; otherwise the earliness is added. Over 1000 the
 * finish is flagged and cancelled. The balance is clamped to ±1000 (or the ping,
 * if higher) before each update.
 *
 * <p>Kept on the safe side of Grim's own: only digs this module sends are seen,
 * and every one of the player's own on-time digs would only have shrunk Grim's
 * balance further. The clamp uses 1000, never the ping.
 */
public final class FastBreakBudget {

    /** Grim's flag threshold, and its clamp. */
    private static final double LIMIT = 1000d;

    /** Finishes less early than this are on time to Grim. */
    private static final double COUNTS_FROM = 25d;

    /** What each on-time finish leaves of the balance. */
    private static final double DECAY = 0.9d;

    private static final double MILLIS_PER_TICK = 50d;

    private double balance;

    private long start = Long.MIN_VALUE;
    private float bestDamage;

    /** A START went out at this time, for a block taking {@code damage} a tick. */
    public void started(long at, float damage) {
        start = at;
        bestDamage = damage;
    }

    /** A tick of the dig, at {@code damage} a tick: Grim keeps the best it sees. */
    public void observe(float damage) {
        bestDamage = Math.max(bestDamage, damage);
    }

    /** The FINISHED went out at this time. */
    public void finished(long at) {
        if (start == Long.MIN_VALUE || bestDamage <= 0f) {
            return;
        }
        double early = earlyAt(at);
        balance = Math.max(-LIMIT, Math.min(LIMIT, balance));
        if (early < COUNTS_FROM) {
            balance *= DECAY;
        } else {
            balance += early;
        }
        start = Long.MIN_VALUE;
    }

    /** @return how early, in ms, a finish at this time would be to Grim; 0 or less is on time */
    public double earlyAt(long at) {
        if (start == Long.MIN_VALUE || bestDamage <= 0f) {
            return 0d;
        }
        double expected = Math.ceil(1d / bestDamage) * MILLIS_PER_TICK;
        return expected - (at - start);
    }

    /**
     * @return whether finishing at this time keeps the balance at or under {@code budget}
     *         with {@code margin} to spare for the network's jitter
     */
    public boolean allows(long at, double budget, double margin) {
        double early = earlyAt(at) + margin;
        return early < COUNTS_FROM || balance + early <= Math.min(budget, LIMIT);
    }

    /** @return the balance as Grim would have it, in ms */
    public double balance() {
        return balance;
    }
}
