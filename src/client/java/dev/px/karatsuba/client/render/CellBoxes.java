package dev.px.karatsuba.client.render;

import dev.px.core.math.Box;
import dev.px.core.math.Vec3i;
import dev.px.core.render.Color;
import dev.px.core.render.Render;
import dev.px.core.render.animation.PresenceTracker;

import java.util.Collection;

/**
 * The block cells a module is working on: each popped in when it is chosen and
 * faded out once filled, mined or given up. Shared across categories (Surround,
 * Hole Fill and Web Aura in Combat, Smart Mine in Mining) so they all move alike,
 * and like Hole ESP.
 *
 * <p>{@link #update} on the tick with the cells to show; {@link #draw} every frame.
 * Game thread only.
 */
public final class CellBoxes {

    private final PresenceTracker<Vec3i, Vec3i> cells = PresenceTracker.popIn();

    /**
     * Sets this tick's cells. Cells no longer given fade out where they were.
     *
     * @param animated whether to pop in and fade out; off, cells appear and vanish at once
     */
    public void update(Collection<Vec3i> shown, boolean animated) {
        cells.setAnimated(animated);
        cells.begin();
        for (Vec3i cell : shown) {
            cells.report(cell, true, cell);
        }
        cells.end();
    }

    /** Draws every cell on screen, each grown out from its middle by how present it is. */
    public void draw(Color color, boolean fill, float fillOpacity, float thickness) {
        if (cells.size() == 0) {
            return;
        }
        for (PresenceTracker.Entry<Vec3i> entry : cells.entries()) {
            double scale = Math.max(0f, entry.presence());
            float alpha = entry.alpha();
            if (scale <= 0d || alpha <= 0f) {
                continue;
            }
            Vec3i cell = entry.value();
            double half = 0.5d * scale;
            double x = cell.getX() + 0.5d;
            double y = cell.getY() + 0.5d;
            double z = cell.getZ() + 0.5d;
            Box box = Box.of(x - half, y - half, z - half, x + half, y + half, z + half);
            if (fill) {
                Render.box(box, color.fade(alpha * fillOpacity));
            }
            Render.boxOutline(box, thickness, color.fade(alpha));
        }
    }

    public void clear() {
        cells.clear();
    }
}
