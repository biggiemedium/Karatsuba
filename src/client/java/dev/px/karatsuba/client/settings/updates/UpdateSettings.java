package dev.px.karatsuba.client.settings.updates;

import dev.px.core.Core;
import dev.px.core.event.impl.ClientLifecycleEvent;
import dev.px.core.setting.impl.BooleanSetting;
import dev.px.core.setting.impl.NumberSetting;
import dev.px.core.util.net.Http;
import dev.px.karatsuba.client.settings.ClientSection;

import java.util.concurrent.TimeUnit;

/**
 * Whether, and how often, the client looks for a newer release.
 *
 * <p>A client setting, not a module: checking for updates is housekeeping for the
 * client, not something done in game. It used to be the Update Check module. The
 * check runs on one background loop started once the client is up
 * ({@link #run}), which reads these settings on every pass, so turning it off or
 * changing the interval applies from the next pass.
 */
public final class UpdateSettings extends ClientSection {

    private static final String VERSION_URL = "https://karatsuba.invalid/latest.txt";

    /** How often an idle loop looks again whether checking has been switched on. */
    private static final long IDLE_MILLIS = TimeUnit.MINUTES.toMillis(1);

    private final BooleanSetting enabled = bool("Check For Updates", true)
            .describe("Look for a newer release in the background");

    private final NumberSetting<Integer> interval = integer("Interval", 30, 5, 240)
            .describe("Minutes between checks")
            .visibleWhen(enabled);

    private final BooleanSetting notifyOnce = bool("Notify Once", true)
            .describe("Report a given version once, rather than at every check")
            .visibleWhen(enabled);

    private volatile String reported = "";

    public UpdateSettings() {
        super("updates", "Updates", "Whether and how often to look for a newer release");
    }

    /** Starts the update loop once the client is up: the thread service is running by then. */
    @Override
    protected void onRegister(Core core) {
        core.getBus().on(ClientLifecycleEvent.class, event -> {
            if (event.getPhase() == ClientLifecycleEvent.Phase.STARTED) {
                Core.threads().loop("Update Check", this::run);
            }
        });
    }

    /**
     * The background loop: checks, then sleeps the interval, until the client
     * stops. Run on a {@code ThreadService} loop of its own.
     */
    public void run() {
        while (!Thread.currentThread().isInterrupted()) {
            if (enabled.isOn()) {
                check();
            }
            try {
                Thread.sleep(enabled.isOn() ? TimeUnit.MINUTES.toMillis(interval.getInt()) : IDLE_MILLIS);
            } catch (InterruptedException interrupted) {
                // Shutting down. Returning ends the loop; carrying on would ignore it.
                Thread.currentThread().interrupt();
                return;
            }
        }
    }

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
