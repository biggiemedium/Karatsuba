package dev.px.karatsuba.adapter.world;

import dev.px.core.math.Box;
import dev.px.core.math.Vec3i;
import net.minecraft.block.BlockState;
import net.minecraft.block.CampfireBlock;
import net.minecraft.block.LadderBlock;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.registry.Registries;
import net.minecraft.registry.tag.BlockTags;
import net.minecraft.registry.tag.FluidTags;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.shape.VoxelShape;

/**
 * Asking about one block, in Core's types.
 *
 * <p>{@link Voxels} is the conversion table and takes a {@code ClientWorld} and a
 * {@code BlockPos}, which makes it usable from inside the adapter and unusable
 * from a module. This is the module-facing half: a {@code Vec3i} goes in, a
 * boolean or a registry id comes out, and the world is looked up per call so
 * there is no handle to go stale.
 *
 * <p>Every reader answers safely out of a world: "is there anything solid here"
 * is false when there is no here.
 *
 * <p><b>Game thread only</b>, and the caller decides how often to ask. These are
 * chunk-cache lookups rather than searches, but a module scanning a radius every
 * frame will still feel it &mdash; scan on the tick and draw from what it found.
 */
public final class Blocks {

    private Blocks() {
    }

    /** @return whether nothing solid occupies this cell */
    public static boolean isOpen(Vec3i position) {
        ClientWorld world = Voxels.world();
        if (world == null) {
            return false;
        }
        BlockPos pos = Voxels.toBlockPos(position);
        return !world.isOutOfHeightLimit(pos) && Voxels.isOpen(world, pos);
    }

    /** @return whether something solid occupies this cell */
    public static boolean isSolid(Vec3i position) {
        ClientWorld world = Voxels.world();
        if (world == null) {
            return false;
        }
        BlockPos pos = Voxels.toBlockPos(position);
        return !world.isOutOfHeightLimit(pos)
                && !world.getBlockState(pos).getCollisionShape(world, pos).isEmpty();
    }

    /**
     * @return whether this cell can hold something up
     *
     * <p>A full solid top face, which is what a floor is. Not the same as
     * {@link #isSolid}: a fence is solid and cannot be stood on flush, and a slab
     * in the lower half is solid and its top is not where a full block's would be.
     */
    public static boolean isStandable(Vec3i position) {
        ClientWorld world = Voxels.world();
        if (world == null) {
            return false;
        }
        BlockPos pos = Voxels.toBlockPos(position);
        return !world.isOutOfHeightLimit(pos)
                && world.getBlockState(pos).isSideSolidFullSquare(world, pos, Direction.UP);
    }

    /**
     * @return the block's outline at this cell in world space, as the game's
     *         wireframe traces it: a slab half height, a fence its post; the full
     *         cube for a block with no outline (air included)
     */
    public static Box outline(Vec3i position) {
        ClientWorld world = Voxels.world();
        Box cube = Box.block(position.getX(), position.getY(), position.getZ());
        if (world == null) {
            return cube;
        }
        BlockPos pos = Voxels.toBlockPos(position);
        VoxelShape shape = world.getBlockState(pos).getOutlineShape(world, pos);
        if (shape.isEmpty()) {
            return cube;
        }
        return Voxels.toBox(shape.getBoundingBox().offset(pos));
    }

    /** @return the registry id at this cell, or an empty string */
    public static String idAt(Vec3i position) {
        ClientWorld world = Voxels.world();
        if (world == null) {
            return "";
        }
        BlockPos pos = Voxels.toBlockPos(position);
        if (world.isOutOfHeightLimit(pos)) {
            return "";
        }
        BlockState state = world.getBlockState(pos);
        return Registries.BLOCK.getId(state.getBlock()).toString();
    }

    /**
     * @return how this cell's block stands up to explosions, as a wall. See
     *         {@link Voxels#blastResistance} for which blocks count.
     */
    public static BlastResistance blastResistance(Vec3i position) {
        ClientWorld world = Voxels.world();
        if (world == null) {
            return BlastResistance.NONE;
        }
        BlockPos pos = Voxels.toBlockPos(position);
        if (world.isOutOfHeightLimit(pos)) {
            return BlastResistance.NONE;
        }
        return Voxels.blastResistance(world.getBlockState(pos));
    }

    /**
     * @return whether standing in this cell, or on it, hurts: lava, fire, a lit
     *         campfire, magma, cactus, a berry bush, a wither rose, powder snow
     *
     * <p>Contact damage only, so a route planner can keep out of it. Falling is
     * the planner's own limit, and suffocation is a cell it cannot stand in anyway.
     */
    public static boolean hurts(Vec3i position) {
        ClientWorld world = Voxels.world();
        if (world == null) {
            return false;
        }
        BlockPos pos = Voxels.toBlockPos(position);
        if (world.isOutOfHeightLimit(pos)) {
            return false;
        }
        return hurts(world.getBlockState(pos));
    }

    /** @return whether standing in or on a block in this state hurts; see {@link #hurts(Vec3i)} */
    public static boolean hurts(BlockState state) {
        return state.getFluidState().isIn(FluidTags.LAVA)
                || state.isIn(BlockTags.FIRE)
                || (state.isIn(BlockTags.CAMPFIRES) && CampfireBlock.isLitCampfire(state))
                || state.isOf(net.minecraft.block.Blocks.MAGMA_BLOCK)
                || state.isOf(net.minecraft.block.Blocks.CACTUS)
                || state.isOf(net.minecraft.block.Blocks.SWEET_BERRY_BUSH)
                || state.isOf(net.minecraft.block.Blocks.WITHER_ROSE)
                || state.isOf(net.minecraft.block.Blocks.POWDER_SNOW);
    }

    /** @return whether this cell can be climbed: a ladder, vines, scaffolding, twisting vines */
    public static boolean isClimbable(Vec3i position) {
        ClientWorld world = Voxels.world();
        if (world == null) {
            return false;
        }
        BlockPos pos = Voxels.toBlockPos(position);
        return !world.isOutOfHeightLimit(pos) && world.getBlockState(pos).isIn(BlockTags.CLIMBABLE);
    }

    /**
     * @return the direction from a ladder to the wall it hangs on, which is the
     *         way to face to climb it; null for anything but a ladder
     */
    public static dev.px.core.math.Direction ladderWall(Vec3i position) {
        ClientWorld world = Voxels.world();
        if (world == null) {
            return null;
        }
        BlockPos pos = Voxels.toBlockPos(position);
        BlockState state = world.getBlockState(pos);
        if (!state.isOf(net.minecraft.block.Blocks.LADDER)) {
            return null;
        }
        // A ladder faces away from its wall.
        return Voxels.toCore(state.get(LadderBlock.FACING).getOpposite());
    }

    /** @return whether this cell holds any fluid: water, lava, or a waterlogged block */
    public static boolean isLiquid(Vec3i position) {
        ClientWorld world = Voxels.world();
        if (world == null) {
            return false;
        }
        BlockPos pos = Voxels.toBlockPos(position);
        return !world.isOutOfHeightLimit(pos) && !world.getFluidState(pos).isEmpty();
    }

    /**
     * @return whether a placed block would take this cell's place: air, grass, snow
     *         layers, water and the like; not a torch, which is passable but stays
     */
    public static boolean isReplaceable(Vec3i position) {
        ClientWorld world = Voxels.world();
        if (world == null) {
            return false;
        }
        BlockPos pos = Voxels.toBlockPos(position);
        return !world.isOutOfHeightLimit(pos) && world.getBlockState(pos).isReplaceable();
    }

    /** @return whether this cell is air, which is not the same as being passable */
    public static boolean isAir(Vec3i position) {
        ClientWorld world = Voxels.world();
        if (world == null) {
            return false;
        }
        BlockPos pos = Voxels.toBlockPos(position);
        return !world.isOutOfHeightLimit(pos) && world.getBlockState(pos).isAir();
    }
}
