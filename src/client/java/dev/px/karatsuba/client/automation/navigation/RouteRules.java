package dev.px.karatsuba.client.automation.navigation;

import dev.px.core.math.Vec3;
import dev.px.core.math.Vec3i;
import dev.px.core.movement.simulation.MotionState;
import dev.px.karatsuba.adapter.world.Blocks;
import dev.px.karatsuba.client.settings.automation.AutomationSettings;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;

/**
 * Where a route may not go: the planner's {@code allowed} rule.
 *
 * <p>The navigation library knows no blocks, so what is unsafe to stand in is
 * the client's to say. The planner asks about every tick of every move it
 * simulates, so the answer is about the cells the player's body is in at that
 * tick:
 *
 * <ul>
 *   <li><b>Hazards</b> ({@link Blocks#hurts}): any cell the body overlaps, at the
 *       feet or the head, and the block underfoot, for magma.</li>
 *   <li><b>Water</b>: any cell the body overlaps holding fluid. The planner models
 *       walking, so a route into water is one the player cannot follow.</li>
 * </ul>
 *
 * <h2>Never trapped where it stands</h2>
 * The cells the player is in as the tick begins count as safe whatever is in
 * them. Standing in a puddle, a berry bush or a fire, every move begins in that
 * cell; refusing it would refuse every way out, and the one place a route must
 * not leave the player is where it is.
 *
 * <p>A plan asks about the same cells thousands of times, so each answer is kept
 * until the next tick ({@link #newTick}), when a block may have changed.
 *
 * <p>An instance is single-threaded: the live one belongs to the game thread,
 * and a plan made elsewhere builds its own over a snapshot.
 */
final class RouteRules {

    /** Half the player's width; the body spans the cells under its four corners. */
    private static final double HALF_WIDTH = 0.3d;

    /** Feet to head: the body overlaps the cell its feet are in and the one above. */
    private static final int BODY_CELLS = 2;

    private static final byte SAFE = 0;
    private static final byte HURTS = 1;
    private static final byte WET = 2;

    private final AutomationSettings settings;
    private final Predicate<Vec3i> hurts;
    private final Predicate<Vec3i> liquid;
    private final Map<Vec3i, Byte> cells = new HashMap<>();
    private final Set<Vec3i> standing = new HashSet<>();

    /** The live world, for the game thread. */
    RouteRules(AutomationSettings settings) {
        this(settings, Blocks::hurts, Blocks::isLiquid);
    }

    /**
     * Rules over another view of the world: a {@code WorldSnapshot}, for a plan
     * made off the game thread. Each such plan gets its own instance, since the
     * cache is not shared safely.
     */
    RouteRules(AutomationSettings settings, Predicate<Vec3i> hurts, Predicate<Vec3i> liquid) {
        this.settings = settings;
        this.hurts = hurts;
        this.liquid = liquid;
    }

    /** @return whether the player may be in {@code state}, by the settings as they are now */
    boolean allows(MotionState state) {
        boolean hazards = settings.avoidHazards();
        boolean water = settings.avoidWater();
        if (!hazards && !water) {
            return true;
        }
        Vec3 feet = state.getPosition();
        int footY = cell(feet.getY());
        for (int x = cell(feet.getX() - HALF_WIDTH); x <= cell(feet.getX() + HALF_WIDTH); x++) {
            for (int z = cell(feet.getZ() - HALF_WIDTH); z <= cell(feet.getZ() + HALF_WIDTH); z++) {
                // Underfoot counts for hazards only: standing on water is not swimming.
                if (hazards && kind(Vec3i.of(x, footY - 1, z)) == HURTS) {
                    return false;
                }
                for (int y = footY; y < footY + BODY_CELLS; y++) {
                    byte kind = kind(Vec3i.of(x, y, z));
                    if ((hazards && kind == HURTS) || (water && kind == WET)) {
                        return false;
                    }
                }
            }
        }
        return true;
    }

    /**
     * A new tick: forgets every cell, since a block may have changed, and notes
     * the cells the player stands in, which never block a way out.
     *
     * @param feet where the player stands now, or null out of a world
     */
    void newTick(Vec3 feet) {
        cells.clear();
        standing.clear();
        if (feet == null) {
            return;
        }
        int footY = cell(feet.getY());
        for (int x = cell(feet.getX() - HALF_WIDTH); x <= cell(feet.getX() + HALF_WIDTH); x++) {
            for (int z = cell(feet.getZ() - HALF_WIDTH); z <= cell(feet.getZ() + HALF_WIDTH); z++) {
                for (int y = footY - 1; y < footY + BODY_CELLS; y++) {
                    standing.add(Vec3i.of(x, y, z));
                }
            }
        }
    }

    /** @return the grid coordinate a world coordinate falls in */
    private static int cell(double coordinate) {
        return (int) Math.floor(coordinate);
    }

    private byte kind(Vec3i cell) {
        Byte known = cells.get(cell);
        if (known != null) {
            return known;
        }
        byte kind = standing.contains(cell) ? SAFE
                : hurts.test(cell) ? HURTS
                : liquid.test(cell) ? WET
                : SAFE;
        cells.put(cell, kind);
        return kind;
    }
}
