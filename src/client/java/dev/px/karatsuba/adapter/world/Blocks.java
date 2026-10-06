package dev.px.karatsuba.adapter.world;

import dev.px.core.math.Vec3i;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.registry.Registries;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;

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
     * @return how this cell's block stands up to explosions, as a wall
     *
     * <p>Named blocks rather than a blast-resistance threshold: anvils, enchanting
     * tables and netherite blocks resist explosions too, but are either not full
     * cubes or not what anyone walls a hole with. Ender chests are, which is why
     * they count despite their lower resistance &mdash; a crystal still cannot
     * break one.
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
        Block block = world.getBlockState(pos).getBlock();
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
