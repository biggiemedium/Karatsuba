package dev.px.karatsuba.client.combat.breach;

import dev.px.core.math.Vec3i;

/**
 * One block that stands between a target and a crystal, and what breaking it opens.
 *
 * @param block     the block to mine
 * @param base      the obsidian or bedrock the crystal would stand on once it is gone;
 *                  null for a burrow, which opens the target rather than a spot
 * @param level     where it is: in their feet, beside their feet, or beside their head
 * @param mineTicks ticks to mine it with the best tool in the hotbar
 * @param inReach   whether it can be mined from where you stand
 */
public record Breach(Vec3i block, Vec3i base, Level level, int mineTicks, boolean inReach) {

    public enum Level {
        /** A block inside their feet cell: a burrow. Out first, or nothing else lands. */
        BURROW,
        /** Beside their feet: the crystal goes into the hole it leaves. */
        FEET,
        /** Beside their head, on a wall block: the crystal goes on top of the wall, at their face. */
        FACE
    }
}
