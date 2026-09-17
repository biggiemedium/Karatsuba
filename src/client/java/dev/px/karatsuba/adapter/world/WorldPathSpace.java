package dev.px.karatsuba.adapter.world;

import dev.px.core.math.Vec3i;
import dev.px.core.util.spatial.PathSpace;
import net.minecraft.block.Blocks;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.registry.tag.BlockTags;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;

/**
 * What Core's pathfinder is allowed to ask about a Minecraft world.
 *
 * <p>{@code AStar} lives in Core and the world does not, so the search is written
 * against two methods and this supplies them. Core's own words for the split:
 * the search is arithmetic over a grid and belongs there, while knowing whether a
 * cell holds a fence gate, a cobweb, or lava is the adapter's business. Until
 * this existed, every one of Core's spatial utilities was unreachable from the
 * running game.
 *
 * <h2>Passable means occupiable</h2>
 * Not empty. A walking agent needs a floor under the cell and headroom above it,
 * and encoding that here rather than in the search is what keeps one pathfinder
 * usable for a flier too &mdash; {@link #flying} is the same class with the floor
 * requirement dropped.
 *
 * <h2>Hazards are weighted, not banned</h2>
 * Lava and fire are passable at a cost of 1000, not impassable. The difference
 * matters when there is no safe route: a banned cell means no path at all and an
 * agent that stands still, while a weighted one means the search goes around
 * unless going around is a thousand times longer. Cobwebs and soul sand are the
 * same idea at honest multipliers &mdash; they really are slower, by about that
 * much.
 *
 * <pre>{@code
 * Path path = AStar.in(WorldPathSpace.walking(world))
 *         .stepUp(1)
 *         .maxFall(3)
 *         .find(Voxels.toVec3i(player.getBlockPos()), goal);
 * }</pre>
 *
 * <p>One mutable cursor is reused for every lookup, so a search allocates nothing
 * per node. That makes an instance single-threaded by construction: build one per
 * search rather than sharing it, which is what {@link #walking} encourages.
 */
public final class WorldPathSpace implements PathSpace {

    /** Survivable, but only just. High enough that any detour is preferred. */
    private static final double LETHAL_COST = 1000d;

    /** Roughly how much longer a cobweb takes to cross than open air. */
    private static final double COBWEB_COST = 20d;

    private static final double SLOW_COST = 4d;
    private static final double FLUID_COST = 3d;

    private final ClientWorld world;
    private final boolean needsFloor;

    /** Reused across every query; see the note on threading above. */
    private final BlockPos.Mutable cursor = new BlockPos.Mutable();

    private WorldPathSpace(ClientWorld world, boolean needsFloor) {
        this.world = world;
        this.needsFloor = needsFloor;
    }

    /** A space for an agent that walks: needs a floor, two blocks of headroom. */
    public static WorldPathSpace walking(ClientWorld world) {
        return new WorldPathSpace(world, true);
    }

    /** A space for an agent that does not fall: air and headroom, no floor. */
    public static WorldPathSpace flying(ClientWorld world) {
        return new WorldPathSpace(world, false);
    }

    @Override
    public boolean isPassable(Vec3i position) {
        cursor.set(position.getX(), position.getY(), position.getZ());
        if (world.isOutOfHeightLimit(cursor)) {
            return false;
        }
        if (!isOccupiable(cursor)) {
            return false;
        }
        // Headroom, checked before the floor: an agent that cannot fit here is out
        // regardless of what is underneath, and this is the cheaper of the two.
        cursor.set(position.getX(), position.getY() + 1, position.getZ());
        if (!world.isOutOfHeightLimit(cursor) && !isOccupiable(cursor)) {
            return false;
        }
        if (!needsFloor) {
            return true;
        }
        cursor.set(position.getX(), position.getY() - 1, position.getZ());
        return !world.isOutOfHeightLimit(cursor)
                && world.getBlockState(cursor).isSideSolidFullSquare(world, cursor, Direction.UP);
    }

    @Override
    public double costOf(Vec3i position) {
        cursor.set(position.getX(), position.getY(), position.getZ());
        var state = world.getBlockState(cursor);
        var block = state.getBlock();

        if (state.isIn(BlockTags.FIRE) || block == Blocks.LAVA || block == Blocks.MAGMA_BLOCK) {
            return LETHAL_COST;
        }
        if (block == Blocks.COBWEB) {
            return COBWEB_COST;
        }
        if (block == Blocks.SWEET_BERRY_BUSH || block == Blocks.POWDER_SNOW) {
            // Damage plus a crawl, so worth the same as several ordinary blocks.
            return COBWEB_COST;
        }
        if (block == Blocks.SOUL_SAND || block == Blocks.HONEY_BLOCK || block == Blocks.SLIME_BLOCK) {
            return SLOW_COST;
        }
        if (!state.getFluidState().isEmpty()) {
            return FLUID_COST;
        }
        // Standing on the edge of a drop is where an agent falls off a path it was
        // following correctly, so a cell whose floor is missing is worth avoiding
        // even for a flier, which is why this is not behind needsFloor.
        cursor.set(position.getX(), position.getY() - 1, position.getZ());
        if (!world.isOutOfHeightLimit(cursor) && world.getBlockState(cursor).getBlock() == Blocks.LAVA) {
            return LETHAL_COST;
        }
        return 1d;
    }

    /**
     * @return whether an entity can be inside this cell at all
     *
     * <p>Fluid is occupiable here and expensive in {@link #costOf}, rather than
     * blocked: a route that has to swim is still a route, and the weighting is
     * what keeps it a last resort.
     */
    private boolean isOccupiable(BlockPos pos) {
        var state = world.getBlockState(pos);
        // Cobwebs and berry bushes have a collision shape but are walked through
        // every day, so they are admitted here and paid for in costOf.
        var block = state.getBlock();
        if (block == Blocks.COBWEB || block == Blocks.SWEET_BERRY_BUSH || block == Blocks.POWDER_SNOW) {
            return true;
        }
        // The collision shape rather than blocksMovement(): see Voxels.isOpen.
        return state.getCollisionShape(world, pos).isEmpty();
    }
}
