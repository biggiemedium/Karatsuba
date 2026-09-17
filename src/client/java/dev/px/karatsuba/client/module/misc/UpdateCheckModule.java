package dev.px.karatsuba.client.module.misc;

import dev.px.core.Core;
import dev.px.core.module.ModuleInfo;
import dev.px.core.module.ThreadedModule;
import dev.px.core.setting.impl.BooleanSetting;
import dev.px.core.setting.impl.NumberSetting;
import dev.px.core.util.net.Http;

import java.util.concurrent.TimeUnit;

/**
 * Polls for a newer release and says so once.
 *
 * <p>The worked example for <b>threading</b>, and it is here because a network
 * call is the honest case: it blocks for as long as a stranger's server feels
 * like taking, and on the game thread that is a frozen client.
 *
 * <p>Three of Core's threading pieces meet here, and the split between them is
 * the thing to read:
 *
 * <ul>
 *   <li>{@link ThreadedModule} gives the body its <b>own thread</b>, taken from
 *       {@code ThreadService.loop} rather than the shared worker pool. A
 *       {@code while} loop on a shared pool owns a worker forever and the timers
 *       sharing it quietly stop firing &mdash; nothing throws, nothing is logged.
 *       Enabling the module starts the thread and disabling it interrupts it, so
 *       there is no executor anywhere below.</li>
 *   <li>{@link Http} runs <b>on that thread</b>. It is a blocking one-shot by
 *       design, which is safe precisely because of where it is called.</li>
 *   <li>{@code Core.threads().sync(...)} is the <b>way back</b>. The notification
 *       queue is game state; touching it from here would be a data race with the
 *       renderer reading it. Core drains that queue from {@code TickEvent}, which
 *       the adapter already posts, so nothing extra is wired up for this to
 *       arrive.</li>
 * </ul>
 *
 * <p>Interruption is honoured rather than swallowed: the sleep returns from the
 * method instead of looping, so switching the module off stops the thread on the
 * next wake rather than one poll later. Core does not treat cancellation as a
 * failure, so a toggle logs nothing.
 */
@ModuleInfo(name = "Update Check", description = "Checks for a newer release in the background",
        category = "Misc")
public final class UpdateCheckModule extends ThreadedModule {

    /**
     * Where the current version number is published.
     *
     * <p><b>A placeholder.</b> {@code .invalid} is the reserved TLD that is
     * guaranteed never to resolve, so as shipped this module polls nothing, finds
     * nothing and reports nothing &mdash; point it at a real URL to make it live.
     * A deliberately dead address rather than a real one, because a module that
     * quietly talks to a host the reader has not seen is worse than one that does
     * nothing.
     *
     * <p>A plain-text body holding a version and nothing else, so the check needs
     * no parser and a malformed response is simply a string that does not match.
     */
    private static final String VERSION_URL = "https://karatsuba.invalid/latest.txt";

    private final NumberSetting<Integer> interval = integer("Interval", 30, 5, 240)
            .describe("Minutes between checks");

    private final BooleanSetting notifyOnce = bool("Notify Once", true)
            .describe("Report a given version once, rather than at every check");

    /**
     * The version already reported.
     *
     * <p>Volatile because it is written on the background thread and read there
     * too, but assigned from the sync task on the game thread &mdash; two threads,
     * so the field needs to say so.
     */
    private volatile String reported = "";

    @Override
    protected void runInBackground() {
        while (!Thread.currentThread().isInterrupted()) {
            check();
            try {
                Thread.sleep(TimeUnit.MINUTES.toMillis(interval.getInt()));
            } catch (InterruptedException interrupted) {
                // Disabled. Returning ends the task; looping here would ignore it.
                Thread.currentThread().interrupt();
                return;
            }
        }
    }

    /**
     * One poll.
     *
     * <p>{@code getOrNull} rather than {@code get}: a failed update check is not
     * worth a stack trace in someone's log every half hour, and there is nothing
     * to do about it but try again next time.
     */
    private void check() {
        String body = Http.getOrNull(VERSION_URL);
        if (body == null) {
            return;
        }
        String latest = body.trim();
        if (latest.isEmpty() || latest.equals(Core.get().getClientVersion())) {
            return;
        }
        if (notifyOnce.isOn() && latest.equals(reported)) {
            return;
        }

        // Back onto the game thread before touching anything the renderer reads.
        Core.threads().sync(() -> {
            reported = latest;
            Core.notifications().info("Update", latest + " is available");
        });
    }
}
