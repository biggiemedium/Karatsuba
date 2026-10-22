package dev.px.karatsuba.client.hud.element;

import dev.px.core.hud.Anchor;
import dev.px.core.hud.HudLayout;
import dev.px.core.layout.Align;
import dev.px.core.layout.Content;
import dev.px.core.math.Vec3;
import dev.px.core.setting.impl.BooleanSetting;
import dev.px.core.setting.impl.EnumSetting;
import dev.px.karatsuba.adapter.game.player.Players;
import dev.px.karatsuba.client.hud.preset.TextRole;
import dev.px.karatsuba.client.hud.style.StyledElement;


/**
 * Where the player is, in three rows or on one line.
 *
 * <p>Each number keeps the width of the widest it is likely to be, so
 * walking from x 9 to x 10 neither shifts the column nor sets the box resizing:
 * coordinates change every tick, which is exactly what fixed widths are for.
 */
public final class CoordinatesElement extends StyledElement {

    private static final Vec3 SAMPLE = Vec3.of(-1337.5d, 64d, 420.25d);

    public enum Precision { WHOLE, TENTHS, HUNDREDTHS }

    public enum Arrangement { STACKED, INLINE }

    private final EnumSetting<Arrangement> arrangement = enumOf("Arrangement", Arrangement.STACKED)
            .describe("Three rows, or X, Y and Z on one line");

    private final BooleanSetting labels = bool("Labels", true)
            .describe("Show X, Y and Z rather than the numbers alone");

    private final EnumSetting<Precision> precision = enumOf("Precision", Precision.WHOLE);

    public CoordinatesElement() {
        super("coordinates", "Coordinates", HudLayout.at(Anchor.BOTTOM_LEFT, 4f, -4f));
    }

    @Override
    protected void describe(Content body) {
        Vec3 at = isEditing() ? SAMPLE : Players.position();
        if (arrangement.get() == Arrangement.INLINE) {
            body.row(row -> {
                row.align(Align.CENTER);
                axis(row, "X", at.getX());
                row.space(LABEL_GAP * 2f, 0f);
                axis(row, "Y", at.getY());
                row.space(LABEL_GAP * 2f, 0f);
                axis(row, "Z", at.getZ());
            });
            return;
        }
        stacked(body, "X", at.getX());
        stacked(body, "Y", at.getY());
        stacked(body, "Z", at.getZ());
    }

    private void stacked(Content body, String name, double value) {
        body.row(row -> {
            row.align(Align.CENTER);
            axis(row, name, value);
        });
    }

    private void axis(Content row, String name, double value) {
        if (labels.isOn()) {
            label(row, name);
            row.space(LABEL_GAP, 0f);
        }
        fixed(row, format(value), widest(), TextRole.VALUE);
    }

    private String format(double value) {
        return switch (precision.get()) {
            case WHOLE -> String.valueOf((long) Math.floor(value));
            case TENTHS -> String.format("%.1f", value);
            case HUNDREDTHS -> String.format("%.2f", value);
        };
    }

    /** As wide as a coordinate usually gets; farther out, a number simply takes its own width. */
    private String widest() {
        return switch (precision.get()) {
            case WHOLE -> "-00000";
            case TENTHS -> "-00000.0";
            case HUNDREDTHS -> "-00000.00";
        };
    }
}
