package dev.px.karatsuba.client.automation.navigation;

import java.util.EnumMap;
import java.util.Map;

/**
 * How a trip has gone: how often it planned and why, and how often movement had
 * to pause for a plan. The numbers to read when a trip stutters.
 *
 * <p>Live, not a snapshot: it is the {@link Driver}'s own, read on the game thread.
 */
public final class TripStats {

    private final Map<Replan, Integer> counts = new EnumMap<>(Replan.class);
    private int plans;
    private int splices;
    private int waitTicks;
    private double worstDrift;
    private Replan lastReplan;

    void reset() {
        counts.clear();
        plans = 0;
        splices = 0;
        waitTicks = 0;
        worstDrift = 0d;
        lastReplan = null;
    }

    void planned(Replan why) {
        plans++;
        lastReplan = why;
        counts.merge(why, 1, Integer::sum);
    }

    void spliced() {
        splices++;
    }

    void waited() {
        waitTicks++;
    }

    void drifted(double off) {
        worstDrift = Math.max(worstDrift, off);
    }

    /** @return segments asked for */
    public int getPlans() {
        return plans;
    }

    /** @return segments asked for, for this reason */
    public int count(Replan why) {
        return counts.getOrDefault(why, 0);
    }

    /** @return segments that took over from the one before without a pause */
    public int getSplices() {
        return splices;
    }

    /** @return ticks spent standing still waiting for a plan */
    public int getWaitTicks() {
        return waitTicks;
    }

    /** @return the furthest the player was from where a segment expected when it was given up */
    public double getWorstDrift() {
        return worstDrift;
    }

    /** @return why the last segment was asked for; null before the first */
    public Replan getLastReplan() {
        return lastReplan;
    }

    @Override
    public String toString() {
        return plans + " plans " + counts + ", " + splices + " spliced, " + waitTicks + " ticks waiting"
                + String.format(", worst drift %.2f", worstDrift);
    }
}
