package dev.px.karatsuba.client.hud.element;

import dev.px.core.hud.Anchor;
import dev.px.core.hud.HudLayout;
import dev.px.core.layout.Content;
import dev.px.core.render.Color;
import dev.px.core.render.Render;
import dev.px.core.render.font.Font;
import dev.px.core.setting.impl.BooleanSetting;
import dev.px.core.setting.impl.NumberSetting;
import dev.px.karatsuba.adapter.game.player.Players;
import dev.px.karatsuba.client.hud.preset.role.TextRole;
import dev.px.karatsuba.client.hud.style.StyledElement;

/**
 * Which way the player faces, as a strip that scrolls under a fixed centre mark:
 * N, E, S and W labelled and ticked, NE, SE, SW and NW fainter, a small tick every
 * 15 degrees, and the heading in degrees under the mark.
 *
 * <p>The strip shows a quarter turn across its width, whatever the width, so a wider
 * compass is finer rather than wider-seeing. Labels and ticks fade towards its ends,
 * so they come into view rather than appearing at the edge. Everything is in the
 * preset's text colours: primary for the cardinals and the mark, softer for the rest
 * (white on the Apple HUD's dark glass).
 */
public final class CompassElement extends StyledElement {

    /** How much of a turn the strip spans, in degrees. */
    private static final float SPAN = 90f;

    /** Every tick, in degrees: the cardinals and intercardinals are among them. */
    private static final int TICK_STEP = 15;

    /** Under the labels: how long a cardinal's tick is, an intercardinal's, and a plain one's. */
    private static final float TICK_CARDINAL = 4f;
    private static final float TICK_INTERCARDINAL = 3f;
    private static final float TICK_MINOR = 2f;
    private static final float TICK_WEIGHT = 1f;

    /** The centre mark: a line a little longer than the longest tick. */
    private static final float MARK_LENGTH = 6f;
    private static final float MARK_WEIGHT = 1.5f;

    /** Between the labels and the ticks, and between the ticks and the heading. */
    private static final float LABEL_GAP_BELOW = 1f;
    private static final float HEADING_GAP = 2f;

    /** How far in from each end the labels and ticks fade in, as a share of the width. */
    private static final float EDGE_FADE = 0.18f;

    /** Which way the editor's sample faces, so the strip shows a cardinal and its neighbours. */
    private static final float SAMPLE_HEADING = 30f;

    private static final String[] NAMES = {"N", "NE", "E", "SE", "S", "SW", "W", "NW"};

    private final NumberSetting<Integer> width = integer("Width", 100, 60, 200)
            .describe("How long the strip is; it always shows a quarter turn");
    private final BooleanSetting showDegrees = bool("Show Degrees", true)
            .describe("The heading in degrees, under the centre mark");

    public CompassElement() {
        super("compass", "Compass", HudLayout.at(Anchor.TOP_CENTER, 0f, 40f));
    }

    @Override
    protected void describe(Content body) {
        if (!Players.present() && !isEditing()) {
            return;
        }
        float heading = Players.present() ? heading(Players.rotation().getYaw()) : SAMPLE_HEADING;
        Font labelFont = tokens().font(TextRole.PRIMARY);
        Font degreeFont = tokens().font(TextRole.SECONDARY);
        Color primary = tokens().text(TextRole.PRIMARY);
        Color secondary = tokens().text(TextRole.SECONDARY);
        Color tertiary = tokens().text(TextRole.TERTIARY);

        float w = width.getInt();
        float labelHeight = labelFont.getHeight();
        float tickTop = labelHeight + LABEL_GAP_BELOW;
        float stripHeight = tickTop + MARK_LENGTH;
        float h = stripHeight + (showDegrees.isOn() ? HEADING_GAP + degreeFont.getHeight() : 0f);
        String degrees = Math.round(heading) % 360 + "°";

        body.custom(w, h, (x, y, partW, partH) -> {
            float centre = x + partW / 2f;
            Render.pushClip(x, y, partW, stripHeight);
            try {
                for (int angle = 0; angle < 360; angle += TICK_STEP) {
                    float dx = offset(angle, heading) * partW / SPAN;
                    float fade = edgeFade(dx, partW);
                    if (fade <= 0f) {
                        continue;
                    }
                    float at = centre + dx;
                    if (angle % 90 == 0) {
                        label(labelFont, NAMES[angle / 45], at, y, primary.fade(fade));
                        tick(at, y + tickTop, TICK_CARDINAL, primary.fade(fade));
                    } else if (angle % 45 == 0) {
                        label(labelFont, NAMES[angle / 45], at, y, secondary.fade(fade));
                        tick(at, y + tickTop, TICK_INTERCARDINAL, secondary.fade(fade));
                    } else {
                        tick(at, y + tickTop, TICK_MINOR, tertiary.fade(fade));
                    }
                }
            } finally {
                Render.popClip();
            }
            // The mark, over the ticks, from the top of the tick band down.
            Render.line(centre, y + tickTop, centre, y + tickTop + MARK_LENGTH, MARK_WEIGHT, primary);
            if (showDegrees.isOn()) {
                Render.text(degreeFont, degrees, centre - degreeFont.widthOf(degrees) / 2f,
                        y + stripHeight + HEADING_GAP, secondary);
            }
        });
    }

    /** @return the compass heading for the game's yaw: 0 north, 90 east, 180 south, 270 west */
    private static float heading(float yaw) {
        // The game's yaw is 0 facing south and grows clockwise from above, so north is 180.
        float heading = (yaw + 180f) % 360f;
        return heading < 0f ? heading + 360f : heading;
    }

    /** @return how far {@code angle} is from {@code heading}, the short way round, in degrees: -180 to 180 */
    private static float offset(float angle, float heading) {
        float delta = (angle - heading) % 360f;
        if (delta < -180f) {
            delta += 360f;
        } else if (delta >= 180f) {
            delta -= 360f;
        }
        return delta;
    }

    /** @return how visible something {@code dx} from the centre is: whole in the middle, fading to nothing at the ends */
    private static float edgeFade(float dx, float width) {
        float fromEnd = width / 2f - Math.abs(dx);
        return Math.max(0f, Math.min(1f, fromEnd / (width * EDGE_FADE)));
    }

    private static void label(Font font, String name, float centreX, float top, Color colour) {
        Render.text(font, name, centreX - font.widthOf(name) / 2f, top, colour);
    }

    private static void tick(float x, float top, float length, Color colour) {
        Render.line(x, top, x, top + length, TICK_WEIGHT, colour);
    }
}
