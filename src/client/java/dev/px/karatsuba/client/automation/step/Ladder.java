package dev.px.karatsuba.client.automation.step;

import dev.px.core.math.Direction;
import dev.px.core.math.Vec3;
import dev.px.core.math.Vec3i;
import dev.px.karatsuba.adapter.world.Blocks;
import lombok.Getter;

/**
 * One column of climbable blocks: a ladder, a run of vines, scaffolding.
 *
 * <p>Found from any cell in it, or as the nearest one to a point, and read for
 * what a climb needs: where it starts and ends, and which way to face. A ladder
 * is climbed facing the wall it hangs on, which is what keeps the player on it;
 * vines and scaffolding have no wall, and are climbed facing their middle.
 */
@Getter
public final class Ladder {

    /** Blocks from the middle, away from the wall, to step to before dropping on. */
    private static final double DESCENT_OFFSET = 0.15d;

    /** The bottom climbable cell. */
    private final Vec3i bottom;

    /** The y of the top climbable cell. */
    private final int topY;

    /** From the ladder to its wall: the way to face. Null for climbables with no wall. */
    private final Direction wall;

    private Ladder(Vec3i bottom, int topY, Direction wall) {
        this.bottom = bottom;
        this.topY = topY;
        this.wall = wall;
    }

    /** @return the column through {@code cell}, or null if it is not climbable */
    public static Ladder at(Vec3i cell) {
        if (!Blocks.isClimbable(cell)) {
            return null;
        }
        int x = cell.getX();
        int z = cell.getZ();
        int low = cell.getY();
        while (Blocks.isClimbable(Vec3i.of(x, low - 1, z))) {
            low--;
        }
        int high = cell.getY();
        while (Blocks.isClimbable(Vec3i.of(x, high + 1, z))) {
            high++;
        }
        return new Ladder(Vec3i.of(x, low, z), high, Blocks.ladderWall(cell));
    }

    /** @return the column with a cell nearest {@code from}, within {@code radius} blocks; or null */
    public static Ladder nearest(Vec3 from, int radius) {
        Vec3i centre = Vec3i.floorOf(from);
        Vec3i best = null;
        double bestDistance = Double.MAX_VALUE;
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dy = -radius; dy <= radius; dy++) {
                for (int dz = -radius; dz <= radius; dz++) {
                    Vec3i cell = Vec3i.of(centre.getX() + dx, centre.getY() + dy, centre.getZ() + dz);
                    double distance = cell.center().distanceTo(from);
                    if (distance < bestDistance && Blocks.isClimbable(cell)) {
                        best = cell;
                        bestDistance = distance;
                    }
                }
            }
        }
        return best == null ? null : at(best);
    }

    /** @return the feet at the foot of the column: where a climb up starts */
    public Vec3 base() {
        return Vec3.of(bottom.getX() + 0.5d, bottom.getY(), bottom.getZ() + 0.5d);
    }

    /** @return the feet just above the column: where a climb down starts */
    public Vec3 top() {
        return Vec3.of(bottom.getX() + 0.5d, topY + 1d, bottom.getZ() + 0.5d);
    }

    /** @return whether these feet are over the column, near enough its middle to be on it */
    public boolean holds(Vec3 feet, double slack) {
        return Math.abs(feet.getX() - (bottom.getX() + 0.5d)) <= slack
                && Math.abs(feet.getZ() - (bottom.getZ() + 0.5d)) <= slack;
    }

    /**
     * @return how far {@code feet} are from the column's middle toward its wall,
     *         in blocks; 0 for a climbable with no wall
     *
     * <p>A ladder is a thin plate against its wall, and a player can stand on top
     * of it. Feet toward the wall from the middle are on that plate; to drop onto
     * the ladder they have to be at the middle or away from the wall.
     */
    public double towardWall(Vec3 feet) {
        if (wall == null) {
            return 0d;
        }
        Vec3 offset = feet.add(-(bottom.getX() + 0.5d), 0d, -(bottom.getZ() + 0.5d));
        Vec3 into = wall.toVector();
        return offset.getX() * into.getX() + offset.getZ() * into.getZ();
    }

    /**
     * @return where to step to drop onto the column from its head: a little from
     *         the middle away from the wall, clear of the ladder's plate, at height {@code y}
     */
    public Vec3 descent(double y) {
        Vec3 middle = Vec3.of(bottom.getX() + 0.5d, y, bottom.getZ() + 0.5d);
        if (wall == null) {
            return middle;
        }
        Vec3 into = wall.toVector();
        return middle.add(-into.getX() * DESCENT_OFFSET, 0d, -into.getZ() * DESCENT_OFFSET);
    }

    /** @return the yaw to climb at from {@code feet}: toward the wall, or toward the column's middle */
    public float climbYaw(Vec3 feet) {
        return wall != null ? wall.toYaw() : yawToMiddle(feet);
    }

    /** @return the yaw from {@code feet} to the column's middle, level */
    public float yawToMiddle(Vec3 feet) {
        return feet.rotationTo(Vec3.of(bottom.getX() + 0.5d, feet.getY(), bottom.getZ() + 0.5d)).getYaw();
    }

    @Override
    public String toString() {
        return "ladder at " + bottom.getX() + ", " + bottom.getY() + "-" + topY + ", " + bottom.getZ();
    }
}
