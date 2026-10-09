package dev.px.karatsuba.client.hud.element;

import dev.px.core.Core;
import dev.px.gui.legacy.GuiStyle;
import dev.px.core.hud.AbstractHudElement;
import dev.px.core.hud.Anchor;
import dev.px.core.hud.HudLayout;
import dev.px.core.layout.Align;
import dev.px.core.layout.Content;
import dev.px.core.network.tps.TpsService;
import dev.px.core.render.Color;
import dev.px.core.setting.impl.BooleanSetting;
import dev.px.core.setting.impl.ColorSetting;

/**
 * The server's tick rate.
 *
 * <p>Read straight from {@code Core.tps()}, which estimates it from the server's
 * clock; nothing here measures anything. Before two clock updates have arrived
 * there is no estimate, and the element says so rather than showing the target
 * as if it had been measured.
 *
 * <p>Coloured against the server's own target rather than a hard-coded 20, so a
 * server running {@code /tick rate 10} on purpose does not read as dying.
 */
public final class TpsElement extends AbstractHudElement {

    private static final float PADDING = 4f;
    private static final float LABEL_GAP = 4f;

    /** At or above this share of the target, the server is keeping up. */
    private static final double HEALTHY = 0.9d;

    /** Below this share of the target, it is visibly struggling. */
    private static final double STRUGGLING = 0.75d;

    private static final double SAMPLE = 19.8d;

    private final BooleanSetting colourByHealth = bool("Colour by Health", true)
            .describe("Green, yellow or red against the server's target rate");

    private final ColorSetting labelColor = color("Label Colour", Color.of(150, 160, 180));

    private final ColorSetting valueColor = color("Value Colour", Color.WHITE)
            .visibleWhen(() -> !colourByHealth.isOn());

    public TpsElement() {
        super("tps", "TPS", HudLayout.at(Anchor.BOTTOM_RIGHT, -4f, -20f));
    }

    @Override
    public void content(Content content) {
        TpsService tps = Core.tps();
        boolean measured = isEditing() || tps.hasEstimate();
        double value = isEditing() ? SAMPLE : tps.getTps();

        content.background(GuiStyle.surface(), GuiStyle.radius()).padding(PADDING);
        content.min(40f, 12f);
        content.row(row -> {
            row.align(Align.CENTER);
            row.text("TPS", labelColor.resolve());
            row.space(LABEL_GAP, 0f);
            row.text(measured ? String.format("%.1f", value) : "--", colour(measured, value, tps));
        });
    }

    private Color colour(boolean measured, double value, TpsService tps) {
        if (!colourByHealth.isOn()) {
            return valueColor.resolve();
        }
        if (!measured) {
            return Color.GRAY;
        }
        double target = tps.getTargetTps();
        double share = target > 0d && !Double.isNaN(target) ? value / target : 1d;
        if (share >= HEALTHY) {
            return Color.GREEN;
        }
        return share >= STRUGGLING ? Color.YELLOW : Color.RED;
    }
}
