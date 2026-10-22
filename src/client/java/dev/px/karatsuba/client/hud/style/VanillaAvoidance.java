package dev.px.karatsuba.client.hud.style;

import dev.px.core.Core;
import dev.px.core.hud.HudElement;
import dev.px.core.hud.PlacementAdjuster;
import dev.px.core.layout.Bounds;
import dev.px.core.render.animation.Animation;
import dev.px.core.render.animation.Easing;
import dev.px.karatsuba.adapter.game.GameHud;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Moves HUD elements out of the way of the game's own HUD while it is there: the
 * hotbar and status bars, boss bars, effect icons and the scoreboard.
 *
 * <p>Each element is pushed the shortest way out of whatever it overlaps (up off
 * the hotbar, down from boss bars, left of the scoreboard), only for as long as
 * the obstacle shows, and eased there and back so nothing jumps. Nothing is
 * written to its layout: when the boss bar goes, the element returns to where it
 * was put. Off with the HUD Style's Avoid Vanilla HUD, and never in the editor
 * (Core does not ask then), so a drag is never fought.
 */
public final class VanillaAvoidance implements PlacementAdjuster {

    private static final long MOVE_MILLIS = 220L;

    /** Room left between an element and what it moved away from. */
    private static final float MARGIN = 2f;

    /** The zones, read once a frame rather than once per element. */
    private List<Bounds> zones = List.of();
    private long zonesAt = -1L;

    private final Map<String, Animation[]> offsets = new HashMap<>();

    /** Installs the avoidance into Core's HUD. */
    public static void install(Core core) {
        core.getHudService().setAdjuster(new VanillaAvoidance());
    }

    @Override
    public Bounds adjust(HudElement element, Bounds placed) {
        boolean on = HudStyle.get() != null && HudStyle.get().avoidVanilla();
        float dx = 0f;
        float dy = 0f;
        if (on && placed.getWidth() > 0f && placed.getHeight() > 0f) {
            Bounds moved = placed;
            for (Bounds zone : zones()) {
                if (overlaps(moved, zone)) {
                    float[] push = shortestWayOut(moved, zone);
                    dx += push[0];
                    dy += push[1];
                    moved = Bounds.of(placed.getX() + dx, placed.getY() + dy, placed.getWidth(), placed.getHeight());
                }
            }
            dx = clamp(placed.getX() + dx, placed.getWidth(), Core.platform().getScreenWidth()) - placed.getX();
            dy = clamp(placed.getY() + dy, placed.getHeight(), Core.platform().getScreenHeight()) - placed.getY();
        }
        Animation[] eased = offsets.computeIfAbsent(element.getId(), id -> new Animation[]{
                Animation.of(0f, MOVE_MILLIS, Easing.CUBIC_OUT), Animation.of(0f, MOVE_MILLIS, Easing.CUBIC_OUT)});
        float x = move(eased[0], dx);
        float y = move(eased[1], dy);
        if (x == 0f && y == 0f) {
            return placed;
        }
        return Bounds.of(placed.getX() + x, placed.getY() + y, placed.getWidth(), placed.getHeight());
    }

    private List<Bounds> zones() {
        long frame = System.nanoTime() / 4_000_000L;
        if (frame != zonesAt) {
            zonesAt = frame;
            zones = GameHud.vanillaZones();
        }
        return zones;
    }

    private static boolean overlaps(Bounds a, Bounds b) {
        return a.getX() < b.getRight() && a.getRight() > b.getX()
                && a.getY() < b.getBottom() && a.getBottom() > b.getY();
    }

    /** @return the smallest move, along one axis, that takes {@code element} clear of {@code zone} */
    private static float[] shortestWayOut(Bounds element, Bounds zone) {
        float up = zone.getY() - MARGIN - element.getBottom();
        float down = zone.getBottom() + MARGIN - element.getY();
        float left = zone.getX() - MARGIN - element.getRight();
        float right = zone.getRight() + MARGIN - element.getX();
        float vertical = Math.abs(up) < Math.abs(down) ? up : down;
        float horizontal = Math.abs(left) < Math.abs(right) ? left : right;
        return Math.abs(vertical) <= Math.abs(horizontal) ? new float[]{0f, vertical} : new float[]{horizontal, 0f};
    }

    private static float clamp(float position, float size, float screen) {
        return Math.max(0f, Math.min(screen - size, position));
    }

    private static float move(Animation animation, float target) {
        // Sliding out of the way is movement: only at Motion: Full (§2.10).
        if (HudStyle.get() != null && HudStyle.get().motion() == HudStyle.Motion.FULL) {
            animation.target(target);
        } else {
            animation.snapTo(target);
        }
        return animation.get();
    }
}
