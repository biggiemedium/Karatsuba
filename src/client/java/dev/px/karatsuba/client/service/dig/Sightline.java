package dev.px.karatsuba.client.service.dig;

import dev.px.core.math.Box;
import dev.px.core.math.Direction;
import dev.px.core.math.Vec2;
import dev.px.core.math.Vec3;
import dev.px.core.math.Vec3i;
import dev.px.karatsuba.adapter.game.entity.Targets;
import dev.px.karatsuba.adapter.world.Blocks;

import java.util.Arrays;
import java.util.Comparator;

/**
 * Where to look to have a block under the look: a point on one of its faces that a
 * look ray from the eyes reaches before anything else.
 *
 * <p>A block behind another can still be mined once the ray finds a way past, a
 * gap or a corner, and one with nothing in front is just its nearest face. The ray
 * is the game's own crosshair ray ({@link Targets#raycast}), against outlines, so
 * an answer here is a look the game would put the crosshair on the block with.
 *
 * <p>A point that works is kept for as long as it does, so the head does not
 * wander about the block while it is being mined.
 */
public final class Sightline {

    /** Where on a face to try, as shares of its half-width each way from the middle. */
    private static final double[][] SPOTS = {
            {0d, 0d}, {0.5d, 0.5d}, {-0.5d, 0.5d}, {0.5d, -0.5d}, {-0.5d, -0.5d},
            {0.8d, 0d}, {-0.8d, 0d}, {0d, 0.8d}, {0d, -0.8d}
    };

    private Vec3i block;
    private Vec3 point;

    /**
     * @return a point on the block a look from {@code eyes} meets the block at,
     *         within {@code reach}; null when every face is hidden or too far
     */
    public Vec3 find(Vec3i target, Vec3 eyes, double reach) {
        if (target.equals(block) && point != null && meets(target, eyes, point, reach)) {
            return point;
        }
        block = target;
        point = null;
        Box outline = Blocks.outline(target);
        Direction[] faces = Direction.values();
        Arrays.sort(faces, Comparator.comparingDouble(face -> middleOf(outline, face).squaredDistanceTo(eyes)));
        for (Direction face : faces) {
            Vec3 middle = middleOf(outline, face);
            // A face looked at from behind its own plane is a face the ray cannot enter.
            if (eyes.subtract(middle).dot(face.toVector()) <= 0d) {
                continue;
            }
            for (double[] spot : SPOTS) {
                Vec3 candidate = spotOn(outline, face, spot[0], spot[1]);
                if (meets(target, eyes, candidate, reach)) {
                    point = candidate;
                    return point;
                }
            }
        }
        return null;
    }

    public void clear() {
        block = null;
        point = null;
    }

    /** @return whether a look from {@code eyes} toward {@code at} lands on {@code target} first, within reach */
    public static boolean meets(Vec3i target, Vec3 eyes, Vec3 at, double reach) {
        if (eyes.distanceTo(at) > reach) {
            return false;
        }
        Targets.Hit hit = Targets.raycast(eyes, eyes.rotationTo(at), reach);
        return hit != null && hit.getBlock().equals(target);
    }

    /** @return the face of {@code target} a look along {@code rotation} lands on, or null if it lands elsewhere */
    public static Direction faceLookedAt(Vec3i target, Vec3 eyes, Vec2 rotation, double reach) {
        Targets.Hit hit = Targets.raycast(eyes, rotation, reach);
        return hit != null && hit.getBlock().equals(target) ? hit.getFace() : null;
    }

    private static Vec3 middleOf(Box box, Direction face) {
        return spotOn(box, face, 0d, 0d);
    }

    /**
     * A point on one face of a box: {@code u} and {@code v} run -1 to 1 across it,
     * kept a little inside so a ray never grazes the edge.
     */
    private static Vec3 spotOn(Box box, Direction face, double u, double v) {
        Vec3 min = box.getMin();
        Vec3 max = box.getMax();
        Vec3 centre = box.getCenter();
        double hx = box.getWidth() / 2d;
        double hy = box.getHeight() / 2d;
        double hz = box.getDepth() / 2d;
        double inset = 1.0E-3d;
        switch (face) {
            case UP:
                return Vec3.of(centre.getX() + u * hx, max.getY() - inset, centre.getZ() + v * hz);
            case DOWN:
                return Vec3.of(centre.getX() + u * hx, min.getY() + inset, centre.getZ() + v * hz);
            case NORTH:
                return Vec3.of(centre.getX() + u * hx, centre.getY() + v * hy, min.getZ() + inset);
            case SOUTH:
                return Vec3.of(centre.getX() + u * hx, centre.getY() + v * hy, max.getZ() - inset);
            case WEST:
                return Vec3.of(min.getX() + inset, centre.getY() + v * hy, centre.getZ() + u * hz);
            default:
                return Vec3.of(max.getX() - inset, centre.getY() + v * hy, centre.getZ() + u * hz);
        }
    }
}
