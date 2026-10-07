package dev.px.karatsuba.adapter.combat;

import dev.px.combat.anchor.AnchorLookup;
import dev.px.combat.bed.BedLookup;
import dev.px.core.math.Box;
import dev.px.core.world.BlockShape;
import dev.px.core.world.BlockView;
import dev.px.core.world.CellTest;
import dev.px.karatsuba.adapter.world.BlastResistance;
import dev.px.karatsuba.adapter.world.Voxels;
import net.minecraft.block.AbstractRedstoneGateBlock;
import net.minecraft.block.AnvilBlock;
import net.minecraft.block.BedBlock;
import net.minecraft.block.Block;
import net.minecraft.block.BlockEntityProvider;
import net.minecraft.block.BlockState;
import net.minecraft.block.ButtonBlock;
import net.minecraft.block.CakeBlock;
import net.minecraft.block.CartographyTableBlock;
import net.minecraft.block.CraftingTableBlock;
import net.minecraft.block.DoorBlock;
import net.minecraft.block.FenceGateBlock;
import net.minecraft.block.GrindstoneBlock;
import net.minecraft.block.LeverBlock;
import net.minecraft.block.LoomBlock;
import net.minecraft.block.NoteBlock;
import net.minecraft.block.RespawnAnchorBlock;
import net.minecraft.block.StonecutterBlock;
import net.minecraft.block.TrapdoorBlock;
import net.minecraft.block.enums.BedPart;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.shape.VoxelShape;

import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

/**
 * The world's blocks, as Core's {@code world} package and the combat library ask
 * about them: shapes for rays, and yes-or-no questions about single cells.
 *
 * <h2>Shapes</h2>
 * {@link #INSTANCE} is the {@link BlockView}: the shape that stops a line through
 * a cell. Explosions decide exposure with a raycast against <b>collision</b>
 * shapes, ignoring fluids ({@code ExplosionImpl.calculateReceivedDamage}, 1.21.4),
 * so that is what this returns. A shape that sticks out of its cell (a fence is
 * 1.5 tall) is cut to the part inside, as the library asks.
 *
 * <p>Shapes are built once per block state and reused. A state whose shape depends
 * on where it is (offset plants) or on its block entity (an opening shulker box)
 * is read fresh each time instead.
 *
 * <h2>Cells</h2>
 * The static methods are {@link CellTest}s, to pass as method references:
 * {@code GameBlocks::isAir}. Every one answers false outside the world or its
 * height limits.
 *
 * <p><b>Game thread only.</b> One cursor is reused for every lookup, because a
 * ray asks dozens of cells and an allocation per cell is the cost that matters.
 */
public final class GameBlocks implements BlockView {

    public static final GameBlocks INSTANCE = new GameBlocks();

    /** What {@link #bedHeadAt} and {@link #anchorChargesAt} ask: one cursor, game thread only. */
    private static final BlockPos.Mutable CURSOR = new BlockPos.Mutable();

    private final Map<BlockState, BlockShape> shapes = new IdentityHashMap<>();

    private GameBlocks() {
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

    /** Forgets the cached shapes. Called on a world change, where a server may bring its own states. */
    void clear() {
        shapes.clear();
    }

    // ---------------------------------------------------------------- cells

    /** Strictly air: what an end crystal needs above its base ({@code EndCrystalItem}). */
    public static boolean isAir(int x, int y, int z) {
        BlockState state = stateAt(Voxels.world(), x, y, z);
        return state != null && state.isAir();
    }

    /** Air, or something a placed block replaces: grass, snow layers, fluids. */
    public static boolean isReplaceable(int x, int y, int z) {
        BlockState state = stateAt(Voxels.world(), x, y, z);
        return state != null && state.isReplaceable();
    }

    /** Somewhere a player can stand: replaceable, and nothing to collide with. */
    public static boolean isOpen(int x, int y, int z) {
        ClientWorld world = Voxels.world();
        BlockState state = stateAt(world, x, y, z);
        return state != null && state.isReplaceable() && state.getCollisionShape(world, CURSOR).isEmpty();
    }

    /** Obsidian or bedrock: what an end crystal can be placed on. */
    public static boolean isCrystalBase(int x, int y, int z) {
        BlockState state = stateAt(Voxels.world(), x, y, z);
        return state != null && (state.isOf(net.minecraft.block.Blocks.OBSIDIAN)
                || state.isOf(net.minecraft.block.Blocks.BEDROCK));
    }

    /** A block crystals and beds cannot break: a hole wall. See {@link Voxels#blastResistance}. */
    public static boolean isBlastProof(int x, int y, int z) {
        BlockState state = stateAt(Voxels.world(), x, y, z);
        return state != null && Voxels.blastResistance(state) != BlastResistance.NONE;
    }

    /** A block nothing breaks: what makes a hole safe. */
    public static boolean isUnbreakable(int x, int y, int z) {
        BlockState state = stateAt(Voxels.world(), x, y, z);
        return state != null && Voxels.blastResistance(state) == BlastResistance.UNBREAKABLE;
    }

    /** A full solid cube: what covers a face, for {@code FaceRule.exposed}. */
    public static boolean isFullBlock(int x, int y, int z) {
        ClientWorld world = Voxels.world();
        BlockState state = stateAt(world, x, y, z);
        return state != null && state.isFullCube(world, CURSOR);
    }

    /**
     * Something a block can be placed against: there, not replaced by what is
     * placed, with an outline to click.
     *
     * <p>Includes blocks that do something when clicked; a player clicking one
     * without sneaking uses it instead of placing. {@link #isSupport} leaves those out.
     */
    public static boolean isClickable(int x, int y, int z) {
        ClientWorld world = Voxels.world();
        BlockState state = stateAt(world, x, y, z);
        return state != null && !state.isReplaceable() && !state.getOutlineShape(world, CURSOR).isEmpty();
    }

    /** {@link #isClickable}, and placing against it never opens or uses it instead. */
    public static boolean isSupport(int x, int y, int z) {
        return isClickable(x, y, z) && !isInteractive(x, y, z);
    }

    /**
     * Whether right-clicking this block uses it rather than placing against it,
     * unless the player sneaks.
     *
     * <p>The game decides that per block in code, so this is a list: every block
     * with a block entity (chests, furnaces, beds, shulkers, hoppers), and the
     * doors, switches, workstations and respawn anchors that act on a click. A
     * block missing from it is only placed against.
     */
    public static boolean isInteractive(int x, int y, int z) {
        BlockState state = stateAt(Voxels.world(), x, y, z);
        if (state == null) {
            return false;
        }
        Block block = state.getBlock();
        return block instanceof BlockEntityProvider
                || block instanceof DoorBlock || block instanceof TrapdoorBlock || block instanceof FenceGateBlock
                || block instanceof ButtonBlock || block instanceof LeverBlock || block instanceof NoteBlock
                || block instanceof AbstractRedstoneGateBlock || block instanceof AnvilBlock
                || block instanceof CraftingTableBlock || block instanceof RespawnAnchorBlock
                || block instanceof CakeBlock || block instanceof StonecutterBlock || block instanceof GrindstoneBlock
                || block instanceof LoomBlock || block instanceof CartographyTableBlock;
    }

    /**
     * The {@link BedLookup}: the way the bed whose head is in this cell faces,
     * foot to head, or null.
     */
    public static dev.px.core.math.Direction bedHeadAt(int x, int y, int z) {
        BlockState state = stateAt(Voxels.world(), x, y, z);
        if (state == null || !(state.getBlock() instanceof BedBlock) || state.get(BedBlock.PART) != BedPart.HEAD) {
            return null;
        }
        return Voxels.toCore(state.get(BedBlock.FACING));
    }

    /** The {@link AnchorLookup}: the charges of the respawn anchor in this cell, or {@link AnchorLookup#NONE}. */
    public static int anchorChargesAt(int x, int y, int z) {
        BlockState state = stateAt(Voxels.world(), x, y, z);
        if (state == null || !state.isOf(net.minecraft.block.Blocks.RESPAWN_ANCHOR)) {
            return AnchorLookup.NONE;
        }
        return state.get(RespawnAnchorBlock.CHARGES);
    }

    // ------------------------------------------------------------ internals

    /** @return the state in this cell, leaving {@link #CURSOR} on it; null outside the world */
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

    /** The game's shape as boxes relative to the cell, each cut to the cell. */
    private static BlockShape shapeOf(VoxelShape shape) {
        if (shape.isEmpty()) {
            return BlockShape.EMPTY;
        }
        List<net.minecraft.util.math.Box> boxes = shape.getBoundingBoxes();
        Box[] inside = new Box[boxes.size()];
        int count = 0;
        boolean full = false;
        for (net.minecraft.util.math.Box box : boxes) {
            double minX = clamp(box.minX), minY = clamp(box.minY), minZ = clamp(box.minZ);
            double maxX = clamp(box.maxX), maxY = clamp(box.maxY), maxZ = clamp(box.maxZ);
            if (maxX > minX && maxY > minY && maxZ > minZ) {
                inside[count++] = Box.of(minX, minY, minZ, maxX, maxY, maxZ);
                full |= minX == 0d && minY == 0d && minZ == 0d && maxX == 1d && maxY == 1d && maxZ == 1d;
            }
        }
        if (count == 0) {
            return BlockShape.EMPTY;
        }
        if (full) {
            return BlockShape.FULL;
        }
        Box[] exact = new Box[count];
        System.arraycopy(inside, 0, exact, 0, count);
        return BlockShape.of(exact);
    }

    private static double clamp(double value) {
        return Math.max(0d, Math.min(1d, value));
    }
}
