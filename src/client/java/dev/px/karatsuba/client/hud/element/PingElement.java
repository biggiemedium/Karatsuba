package dev.px.karatsuba.client.hud.element;

import dev.px.core.Core;
import dev.px.core.hud.Anchor;
import dev.px.core.hud.HudLayout;
import dev.px.core.layout.Align;
import dev.px.core.layout.Content;
import dev.px.core.network.lag.LagService;
import dev.px.core.render.Color;
import dev.px.core.setting.impl.BooleanSetting;
import dev.px.core.setting.impl.NumberSetting;
import dev.px.karatsuba.client.hud.preset.role.ColorRole;
import dev.px.karatsuba.client.hud.preset.role.TextRole;
import dev.px.karatsuba.client.hud.style.StyledElement;

/**
 * Ping, and how long the server has been silent when it has.
 *
 * <p>Both come from {@code Core.lag()}. The ping is the server's own measurement
 * of the connection, the only one a client can honestly show; while the server
 * has gone quiet the value is replaced by how long for, because a ping that is
 * seconds out of date is the least useful number on screen at that moment.
 *
 * <p>The value keeps one width whatever it reads, so the
 * element never twitches as the ping moves, and the bars say how good it is by
 * how many are lit as well as by colour.
 */
public final class PingElement extends StyledElement {

    private static final int SAMPLE = 48;

    private final BooleanSetting showLag = bool("Show Lag", true)
            .describe("Show how long the server has been silent instead of the ping while it is");

    private final BooleanSetting showBars = bool("Quality Bars", true)
            .describe("Three bars, more lit the better the connection");

    private final BooleanSetting colourByHealth = bool("Colour by Health", false)
            .describe("Colour the value and bars by how good the ping is");

    private final NumberSetting<Integer> goodPing = integer("Good Below", 80, 10, 500)
            .describe("Milliseconds under which the ping counts as good");

    private final NumberSetting<Integer> badPing = integer("Bad Above", 150, 10, 1000)
            .describe("Milliseconds over which the ping counts as bad");

    public PingElement() {
        super("ping", "Ping", HudLayout.at(Anchor.BOTTOM_RIGHT, -4f, -4f));
    }

    @Override
    protected void describe(Content body) {
        LagService lag = Core.lag();
        if (!isEditing() && showLag.isOn() && lag.isLagging()) {
            // A lag spike is the change most worth seeing, so it cues.
            watch("lag");
            Color colour = colourByHealth.isOn() ? tokens().color(ColorRole.BAD) : tokens().text(TextRole.VALUE);
            line(body, "Lag", String.format("%.1fs", lag.getLagMillis() / 1000d), "99.9s", 0, colour);
            return;
        }
        int ping = isEditing() ? SAMPLE : lag.getPing();
        int level = ping < 0 ? 0 : ping <= goodPing.getInt() ? 3 : ping <= badPing.getInt() ? 2 : 1;
        // By health, not by millisecond: a ping moving within "good" is not news.
        watch(level);
        line(body, "Ping", ping < 0 ? "--" : ping + "ms", "999ms", level, colour(ping, level));
    }

    private void line(Content body, String label, String value, String widest, int level, Color colour) {
        body.row(row -> {
            row.align(Align.CENTER);
            label(row, label);
            row.space(LABEL_GAP, 0f);
            fixed(row, value, widest, colour);
            if (showBars.isOn()) {
                row.space(LABEL_GAP, 0f);
                bars(row, level, 3, colour);
            }
        });
    }

    private Color colour(int ping, int level) {
        if (ping < 0) {
            return tokens().text(TextRole.SECONDARY);
        }
        return colourByHealth.isOn() ? health(level) : tokens().text(TextRole.VALUE);
    }
}
