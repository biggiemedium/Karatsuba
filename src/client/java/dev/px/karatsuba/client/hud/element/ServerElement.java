package dev.px.karatsuba.client.hud.element;

import dev.px.core.Core;
import dev.px.gui.legacy.GuiStyle;
import dev.px.core.hud.AbstractHudElement;
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
import dev.px.core.setting.impl.ColorSetting;

import java.util.Locale;
import java.util.Optional;

/**
 * Which server this is, what it runs, and what is probably watching.
 *
 * <p>All three rows are Core's answers: {@code Core.server()} for the address and
 * the software its brand names, {@code Core.anticheat()} for the most confident
 * detection. What counts as recognisable software and which anticheats can be
 * named are the client's catalogues in {@code client/network}; this only shows
 * the result.
 *
 * <p>An anticheat detection is a guess with a confidence attached, and the row
 * says which so a {@code POSSIBLE} is never read as a certainty.
 */
public final class ServerElement extends AbstractHudElement {

    private static final float PADDING = 4f;
    private static final float ROW_GAP = 1f;
    private static final float LABEL_GAP = 6f;

    private final BooleanSetting address = bool("Address", true);
    private final BooleanSetting software = bool("Software", true);
    private final BooleanSetting anticheat = bool("Anticheat", true);

    private final BooleanSetting rawBrand = bool("Raw Brand", false)
            .describe("Show the brand exactly as the server sent it, rather than the software it names")
            .visibleWhen(software);

    private final ColorSetting labelColor = color("Label Colour", Color.of(150, 160, 180));
    private final ColorSetting valueColor = color("Value Colour", Color.WHITE);

    public ServerElement() {
        super("server", "Server", HudLayout.at(Anchor.TOP_RIGHT, -4f, 4f));
    }

    @Override
    public void content(Content content) {
        content.background(GuiStyle.surface(), GuiStyle.radius()).padding(PADDING).gap(ROW_GAP);
        content.align(Align.STRETCH);
        content.min(90f, 12f);

        if (isEditing()) {
            sample(content);
            return;
        }

        ServerInfo server = Core.server().getInfo();
        if (address.isOn()) {
            row(content, "Server", server.isSingleplayer() ? "Singleplayer" : server.getAddress(), valueColor.resolve());
        }
        if (software.isOn() && server.isMultiplayer()) {
            row(content, "Software", software(server.getBrand()), valueColor.resolve());
        }
        if (anticheat.isOn() && server.isMultiplayer()) {
            Optional<Detection> primary = Core.anticheat().getPrimary();
            row(content, "Anticheat",
                    primary.map(d -> d.getName() + " (" + d.getConfidence().name().toLowerCase(Locale.ROOT) + ")")
                            .orElse("None detected"),
                    primary.isPresent() ? Color.ORANGE : Color.GRAY);
        }
    }

    /** What the box looks like while being positioned, so it does not resize under the cursor. */
    private void sample(Content content) {
        if (address.isOn()) {
            row(content, "Server", "play.example.net", valueColor.resolve());
        }
        if (software.isOn()) {
            row(content, "Software", "Paper via Velocity", valueColor.resolve());
        }
        if (anticheat.isOn()) {
            row(content, "Anticheat", "None detected", Color.GRAY);
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

    private void row(Content content, String label, String value, Color color) {
        content.row(row -> {
            row.align(Align.CENTER);
            row.text(label, labelColor.resolve());
            row.space(LABEL_GAP, 0f);
            row.fill();
            row.text(value, color);
        });
    }
}
