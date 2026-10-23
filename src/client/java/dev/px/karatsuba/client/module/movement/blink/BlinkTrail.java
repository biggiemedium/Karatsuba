package dev.px.karatsuba.client.module.movement.blink;

import dev.px.core.math.Box;
import dev.px.core.math.Vec3;
import dev.px.core.render.Color;
import dev.px.core.render.Render;

import java.util.ArrayList;
import java.util.List;

/**
 * Blink's trail: a line through every position held back from the server, on to
 * the player, and a ghost of the hitbox where the server last saw them.
 *
 * <p>The points are the positions in the held movement packets, not samples of
 * the player, so the line is exactly what the server will be told when the hold
 * lets go. When it does, the line is delivered and fades out, and the ghost
 * glides to its last point, which is where the server now has the player.
 *
 * <p>Time-based, like Core's {@code Animation}: the same at any frame rate. Game
 * thread only (ticks and frames are both on it).
 */
public final class BlinkTrail {

    /** Points closer together than this are one point: standing still adds nothing. */
    private static final double SPACING = 0.2d;

    /** How long a delivered line takes to fade out. */
    private static final long DELIVER_MILLIS = 350L;

    private static final double PLAYER_WIDTH = 0.6d;
    private static final double PLAYER_HEIGHT = 1.8d;

    /** Points still held, oldest first. */
    private final List<Vec3> live = new ArrayList<>();

    /** The last line delivered, fading out; empty once it has. */
    private final List<Vec3> delivered = new ArrayList<>();
    private long deliveredNanos;

    /** Where the server last had the player, and where the ghost is drawn on its way there. */
    private Vec3 server;
    private Vec3 ghost;

    /** A position the server was sent, with nothing held: where it has the player now. */
    public void sent(Vec3 position) {
        if (live.isEmpty()) {
            server = position;
        }
    }

    /** A position held back from the server. */
    public void held(Vec3 position) {
        Vec3 last = live.isEmpty() ? server : live.get(live.size() - 1);
        if (last == null || last.distanceTo(position) >= SPACING) {
            live.add(position);
        }
    }

    /** The hold let go: the server now has every point, and the last is where it has the player. */
    public void deliver() {
        if (live.isEmpty()) {
            return;
        }
        server = live.get(live.size() - 1);
        delivered.clear();
        delivered.addAll(live);
        deliveredNanos = System.nanoTime();
        live.clear();
    }

    public void clear() {
        live.clear();
        delivered.clear();
        server = null;
        ghost = null;
    }

    /** @return whether there is anything to draw: a held line, or a delivered one still fading */
    public boolean isShowing() {
        return !live.isEmpty() || !delivered.isEmpty();
    }

    /**
     * @param head         the player's feet this frame, interpolated
     * @param color        the line's colour
     * @param ghostColor   the ghost's colour; null for no ghost
     * @param frameSeconds time since the last frame, for the ghost's glide
     * @param animated     whether to fade and glide; off, everything snaps
     */
    public void draw(Vec3 head, Color color, Color ghostColor, float thickness, float frameSeconds,
                     boolean animated) {
        if (!delivered.isEmpty()) {
            float t = animated
                    ? Math.min(1f, (System.nanoTime() - deliveredNanos) / (DELIVER_MILLIS * 1.0e6f))
                    : 1f;
            if (t >= 1f) {
                delivered.clear();
            } else {
                line(delivered, null, color.fade(1f - t), thickness);
            }
        }
        if (!live.isEmpty()) {
            line(live, head, color, thickness);
        }
        if (ghostColor != null && server != null && isShowing()) {
            drawGhost(frameSeconds, animated, ghostColor);
        } else {
            ghost = null;
        }
    }

    /** One line through {@code points}, then on to {@code head} if given. */
    private static void line(List<Vec3> points, Vec3 head, Color color, float thickness) {
        Vec3 previous = null;
        for (Vec3 point : points) {
            if (previous != null) {
                Render.line3D(previous, point, thickness, color);
            }
            previous = point;
        }
        if (previous != null && head != null) {
            Render.line3D(previous, head, thickness, color);
        }
    }

    /** The hitbox where the server has the player, gliding there after each delivery. */
    private void drawGhost(float frameSeconds, boolean animated, Color color) {
        if (ghost == null || !animated) {
            // Appears where the server has the player; only moves by gliding.
            ghost = server;
        } else {
            ghost = ghost.lerp(server, 1d - Math.exp(-frameSeconds * 14d));
        }
        Box box = Box.around(ghost, PLAYER_WIDTH, PLAYER_HEIGHT);
        Render.box(box, color.fade(0.18f));
        Render.boxOutline(box, 1.5f, color);
    }
}
