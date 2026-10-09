package dev.px.karatsuba.client.hud.element;

import dev.px.core.Core;
import dev.px.core.hud.Anchor;
import dev.px.core.hud.HudLayout;
import dev.px.core.layout.Align;
import dev.px.core.layout.Content;
import dev.px.core.network.anticheat.Detection;
import dev.px.core.network.server.ServerBrand;
import dev.px.core.network.server.ServerInfo;
import dev.px.core.network.server.ServerSoftware;
import dev.px.core.render.Color;
import dev.px.core.setting.impl.BooleanSetting;
import dev.px.karatsuba.client.hud.style.StyledElement;

import java.util.Locale;
import java.util.Optional;

/**
 * The server: its address, the software it runs, and its probable anticheat.
 *
 * <p>A label and a value per row. A detected anticheat is shown in the
 * "fair" colour and named in words, so it reads the same without colour.
 */
public final class ServerElement extends StyledElement {

    private final BooleanSetting address = bool("Address", true);
    private final BooleanSetting software = bool("Software", true);
    private final BooleanSetting anticheat = bool("Anticheat", true);

    private final BooleanSetting rawBrand = bool("Raw Brand", false)
            .describe("Show the brand exactly as the server sent it, rather than the software it names")
            .visibleWhen(software);

    public ServerElement() {
        super("server", "Server", HudLayout.at(Anchor.BOTTOM_RIGHT, -4f, -44f));
    }

    @Override
    protected void describe(Content body) {
        if (isEditing()) {
            sample(body);
            return;
        }
        ServerInfo server = Core.server().getInfo();
        watch(server.getAddress(), Core.anticheat().getPrimary().map(detection -> detection.getName()).orElse(null));
        if (address.isOn()) {
            row(body, "Server", server.isSingleplayer() ? "Singleplayer" : server.getAddress(), style().value());
        }
        if (software.isOn() && server.isMultiplayer()) {
            row(body, "Software", software(server.getBrand()), style().value());
        }
        if (anticheat.isOn() && server.isMultiplayer()) {
            Optional<Detection> primary = Core.anticheat().getPrimary();
            row(body, "Anticheat",
                    primary.map(d -> d.getName() + " (" + d.getConfidence().name().toLowerCase(Locale.ROOT) + ")")
                            .orElse("None detected"),
                    primary.isPresent() ? Health.FAIR : style().muted());
        }
    }

    private void sample(Content body) {
        if (address.isOn()) {
            row(body, "Server", "play.example.net", style().value());
        }
        if (software.isOn()) {
            row(body, "Software", "Paper via Velocity", style().value());
        }
        if (anticheat.isOn()) {
            row(body, "Anticheat", "None detected", style().muted());
        }
    }

    private String software(ServerBrand brand) {
        if (brand == null) {
            return "Unknown";
        }
        if (rawBrand.isOn()) {
            return brand.getRaw();
        }
        ServerSoftware runs = brand.getSoftware();
        String name = runs != null && runs.isKnown() ? runs.getName() : brand.getRaw();
        ServerSoftware proxy = brand.getProxy();
        return proxy != null ? name + " via " + proxy.getName() : name;
    }

    private void row(Content body, String label, String value, Color color) {
        body.row(row -> {
            row.align(Align.CENTER);
            label(row, label);
            row.space(LABEL_GAP, 0f);
            text(row, value, color);
        });
    }
}
