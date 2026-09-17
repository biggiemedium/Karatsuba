package dev.px.karatsuba.client.hud;

import dev.px.core.gui.GuiStyle;
import dev.px.core.hud.AbstractHudElement;
import dev.px.core.hud.Anchor;
import dev.px.core.hud.HudLayout;
import dev.px.core.layout.Align;
import dev.px.core.layout.Content;
import dev.px.core.math.Vec3;
import dev.px.core.render.Color;
import dev.px.core.setting.impl.BooleanSetting;
import dev.px.core.setting.impl.ColorSetting;
import dev.px.core.setting.impl.EnumSetting;
import dev.px.karatsuba.adapter.game.Players;

/**
 * Where the player is.
 *
 * <p>The worked example for a <b>HUD element with settings</b>, and for the box
 * model doing the work a hand-written {@code render} used to. Three rows of a label
 * and a value, right-aligned values, one background: expressed as a column of rows
 * with a {@code fill()} between each pair, and measured by Core.
 *
 * <p>Nothing here computes a size, a position or a width. There is no
 * {@code getPreferredSize}, because the description below <em>is</em> the size:
 * Core measures it, anchors it, clamps it, scales it and derives the clickable
 * region from the background it was given. Changing the padding changes the size
 * and the background together, because they are the same statement.
 *
 * <p>{@link #isEditing()} is the edit-mode hook, and the branch is at the point the
 * data comes from rather than around the drawing. Everything below it is identical
 * in both modes, which is the whole trick: an element that laid itself out
 * differently while being positioned would be positioned wrong.
 */
public final class CoordinatesElement extends AbstractHudElement {

    private static final float PADDING = 4f;
    private static final float ROW_GAP = 1f;
    private static final float LABEL_GAP = 6f;

    /** A stable sample while positioning, so the box does not resize under the cursor. */
    private static final Vec3 SAMPLE = Vec3.of(-1337.5d, 64d, 420.25d);

    private final BooleanSetting labels = bool("Labels", true)
            .describe("Show X, Y and Z rather than the numbers alone");

    private final EnumSetting<Precision> precision = enumOf("Precision", Precision.WHOLE);

    private final ColorSetting labelColor = color("Label Colour", Color.of(150, 160, 180))
            .visibleWhen(labels);

    private final ColorSetting valueColor = color("Value Colour", Color.WHITE);

    public CoordinatesElement() {
        super("coordinates", "Coordinates", HudLayout.at(Anchor.BOTTOM_LEFT, 4f, -4f));
    }

    @Override
    public void content(Content content) {
        Vec3 at = isEditing() ? SAMPLE : Players.position();

        content.background(GuiStyle.surface(), GuiStyle.radius()).padding(PADDING).gap(ROW_GAP);
        // STRETCH so each row gets the column's full width; without it every row
        // would be only as wide as its own text and the values would not line up.
        content.align(Align.STRETCH);
        // A floor, so the element stays grabbable before a font has loaded.
        content.min(70f, 12f);

        row(content, "X", at.getX());
        row(content, "Y", at.getY());
        row(content, "Z", at.getZ());
    }

    /**
     * One axis: label on the left, value hard against the right.
     *
     * <p>{@code fill()} is what does it. The row has a width because the column
     * stretched it, so the empty part absorbs the slack and pushes the value over
     * &mdash; the same three-part idiom every setting row in the GUI uses.
     */
    private void row(Content content, String axis, double value) {
        content.row(row -> {
            row.align(Align.CENTER);
            if (labels.isOn()) {
                row.text(axis, labelColor.resolve());
                row.space(LABEL_GAP, 0f);
            }
            row.fill();
            row.text(format(value), valueColor.resolve());
        });
    }

    private String format(double value) {
        return switch (precision.get()) {
            case WHOLE -> String.valueOf((int) Math.floor(value));
            case TENTHS -> String.format("%.1f", value);
            case HUNDREDTHS -> String.format("%.2f", value);
        };
    }

    public enum Precision {
        WHOLE,
        TENTHS,
        HUNDREDTHS
    }
}
