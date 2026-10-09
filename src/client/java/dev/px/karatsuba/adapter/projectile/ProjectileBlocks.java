package dev.px.karatsuba.adapter.projectile;

import dev.px.core.math.Box;
import dev.px.core.world.BlockShape;
import dev.px.core.world.BlockView;
import dev.px.karatsuba.adapter.world.Voxels;
import net.minecraft.block.BlockEntityProvider;
import net.minecraft.block.BlockState;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.registry.tag.FluidTags;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.shape.VoxelShape;

import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

/**
 * The world's blocks as a projectile meets them.
 *
 * <p>A projectile's move is a raycast against <b>collision</b> shapes, ignoring
 * fluids ({@code ProjectileUtil.getCollision} with {@code ShapeType.COLLIDER},
 * {@code FluidHandling.NONE}). That cast visits cells along the line and tests each
 * cell's whole shape, so a fence's 1.5-tall box counts in full when the line
 * crosses the fence's own cell, and not at all from the cell above. The projectile
 * README asks for exactly that: shapes <em>not</em> cut to their cell. This is
 * why the combat kit's {@code GameBlocks}, which cuts them for explosion rays, is
 * not reused here.
 *
 * <p>Shapes are built once per block state, as in {@code GameBlocks}; states whose
 * shape depends on where they are or on a block entity are read fresh.
 *
 * <p><b>Game thread only.</b> One cursor serves every lookup.
 */
final class ProjectileBlocks implements BlockView {

    static final ProjectileBlocks INSTANCE = new ProjectileBlocks();

    private static final BlockPos.Mutable CURSOR = new BlockPos.Mutable();

    private final Map<BlockState, BlockShape> shapes = new IdentityHashMap<>();

    private ProjectileBlocks() {
    }

    @Override
    public BlockShape shapeAt(int x, int y, int z) {
        ClientWorld world = Voxels.world();
        BlockState state = stateAt(world, x, y, z);
        if (state == null || state.isAir()) {
            return BlockShape.EMPTY;
        }
        if (state.hasModelOffset() || state.getBlock() instanceof BlockEntityProvider) {
            return shapeOf(state.getCollisionShape(world, CURSOR));
        }
        BlockShape shape = shapes.get(state);
        if (shape == null) {
            shape = shapeOf(state.getCollisionShape(world, CURSOR));
            shapes.put(state, shape);
        }
        return shape;
    }

    /** Forgets the cached shapes, on a world change. */
    void clear() {
        shapes.clear();
    }

    /** Water in this cell: where an arrow's drag changes. A {@code CellTest} for {@code DragRule.inside}. */
    static boolean isWater(int x, int y, int z) {
        BlockState state = stateAt(Voxels.world(), x, y, z);
        return state != null && state.getFluidState().isIn(FluidTags.WATER);
    }

    private static BlockState stateAt(ClientWorld world, int x, int y, int z) {
        if (world == null) {
            return null;
        }
        CURSOR.set(x, y, z);
        if (world.isOutOfHeightLimit(CURSOR)) {
            return null;
        }
        return world.getBlockState(CURSOR);
    }

    /** The game's shape as boxes relative to the cell, whole, sticking out or not. */
    private static BlockShape shapeOf(VoxelShape shape) {
        if (shape.isEmpty()) {
            return BlockShape.EMPTY;
        }
        List<net.minecraft.util.math.Box> boxes = shape.getBoundingBoxes();
        if (boxes.size() == 1) {
            net.minecraft.util.math.Box only = boxes.get(0);
            if (only.minX == 0d && only.minY == 0d && only.minZ == 0d
                    && only.maxX == 1d && only.maxY == 1d && only.maxZ == 1d) {
                return BlockShape.FULL;
            }
        }
        Box[] exact = new Box[boxes.size()];
        for (int i = 0; i < exact.length; i++) {
            exact[i] = Voxels.toBox(boxes.get(i));
        }
        return BlockShape.of(exact);
    }
}
