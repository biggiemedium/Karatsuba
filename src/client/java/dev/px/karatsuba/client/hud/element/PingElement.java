package dev.px.karatsuba.client.hud.element;

import dev.px.core.Core;
import dev.px.gui.legacy.GuiStyle;
import dev.px.core.hud.AbstractHudElement;
import dev.px.core.hud.Anchor;
import dev.px.core.hud.HudLayout;
import dev.px.core.layout.Align;
import dev.px.core.layout.Content;
import dev.px.core.network.lag.LagService;
import dev.px.core.render.Color;
import dev.px.core.setting.impl.BooleanSetting;
import dev.px.core.setting.impl.ColorSetting;
import dev.px.core.setting.impl.NumberSetting;

/**
 * Ping, and how long the server has been silent when it has.
 *
 * <p>Both come from {@code Core.lag()}. The ping is the server's own measurement
 * of the connection, the only one a client can honestly show; while the server
 * has gone quiet the value is replaced by how long for, because a ping that is
 * seconds out of date is the least useful number on screen at that moment.
 */
public final class PingElement extends AbstractHudElement {

    private static final float PADDING = 4f;
    private static final float LABEL_GAP = 4f;

    private static final int SAMPLE = 48;

    private final BooleanSetting showLag = bool("Show Lag", true)
            .describe("Show how long the server has been silent instead of the ping while it is");

    private final BooleanSetting colourByHealth = bool("Colour by Health", true);

    private final NumberSetting<Integer> goodPing = integer("Good Below", 80, 10, 500)
            .describe("Milliseconds under which the ping shows green")
            .visibleWhen(colourByHealth);

    private final NumberSetting<Integer> badPing = integer("Bad Above", 150, 10, 1000)
            .describe("Milliseconds over which the ping shows red")
            .visibleWhen(colourByHealth);

    private final ColorSetting labelColor = color("Label Colour", Color.of(150, 160, 180));

    private final ColorSetting valueColor = color("Value Colour", Color.WHITE)
            .visibleWhen(() -> !colourByHealth.isOn());

    public PingElement() {
        super("ping", "Ping", HudLayout.at(Anchor.BOTTOM_RIGHT, -4f, -4f));
    }

    @Override
    public void content(Content content) {
        LagService lag = Core.lag();

        content.background(GuiStyle.surface(), GuiStyle.radius()).padding(PADDING);
        content.min(40f, 12f);
        content.row(row -> {
            row.align(Align.CENTER);
            if (!isEditing() && showLag.isOn() && lag.isLagging()) {
                row.text("Lag", labelColor.resolve());
                row.space(LABEL_GAP, 0f);
                row.text(String.format("%.1fs", lag.getLagMillis() / 1000d), Color.RED);
                return;
            }
            int ping = isEditing() ? SAMPLE : lag.getPing();
            row.text("Ping", labelColor.resolve());
            row.space(LABEL_GAP, 0f);
            row.text(ping < 0 ? "--" : ping + "ms", colour(ping));
        });
    }

    private Color colour(int ping) {
        if (!colourByHealth.isOn()) {
            return valueColor.resolve();
        }
        if (ping < 0) {
            return Color.GRAY;
        }
        if (ping <= goodPing.getInt()) {
            return Color.GREEN;
        }
        return ping <= badPing.getInt() ? Color.YELLOW : Color.RED;
    }
}
