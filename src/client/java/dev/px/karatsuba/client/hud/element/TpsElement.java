package dev.px.karatsuba.client.hud.element;

import dev.px.core.Core;
import dev.px.core.hud.Anchor;
import dev.px.core.hud.HudLayout;
import dev.px.core.layout.Align;
import dev.px.core.layout.Content;
import dev.px.core.network.tps.TpsService;
import dev.px.core.render.Color;
import dev.px.core.setting.impl.BooleanSetting;
import dev.px.karatsuba.client.hud.preset.role.TextRole;
import dev.px.karatsuba.client.hud.style.StyledElement;

/**
 * The server's tick rate.
 *
 * <p>Read straight from {@code Core.tps()}, which estimates it from the server's
 * clock; nothing here measures anything. Before two clock updates have arrived
 * there is no estimate, and the element says so rather than showing the target
 * as if it had been measured.
 *
 * <p>Judged against the server's own target rather than a hard-coded 20, so a
 * server running {@code /tick rate 10} on purpose does not read as dying. A value
 * of fixed width, and bars that carry the state by shape.
 */
public final class TpsElement extends StyledElement {

    /** At or above this share of the target, the server is keeping up. */
    private static final double HEALTHY = 0.9d;

    /** Below this share of the target, it is visibly struggling. */
    private static final double STRUGGLING = 0.75d;

    private static final double SAMPLE = 19.8d;

    private final BooleanSetting showBars = bool("Quality Bars", true)
            .describe("Three bars, more lit the closer the server is to its target rate");

    private final BooleanSetting colourByHealth = bool("Colour by Health", false)
            .describe("Colour the value and bars against the server's target rate");

    public TpsElement() {
        super("tps", "TPS", HudLayout.at(Anchor.BOTTOM_RIGHT, -4f, -24f));
    }

    @Override
    protected void describe(Content body) {
        TpsService tps = Core.tps();
        boolean measured = isEditing() || tps.hasEstimate();
        double value = isEditing() ? SAMPLE : tps.getTps();
        int level = measured ? level(value, isEditing() ? 20d : tps.getTargetTps()) : 0;
        Color colour = !measured ? tokens().text(TextRole.SECONDARY) : colourByHealth.isOn() ? health(level) : tokens().text(TextRole.VALUE);
        watch(level);

        body.row(row -> {
            row.align(Align.CENTER);
            label(row, "TPS");
            row.space(LABEL_GAP, 0f);
            fixed(row, measured ? String.format("%.1f", value) : "--", "20.0", colour);
            if (showBars.isOn()) {
                row.space(LABEL_GAP, 0f);
                bars(row, level, 3, colour);
            }
        });
    }

    private static int level(double value, double target) {
        double share = target > 0d && !Double.isNaN(target) ? value / target : 1d;
        return share >= HEALTHY ? 3 : share >= STRUGGLING ? 2 : 1;
    }
}
