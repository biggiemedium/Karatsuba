package dev.px.karatsuba.client.module.movement.blink;

import java.util.HashMap;
import java.util.Map;

/**
 * How long a blink's bursts may hold on the server you are on, learned from what
 * the server does with them.
 *
 * <p>Grim's TimerLimit sets the longest hold a server accepts from its
 * {@code ping-abuse-limit-threshold}, which servers change (PvP servers often
 * lower it), and real ping jitters, so no fixed budget is right everywhere. So
 * bursts start short and grow while they land clean, and a setback just after a
 * burst lands cuts the length back hard: more slowly up, quickly down, the way a
 * network finds its own limit. What it learns is kept per server for the session,
 * so a second blink starts where the last one left off.
 *
 * <p>The budget is a ceiling on top of Blink's own Grim Safe limit, never above
 * it.
 */
public final class BurstBudget {

    /** Ticks the first burst on a server holds: well inside any threshold a server would set. */
    public static final int START_TICKS = 8;

    /** Never shorter than this: below it a burst does nothing a lag spike would not. */
    public static final int MIN_TICKS = 3;

    /** Ticks added after each burst that lands clean. */
    private static final int GROW_TICKS = 2;

    /** What a burst that is set back leaves of the length: and the new ceiling it may grow to. */
    private static final double CUT = 0.6d;

    /** Ticks under a length that was set back before growing stops: the edge is not tested again at once. */
    private static final int CEILING_MARGIN = 1;

    private static final Map<String, Learned> SERVERS = new HashMap<>();

    private static final class Learned {
        int ticks = START_TICKS;
        /** The longest this server is believed to take; MAX_VALUE until a setback shows one. */
        int ceiling = Integer.MAX_VALUE;
    }

    private Learned current = new Learned();
    private String server = "";

    /** Switches to what is known of {@code server}: an address, or empty for singleplayer. */
    public void server(String server) {
        this.server = server == null ? "" : server;
        current = SERVERS.computeIfAbsent(this.server, key -> new Learned());
    }

    /** @return ticks the next burst may hold on this server */
    public int ticks() {
        return current.ticks;
    }

    /** @return whether a setback has shown this server's limit to be lower than Blink's own guess */
    public boolean isLearned() {
        return current.ceiling != Integer.MAX_VALUE;
    }

    /** A burst of {@code held} ticks landed with no setback: try a little longer next time. */
    public void landed(int held) {
        // Only a burst near the current length says anything about the next one.
        if (held + GROW_TICKS < current.ticks) {
            return;
        }
        current.ticks = Math.min(current.ticks + GROW_TICKS, current.ceiling - CEILING_MARGIN);
        current.ticks = Math.max(current.ticks, MIN_TICKS);
    }

    /** A burst of {@code held} ticks was set back: cut back below it, and do not grow past it again. */
    public void setBack(int held) {
        int cut = Math.max(MIN_TICKS, (int) Math.floor(held * CUT));
        current.ticks = Math.min(current.ticks, cut);
        current.ceiling = Math.max(MIN_TICKS + CEILING_MARGIN, held);
    }
}
