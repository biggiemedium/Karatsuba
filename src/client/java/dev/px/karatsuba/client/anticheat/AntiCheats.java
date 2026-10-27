package dev.px.karatsuba.client.anticheat;

import dev.px.core.Core;
import dev.px.core.network.anticheat.AntiCheatChangeEvent;
import dev.px.core.network.anticheat.AntiCheatService;
import dev.px.core.network.anticheat.AntiCheatSignature;
import dev.px.core.network.anticheat.AntiCheatSignatures;
import dev.px.core.network.anticheat.Confidence;
import dev.px.core.network.anticheat.Detection;
import dev.px.core.network.anticheat.ServerEvidence;

import java.util.List;
import java.util.Optional;

/**
 * The anticheat signature catalogue: what {@code Core.anticheat()} looks for.
 *
 * <p>Core ships none. A detection is a guess that says how sure it is, so
 * nothing read from transactions is ever {@code KNOWN}: only a server that is
 * publicly known to run one is.
 *
 * <p>What is here by default:
 *
 * <ul>
 *   <li><b>Numbering.</b> Where a server's transaction ids start on a new
 *       connection, and which way they count, names the anticheat that numbers
 *       them. The table is Wurst7-CevAPI's {@code ServerObserver.guessAntiCheat};
 *       of its rows only Grim's is checked against the anticheat itself (2.3.73
 *       bytecode, {@code Grim.md}), and each detection's reason says which.</li>
 *   <li><b>Behaviour.</b> A server sending answer-me packets far faster than the
 *       game's own traffic explains is timing the client, whichever anticheat it
 *       is. A vanilla server sends none, so even a few a second is telling.</li>
 *   <li><b>Known servers.</b> Named outright, at {@code KNOWN}.</li>
 * </ul>
 *
 * <p>At equal confidence a named detection outranks the generic behavioural one,
 * so a numbering match is what the HUD shows, and the generic one covers a
 * server whose numbering is in no row.
 */
public final class AntiCheats {

    /** Transactions a second above which the server is probably timing the client. */
    private static final double POSSIBLE_RATE = 2d;

    /** Transactions a second above which it almost certainly is. */
    private static final double LIKELY_RATE = 10d;

    /** Ids from the start of a connection needed before the numbering is read; Wurst's threshold too. */
    private static final int MIN_START_IDS = 5;

    private static final String CHECKED = "checked against Grim 2.3.73";
    private static final String UNCHECKED = "Wurst7-CevAPI's table, unverified";

    private AntiCheats() {
    }

    public static void registerAll(Core core) {
        AntiCheatService service = core.getAntiCheatService();
        for (AntiCheatSignature signature : all()) {
            service.register(signature);
        }
    }

    /**
     * Whether the most likely anticheat is now a different one, or the same one
     * held with a different confidence.
     *
     * <p>Core posts {@link AntiCheatChangeEvent} whenever any detection differs,
     * reason included, and a behavioural reason carries a live count &mdash; so
     * it changes about once a second while transactions arrive. Anything that
     * tells the player should ask this instead.
     *
     * @return the new verdict, or empty if it has not changed or there is none
     */
    public static Optional<Detection> verdictChanged(AntiCheatChangeEvent event) {
        Optional<Detection> current = event.getPrimary();
        if (current.isEmpty()) {
            return Optional.empty();
        }
        Detection now = current.get();
        Detection before = event.getPrevious().isEmpty() ? null : event.getPrevious().get(0);
        boolean same = before != null && before.is(now.getName()) && before.getConfidence() == now.getConfidence();
        return same ? Optional.empty() : current;
    }

    public static AntiCheatSignature[] all() {
        return new AntiCheatSignature[]{
                // Numbering
                AntiCheats::byNumbering,

                // Behaviour
                AntiCheatSignatures.transactionBased(POSSIBLE_RATE, LIKELY_RATE),

                // Known servers
                AntiCheatSignature.onServer("hypixel.net", "Watchdog"),
        };
    }

    // ------------------------------------------------------------- numbering

    /**
     * Wurst7-CevAPI's rules, first match wins, read off the first ids of the
     * connection. A counter stepping by the same amount every time is matched
     * on where it starts; the shapes after that are counters with one odd step
     * at the front.
     */
    private static Detection byNumbering(ServerEvidence evidence) {
        List<Long> ids = evidence.getTransactions().getStartIds();
        if (ids.size() < MIN_START_IDS) {
            return null;
        }
        long first = ids.get(0);
        long step = ids.get(1) - first;

        if (steadyFrom(ids, 1, step) && (step == 1L || step == -1L)) {
            String name = step == 1L ? countingUp(first) : countingDown(first);
            return found(name, first, step == 1L ? "counts up" : "counts down");
        }
        if (step == 0L && steadyFrom(ids, 1, 1L)) {
            return found("Verus", first, "repeats its first id, then counts up");
        }
        if (step >= 100L && steadyFrom(ids, 1, -1L)) {
            return found("Polar", first, "jumps, then counts down");
        }
        if (first < -3000L && ids.contains(0L)) {
            return found("Intave", first, "reaches 0 from below -3000");
        }
        if (first == -30767L && ids.get(1) == -30766L && ids.get(2) == -25767L && steadyFrom(ids, 2, 1L)) {
            return found("Vulcan (old)", first, "counts up after a jump of 5000");
        }
        return null;
    }

    private static String countingUp(long first) {
        if (first >= -23772L && first <= -23762L) {
            return "Vulcan";
        }
        if ((first >= 95L && first <= 105L) || (first >= -20005L && first <= -19995L)) {
            return "Matrix";
        }
        if (first >= -32773L && first <= -32762L) {
            return "Grizzly";
        }
        return "Verus";
    }

    private static String countingDown(long first) {
        if (first >= -8287L && first <= -8280L) {
            return "Errata";
        }
        if (first < -3000L) {
            return "Intave";
        }
        if (first >= -5L && first <= 0L) {
            return "Grim";
        }
        if (first >= -3000L && first <= -2995L) {
            return "Karhu";
        }
        return "Polar";
    }

    /** @return whether every step from {@code from} on is {@code step} */
    private static boolean steadyFrom(List<Long> ids, int from, long step) {
        for (int i = from + 1; i < ids.size(); i++) {
            if (ids.get(i) - ids.get(i - 1) != step) {
                return false;
            }
        }
        return true;
    }

    private static Detection found(String name, long first, String shape) {
        String source = name.equals("Grim") ? CHECKED : UNCHECKED;
        return Detection.of(name, Confidence.LIKELY, "ids start at " + first + " and " + shape + " (" + source + ")");
    }
}
