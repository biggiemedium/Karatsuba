package dev.px.karatsuba.client.module.combat.render;

import dev.px.core.math.Box;
import dev.px.core.math.Vec3;
import dev.px.core.render.Color;
import dev.px.core.render.Render;
import dev.px.core.render.animation.Animation;
import dev.px.core.render.animation.Easing;
import dev.px.core.render.animation.PresenceTracker;

/**
 * The box an aura draws where it is placing: popped in, faded out, and slid to
 * the next placement when interpolating. Shared so every aura moves alike.
 *
 * <p>Two halves, like every animated module here: {@link #update} on the tick
 * says which box is shown and where a slide is heading; {@link #draw} every
 * frame reads that and draws.
 *
 * <ul>
 *   <li><b>Not interpolating:</b> each placement is its own box, keyed by where it
 *       is, so a new one pops in while the old fades out.</li>
 *   <li><b>Interpolating:</b> one box, which slides &mdash; both corners, so a bed
 *       turning from north to east swings round rather than jumping. A box that
 *       was not showing appears where it is, and one that changes target
 *       mid-slide turns from wherever it is drawn.</li>
 * </ul>
 *
 * <p>Game thread only, like the tick and the frame that drive it.
 */
public final class PlacementBox {

    /** The one key every placement shares while interpolating. */
    private static final Object SLIDING = new Object();

    private final PresenceTracker<Object, Shown> boxes = PresenceTracker.popIn();

    private Box slideFrom;
    private Box slideTo;
    private final Animation slide = Animation.of(1f, 150L, Easing.CUBIC_OUT);

    /** Whether the sliding box was shown last tick. */
    private boolean sliding;

    /**
     * Sets this tick's box.
     *
     * @param target      the box to show, or null for none: whatever was shown fades out
     * @param animated    whether to pop in and fade out; off, boxes appear and vanish at once
     * @param interpolate whether to slide one box between placements
     * @param moveMillis  how long a slide takes
     */
    public void update(Box target, boolean animated, boolean interpolate, int moveMillis) {
        boxes.setAnimated(animated);
        boxes.begin();
        if (target != null) {
            if (interpolate) {
                slideToward(target, moveMillis);
                boxes.report(SLIDING, true, new Shown(target, true));
            } else {
                boxes.report(key(target), true, new Shown(target, false));
            }
        }
        sliding = target != null && interpolate;
        boxes.end();
    }

    /** Draws every box on screen, each grown out from its middle by how present it is. */
    public void draw(Color color, boolean fill, float fillOpacity, float thickness) {
        if (boxes.size() == 0) {
            return;
        }
        for (PresenceTracker.Entry<Shown> entry : boxes.entries()) {
            Shown shown = entry.value();
            drawOne(shown.sliding() ? slid() : shown.box(), entry.presence(), entry.alpha(),
                    color, fill, fillOpacity, thickness);
        }
    }

    public void clear() {
        boxes.clear();
        slideFrom = null;
        slideTo = null;
        sliding = false;
    }

    private void slideToward(Box target, int moveMillis) {
        if (!sliding || slideTo == null) {
            slideFrom = target;
            slideTo = target;
            slide.snapTo(1f);
            return;
        }
        if (same(target, slideTo)) {
            return;
        }
        slideFrom = slid();
        slideTo = target;
        slide.duration(moveMillis).snapTo(0f).target(1f);
    }

    /** Where the sliding box is drawn this frame: both corners eased toward the target's. */
    private Box slid() {
        if (slideFrom == null || slideTo == null) {
            return slideTo;
        }
        float progress = slide.get();
        return Box.of(slideFrom.getMin().lerp(slideTo.getMin(), progress),
                slideFrom.getMax().lerp(slideTo.getMax(), progress));
    }

    /**
     * One box at some point in its animation: grown out from its middle as it
     * appears, the pop-in's overshoot swelling it a touch past full size before
     * it settles, and faded as it goes.
     */
    private static void drawOne(Box box, float presence, float alpha, Color color,
                                boolean fill, float fillOpacity, float thickness) {
        double scale = Math.max(0f, presence);
        if (box == null || scale <= 0d || alpha <= 0f) {
            return;
        }
        Vec3 centre = box.getCenter();
        double halfX = box.getWidth() / 2d * scale;
        double halfY = box.getHeight() / 2d * scale;
        double halfZ = box.getDepth() / 2d * scale;
        Box drawn = Box.of(centre.getX() - halfX, centre.getY() - halfY, centre.getZ() - halfZ,
                centre.getX() + halfX, centre.getY() + halfY, centre.getZ() + halfZ);
        if (fill) {
            Render.box(drawn, color.fade(alpha * fillOpacity));
        }
        Render.boxOutline(drawn, thickness, color.fade(alpha));
    }

    /** Boxes have no equality of their own; their corners do. */
    private static Object key(Box box) {
        return java.util.List.of(box.getMin(), box.getMax());
    }

    private static boolean same(Box a, Box b) {
        return a.getMin().equals(b.getMin()) && a.getMax().equals(b.getMax());
    }

    private record Shown(Box box, boolean sliding) {
    }
}
