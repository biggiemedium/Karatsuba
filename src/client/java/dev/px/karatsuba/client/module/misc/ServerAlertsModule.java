package dev.px.karatsuba.client.module.misc;

import dev.px.core.Core;
import dev.px.core.event.Subscribe;
import dev.px.core.module.Module;
import dev.px.core.module.ModuleInfo;
import dev.px.core.network.anticheat.AntiCheatChangeEvent;
import dev.px.core.network.anticheat.Detection;
import dev.px.core.network.lag.LagSpikeEvent;
import dev.px.core.network.server.ServerBrand;
import dev.px.core.network.server.ServerChangeEvent;
import dev.px.core.setting.impl.BooleanSetting;
import dev.px.karatsuba.client.network.AntiCheats;

import java.util.Locale;
import java.util.Optional;

/**
 * Says when the connection changes: a lag spike, the software a server runs, an
 * anticheat detected.
 *
 * <p>Nothing here watches the network. Core's services do that and post an event
 * when something changes, always on the game thread, so each handler below is a
 * notification and nothing else. Disabling the module is what silences them; the
 * services keep measuring regardless, for the HUD and for any other module.
 */
@ModuleInfo(name = "Server Alerts", description = "Notifies on lag spikes, server software and anticheats",
        category = "Misc")
public final class ServerAlertsModule extends Module {

    private final BooleanSetting lagSpikes = bool("Lag Spikes", true)
            .describe("When the server stops responding, and when it comes back");

    private final BooleanSetting software = bool("Server Software", false)
            .describe("What a server runs, once its brand arrives");

    private final BooleanSetting anticheat = bool("Anticheat", true)
            .describe("When the most likely anticheat changes");

    @Subscribe
    private void onLagSpike(LagSpikeEvent event) {
        if (!lagSpikes.isOn()) {
            return;
        }
        if (event.isStarted()) {
            Core.notifications().warn("Lag", "Server not responding");
        } else {
            Core.notifications().info("Lag", String.format(Locale.ROOT, "Server back after %.1fs",
                    event.getMillis() / 1000d));
        }
    }

    @Subscribe
    private void onServerChanged(ServerChangeEvent event) {
        if (!software.isOn() || !event.isBrandChanged() || !event.getCurrent().isMultiplayer()) {
            return;
        }
        ServerBrand brand = event.getCurrent().getBrand();
        if (brand == null) {
            return;
        }
        String name = brand.getSoftware().isKnown() ? brand.getSoftware().getName() : brand.getRaw();
        if (brand.getProxy() != null) {
            name += " via " + brand.getProxy().getName();
        }
        Core.notifications().info("Server", event.getCurrent().getHost() + " runs " + name);
    }

    /** Only when the verdict changes, not the evidence; see {@link AntiCheats#verdictChanged}. */
    @Subscribe
    private void onAntiCheatChanged(AntiCheatChangeEvent event) {
        if (!anticheat.isOn()) {
            return;
        }
        Optional<Detection> verdict = AntiCheats.verdictChanged(event);
        verdict.ifPresent(detection -> Core.notifications().warn("Anticheat", detection.getName() + " ("
                + detection.getConfidence().name().toLowerCase(Locale.ROOT) + ")"));
    }
}
