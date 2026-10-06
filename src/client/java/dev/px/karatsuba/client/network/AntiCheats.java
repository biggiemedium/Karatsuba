package dev.px.karatsuba.client.network;

import dev.px.core.Core;
import dev.px.core.network.anticheat.AntiCheatChangeEvent;
import dev.px.core.network.anticheat.AntiCheatService;
import dev.px.core.network.anticheat.AntiCheatSignature;
import dev.px.core.network.anticheat.AntiCheatSignatures;
import dev.px.core.network.anticheat.Detection;

import java.util.Optional;

/**
 * The anticheat signature catalogue: what {@code Core.anticheat()} looks for.
 *
 * <p>Core ships none, and for the same reason this list is short. A detection is
 * a guess that says how sure it is, and a signature that names an anticheat on
 * thin evidence reports the wrong name with {@code KNOWN} confidence &mdash;
 * worse than reporting nothing. Only evidence that actually identifies something
 * belongs here: a server that is publicly known to run one, a brand or a plugin
 * channel that names one. Guesses from transaction numbering belong here only
 * once they have been checked against a real server.
 *
 * <p>What is here by default:
 *
 * <ul>
 *   <li><b>Behaviour.</b> A server sending answer-me packets far faster than the
 *       game's own traffic explains is timing the client, whichever anticheat it
 *       is. A vanilla server sends none, so even a few a second is telling.</li>
 *   <li><b>Known servers.</b> Named outright, at {@code KNOWN}.</li>
 * </ul>
 *
 * <p>At equal confidence a named detection outranks the generic behavioural one,
 * so adding a signature for a specific anticheat is always safe: it wins where it
 * matches and the generic one still covers everything else.
 */
public final class AntiCheats {

    /** Transactions a second above which the server is probably timing the client. */
    private static final double POSSIBLE_RATE = 2d;

    /** Transactions a second above which it almost certainly is. */
    private static final double LIKELY_RATE = 10d;

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
                // Behaviour
                AntiCheatSignatures.transactionBased(POSSIBLE_RATE, LIKELY_RATE),

                // Known servers
                AntiCheatSignature.onServer("hypixel.net", "Watchdog"),
        };
    }
}
