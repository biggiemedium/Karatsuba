package dev.px.karatsuba.client.settings.alerts;

import dev.px.core.Core;
import dev.px.core.network.anticheat.AntiCheatChangeEvent;
import dev.px.core.network.anticheat.Detection;
import dev.px.core.network.lag.LagSpikeEvent;
import dev.px.core.network.server.ServerBrand;
import dev.px.core.network.server.ServerChangeEvent;
import dev.px.core.setting.impl.BooleanSetting;
import dev.px.karatsuba.client.anticheat.AntiCheats;
import dev.px.karatsuba.client.settings.ClientSection;
import dev.px.karatsuba.client.notify.Notices;
import dev.px.karatsuba.client.notify.Source;

import java.util.Locale;
import java.util.Optional;

/**
 * Which changes to the connection the client tells you about: a lag spike, the
 * software a server runs, an anticheat detected.
 *
 * <p>A client setting, not a module: it reports on the connection rather than
 * doing anything in game. It used to be the Server Alerts module, whose on/off
 * toggle only muted all three at once, which the three switches here already do.
 *
 * <p>Nothing here watches the network. Core's services do that and post an event
 * when something changes, always on the game thread, so each handler below is a
 * notification and nothing else. The services keep measuring regardless, for the
 * HUD and for any module.
 */
public final class AlertSettings extends ClientSection {

    private final BooleanSetting lagSpikes = bool("Lag Spikes", true)
            .describe("When the server stops responding, and when it comes back");

    private final BooleanSetting software = bool("Server Software", false)
            .describe("What a server runs, once its brand arrives");

    private final BooleanSetting anticheat = bool("Anticheat", true)
            .describe("When the most likely anticheat changes");

    public AlertSettings() {
        super("alerts", "Alerts", "What to tell you when the connection changes");
    }

    /** Listens for the whole session; each handler reads its switch when the event arrives. */
    @Override
    protected void onRegister(Core core) {
        core.getBus().on(LagSpikeEvent.class, this::onLagSpike);
        core.getBus().on(ServerChangeEvent.class, this::onServerChanged);
        core.getBus().on(AntiCheatChangeEvent.class, this::onAntiCheatChanged);
    }

    private void onLagSpike(LagSpikeEvent event) {
        if (!lagSpikes.isOn()) {
            return;
        }
        if (event.isStarted()) {
            Notices.warn(Source.SERVER, "Lag", "Server not responding");
        } else {
            Notices.info(Source.SERVER, "Lag", String.format(Locale.ROOT, "Server back after %.1fs",
                    event.getMillis() / 1000d));
        }
    }

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
        Notices.info(Source.SERVER, "Server", event.getCurrent().getHost() + " runs " + name);
    }

    /** Only when the verdict changes, not the evidence; see {@link AntiCheats#verdictChanged}. */
    private void onAntiCheatChanged(AntiCheatChangeEvent event) {
        if (!anticheat.isOn()) {
            return;
        }
        Optional<Detection> verdict = AntiCheats.verdictChanged(event);
        verdict.ifPresent(detection -> Notices.warn(Source.SERVER, "Anticheat", detection.getName() + " ("
                + detection.getConfidence().name().toLowerCase(Locale.ROOT) + ")"));
    }
}
