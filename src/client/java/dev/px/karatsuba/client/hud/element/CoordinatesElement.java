package dev.px.karatsuba.client.hud.element;

import dev.px.core.hud.Anchor;
import dev.px.core.hud.HudLayout;
import dev.px.core.layout.Align;
import dev.px.core.layout.Content;
import dev.px.core.math.Vec3;
import dev.px.core.setting.impl.BooleanSetting;
import dev.px.core.setting.impl.EnumSetting;
import dev.px.karatsuba.adapter.game.player.Players;
import dev.px.karatsuba.client.hud.preset.role.TextRole;
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

    /** Between X, Y and Z on one line. */
    private static final float INLINE_GAP = 3.5f;

    /**
     * How wide each inline number usually gets: X and Z to within ten thousand either
     * way, Y across the world's height (-64 to 320). Past these a number takes its own
     * width, so walking far out widens the line rather than clipping it.
     */
    private static final String HORIZONTAL_WIDEST = "-0000";
    private static final String HEIGHT_WIDEST = "000";

    /** Added between the stacked rows, on top of the preset's own row gap. */
    private static final float STACKED_EXTRA_GAP = 1.5f;

    public enum Precision { WHOLE, TENTHS }

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
                // Each slot as wide as its own axis usually gets, so the line is no longer than it needs.
                axis(row, "X", at.getX(), inline(HORIZONTAL_WIDEST));
                row.space(INLINE_GAP, 0f);
                axis(row, "Y", at.getY(), inline(HEIGHT_WIDEST));
                row.space(INLINE_GAP, 0f);
                // The last number has nothing after it to keep still, so it takes its own width
                // and leaves no empty slot at the end; the box only eases when Z gains a digit.
                axis(row, "Z", at.getZ(), "");
            });
            return;
        }
        // Their own column, so the extra room is added once between rows, not on top of a spacer's gaps.
        Align inner = alignment();
        body.column(rows -> {
            rows.align(inner).gap(tokens().gap() + STACKED_EXTRA_GAP);
            stacked(rows, "X", at.getX());
            stacked(rows, "Y", at.getY());
            stacked(rows, "Z", at.getZ());
        });
    }

    private void stacked(Content body, String name, double value) {
        body.row(row -> {
            row.align(Align.CENTER);
            axis(row, name, value, stackedWidest());
        });
    }

    private void axis(Content row, String name, double value, String widest) {
        if (labels.isOn()) {
            label(row, name);
            row.space(LABEL_GAP, 0f);
        }
        fixed(row, format(value), widest, TextRole.VALUE);
    }

    private String format(double value) {
        return switch (precision.get()) {
            case WHOLE -> String.valueOf((long) Math.floor(value));
            case TENTHS -> String.format("%.1f", value);
        };
    }

    /**
     * @return a stacked row's slot: as wide as a coordinate usually gets, without the
     *         minus sign's room (a minus is narrower than a digit, so "-9999" still
     *         fits); farther out, a number simply takes its own width
     */
    private String stackedWidest() {
        return withPrecision("00000");
    }

    /** @return an inline slot, {@code digits} wide (with any sign it allows) at the chosen precision */
    private String inline(String digits) {
        return withPrecision(digits);
    }

    private String withPrecision(String digits) {
        return precision.get() == Precision.TENTHS ? digits + ".0" : digits;
    }
}
