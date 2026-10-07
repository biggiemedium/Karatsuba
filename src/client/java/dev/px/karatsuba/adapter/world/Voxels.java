package dev.px.karatsuba.adapter.world;

import dev.px.core.math.Box;
import dev.px.core.math.Vec3;
import dev.px.core.math.Vec3i;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;

import java.util.function.Predicate;

/**
 * The translation table between Core's grid types and the game's.
 *
 * <p>Core's spatial utilities &mdash; {@code AStar}, {@code VoxelRay},
 * {@code FloodFill}, {@code SpatialGrid} &mdash; are written entirely against
 * {@code Vec3i} and Core's own {@code Direction}, on purpose: the arithmetic is
 * version-proof and testable against a hand-drawn grid with no game running. The
 * cost of that is a conversion at the boundary, and this is the boundary. Written
 * once here rather than inline at each call site, because a mismatched axis or an
 * off-by-one in a {@code BlockPos} conversion is invisible until a path walks into
 * a wall.
 *
 * <p>Nothing here allocates beyond the one object it returns, and the block
 * lookups go through the chunk cache the game already keeps warm. That matters:
 * a search with {@code maxNodes} of 4000 asks these questions tens of thousands
 * of times, and Core's own docs make O(1) per lookup a condition of the contract.
 */
public final class Voxels {

    /**
     * Core's directions in the game's order, indexed by ordinal.
     *
     * <p>Both enums happen to declare DOWN, UP, NORTH, SOUTH, WEST, EAST in that
     * order, which makes the mapping a lookup. The constants are named out in full
     * rather than left implicit, so that the one thing that would silently swap
     * north and south &mdash; a reordering on either side &mdash; is visible as a
     * diff against this table rather than hidden behind an ordinal cast.
     */
    private static final Direction[] TO_GAME = {
            Direction.DOWN, Direction.UP, Direction.NORTH,
            Direction.SOUTH, Direction.WEST, Direction.EAST
    };

    private static final dev.px.core.math.Direction[] TO_CORE = {
            dev.px.core.math.Direction.DOWN, dev.px.core.math.Direction.UP,
            dev.px.core.math.Direction.NORTH, dev.px.core.math.Direction.SOUTH,
            dev.px.core.math.Direction.WEST, dev.px.core.math.Direction.EAST
    };

    private Voxels() {
    }

    // ----------------------------------------------------------- conversion

    public static BlockPos toBlockPos(Vec3i position) {
        return new BlockPos(position.getX(), position.getY(), position.getZ());
    }

    public static Vec3i toVec3i(BlockPos pos) {
        return Vec3i.of(pos.getX(), pos.getY(), pos.getZ());
    }

    public static Vec3 toVec3(Vec3d vector) {
        return Vec3.of(vector.x, vector.y, vector.z);
    }

    public static Vec3d toVec3d(Vec3 vector) {
        return new Vec3d(vector.getX(), vector.getY(), vector.getZ());
    }

    /**
     * Core's box to the game's.
     *
     * <p>The two are the same six doubles in the same order, but they are
     * different types and mixing them up compiles happily right up until a
     * simulation sweeps against a region that is not where it thought.
     */
    public static net.minecraft.util.math.Box toGameBox(Box box) {
        return new net.minecraft.util.math.Box(
                box.getMinX(), box.getMinY(), box.getMinZ(),
                box.getMaxX(), box.getMaxY(), box.getMaxZ());
    }

    /** The game's box to Core's. */
    public static Box toBox(net.minecraft.util.math.Box box) {
        return Box.of(box.minX, box.minY, box.minZ, box.maxX, box.maxY, box.maxZ);
    }

    public static Direction toGame(dev.px.core.math.Direction direction) {
        return TO_GAME[direction.ordinal()];
    }

    public static dev.px.core.math.Direction toCore(Direction direction) {
        return TO_CORE[direction.ordinal()];
    }

    // -------------------------------------------------------------- queries

    /** @return the world the player is in, or null in a menu. */
    public static ClientWorld world() {
        return MinecraftClient.getInstance().world;
    }

    /**
     * @return whether a whole entity can stand in this cell: nothing solid, no
     *         fluid, and inside the world's build limits
     *
     * <p>Fluid counts as blocked rather than as cost, because a path that swims is
     * a different agent from one that walks and the difference belongs in the
     * {@link WorldPathSpace} that asks, not in a shared helper.
     */
    public static boolean isOpen(ClientWorld world, BlockPos pos) {
        if (world.isOutOfHeightLimit(pos)) {
            return false;
        }
        var state = world.getBlockState(pos);
        // The collision shape rather than blocksMovement(): the latter is deprecated
        // and answers a coarser question, and the shape is what the game itself uses
        // to decide whether something fits. Full cubes hit a cached shape, so this
        // stays the O(1) lookup Core's PathSpace contract asks for.
        return state.getCollisionShape(world, pos).isEmpty() && state.getFluidState().isEmpty();
    }

    /** @return whether this cell can hold an entity up, which is what a floor is. */
    public static boolean isStandable(ClientWorld world, BlockPos pos) {
        return !world.isOutOfHeightLimit(pos)
                && world.getBlockState(pos).isSideSolidFullSquare(world, pos, Direction.UP);
    }

    /**
     * How a block stands up to explosions, as a wall. The one definition behind
     * {@link Blocks#blastResistance} and the combat library's hole walls.
     *
     * <p>Named blocks rather than a blast-resistance threshold: anvils, enchanting
     * tables and netherite blocks resist explosions too, but are either not full
     * cubes or not what anyone walls a hole with. Ender chests are, which is why
     * they count despite their lower resistance &mdash; a crystal still cannot
     * break one.
     */
    public static BlastResistance blastResistance(BlockState state) {
        Block block = state.getBlock();
        if (block == net.minecraft.block.Blocks.BEDROCK) {
            return BlastResistance.UNBREAKABLE;
        }
        if (block == net.minecraft.block.Blocks.OBSIDIAN
                || block == net.minecraft.block.Blocks.CRYING_OBSIDIAN
                || block == net.minecraft.block.Blocks.ENDER_CHEST) {
            return BlastResistance.RESISTANT;
        }
        return BlastResistance.NONE;
    }

    /**
     * A cell test for {@code VoxelRay}, in the type Core's traversal wants.
     *
     * <p>Core walks the cells a ray crosses and asks a predicate which one stops
     * it, so a block raytrace is this plus {@code VoxelRay.trace}. Bound to a world
     * once rather than looking it up per cell: a ray can cross a hundred of them.
     *
     * <pre>{@code
     * VoxelRay.Hit hit = VoxelRay.trace(eye, rotation, 4.5d, Voxels.solidCells(world));
     * }</pre>
     */
    public static Predicate<Vec3i> solidCells(ClientWorld world) {
        BlockPos.Mutable cursor = new BlockPos.Mutable();
        // Mutable, and therefore not thread safe. That is the right trade here:
        // a traversal is a tight loop on one thread, and the alternative is an
        // allocation per cell crossed.
        return cell -> {
            cursor.set(cell.getX(), cell.getY(), cell.getZ());
            return !world.isOutOfHeightLimit(cursor)
                    && !world.getBlockState(cursor).getCollisionShape(world, cursor).isEmpty();
        };
    }
}
