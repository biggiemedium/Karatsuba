package dev.px.karatsuba.client.module.mining;

import dev.px.core.math.Box;
import dev.px.core.math.Vec3;
import dev.px.core.math.Vec3i;
import dev.px.core.render.Color;
import dev.px.core.render.Render;
import dev.px.core.render.animation.Easing;
import dev.px.core.render.animation.PresenceTracker;
import dev.px.karatsuba.adapter.world.Blocks;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;

/**
 * The block being mined, drawn as a box that grows out of its middle as the dig
 * goes, the way Shoreline's Speed Mine draws it.
 *
 * <ul>
 *   <li><b>Size</b> is the dig's progress, eased ({@link Easing#SINE_IN_OUT}) and
 *       carried on between ticks at the pace of the last one, so it grows smoothly at
 *       any frame rate rather than stepping twenty times a second.</li>
 *   <li><b>Colour</b> runs from the start colour to the finish colour with the
 *       progress.</li>
 *   <li><b>Appearing and going</b> fade over {@link #FADE_MILLIS}. A block that was
 *       mined fades out at full size; one given up on fades out where it got to.</li>
 * </ul>
 *
 * <p>{@link #update} on the tick, {@link #draw} every frame. Game thread only.
 */
public final class MiningBox {

    /** How long a box takes to fade in or out, as Shoreline's. */
    private static final long FADE_MILLIS = 300L;

    /** Lifted off the block face, so the box does not fight it for depth. */
    private static final double SURFACE_OFFSET = 0.002d;

    private final PresenceTracker<Vec3i, Dig> boxes =
            new PresenceTracker<>(FADE_MILLIS, Easing.SINE_IN_OUT, FADE_MILLIS, Easing.SINE_IN_OUT);

    /** Each shown block's dig, kept while its box is on screen. */
    private final Map<Vec3i, Dig> digs = new HashMap<>();

    /**
     * Sets this tick's state.
     *
     * @param mining   the block being mined, or null for none: any other fades out
     * @param progress how far through it the game is, 0 to 1
     */
    public void update(Vec3i mining, float progress, boolean animated) {
        boxes.setAnimated(animated);
        boxes.begin();
        if (mining != null) {
            Dig dig = digs.computeIfAbsent(mining, Dig::new);
            dig.advance(progress);
            boxes.report(mining, true, dig);
        }
        boxes.end();
        for (Dig dig : digs.values()) {
            if (!dig.block.equals(mining) && !dig.done && Blocks.isAir(dig.block)) {
                // Gone: it was mined, so it leaves whole.
                dig.done = true;
            }
        }
        digs.keySet().removeIf(block -> !boxes.has(block));
    }

    /**
     * Draws every box.
     *
     * @param colour the colour at a progress from 0 (start) to 1 (finish)
     */
    public void draw(float partialTicks, Function<Float, Color> colour, float fillOpacity, float thickness) {
        for (PresenceTracker.Entry<Dig> entry : boxes.entries()) {
            Dig dig = entry.value();
            float alpha = entry.alpha();
            float progress = dig.progress(entry.isWanted() ? partialTicks : 1f);
            double scale = dig.done ? 1d : Easing.SINE_IN_OUT.apply(progress);
            if (alpha <= 0f || scale <= 0d) {
                continue;
            }
            Box drawn = scaled(Blocks.outline(dig.block).expand(SURFACE_OFFSET), scale);
            Color color = colour.apply(dig.done ? 1f : progress);
            Render.box(drawn, color.fade(alpha * fillOpacity));
            Render.boxOutline(drawn, thickness, color.fade(alpha));
        }
    }

    public void clear() {
        boxes.clear();
        digs.clear();
    }

    /** The box scaled about its centre. */
    private static Box scaled(Box box, double scale) {
        Vec3 centre = box.getCenter();
        Vec3 half = box.getMax().subtract(centre).scale(scale);
        return Box.of(centre.subtract(half), centre.add(half));
    }

    /** One block's dig: its progress this tick and last, to carry it on between them. */
    private static final class Dig {

        private final Vec3i block;
        private float last;
        private float now;
        private boolean done;

        private Dig(Vec3i block) {
            this.block = block;
        }

        private void advance(float progress) {
            last = progress < now ? progress : now;
            now = progress;
        }

        /** Between last tick's progress and this tick's, carried on by the same step. */
        private float progress(float partialTicks) {
            float step = now - last;
            float value = now + step * partialTicks;
            return value < 0f ? 0f : value > 1f ? 1f : value;
        }
    }
}
