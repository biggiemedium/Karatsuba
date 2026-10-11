package dev.px.karatsuba.adapter.world;

import dev.px.core.math.Box;
import dev.px.core.math.Vec3;
import dev.px.core.math.Vec3i;
import dev.px.core.movement.simulation.CollisionSpace;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.ShapeContext;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.fluid.FluidState;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkSectionPos;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.world.BlockView;
import net.minecraft.world.chunk.ChunkSection;
import net.minecraft.world.chunk.PalettedContainer;
import net.minecraft.world.chunk.WorldChunk;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * A copy of the blocks around a point, for reading off the game thread.
 *
 * <p>{@link WorldCollisionSpace} reads the live world, and chunk access is not
 * safe off the game thread: a chunk can load, unload or have a block set while
 * another thread is halfway through reading it. This is what the game's own
 * chunk-meshing threads do instead. On the game thread, every chunk section in
 * reach has its block states copied (a few microseconds each, and empty sections
 * are skipped); after that this is a value nothing else writes, and any thread
 * may read it.
 *
 * <pre>{@code
 * WorldSnapshot world = WorldSnapshot.around(feet, 26);     // game thread
 * executor.submit(() -> plan(world));                       // any thread
 * }</pre>
 *
 * <p>Collisions are found as {@code World.getBlockCollisions} finds them: the
 * blocks the box overlaps and a block's border round it, the border counting
 * only for blocks taller or wider than a cube (fences, walls) and moving
 * pistons. Shapes that depend on who is touching them (scaffolding, powder snow)
 * answer for the local player as they were when the copy was made.
 *
 * <p>Outside the copy, and in chunks that were not loaded, everything is air,
 * which is how the client sees an unloaded chunk too.
 */
public final class WorldSnapshot implements CollisionSpace, BlockView {

    private final Map<Long, PalettedContainer<BlockState>> sections;
    private final int bottomY;
    private final int height;
    private final ShapeContext context;

    private WorldSnapshot(Map<Long, PalettedContainer<BlockState>> sections, int bottomY, int height,
                          ShapeContext context) {
        this.sections = sections;
        this.bottomY = bottomY;
        this.height = height;
        this.context = context;
    }

    /**
     * Copies every loaded section within {@code radius} blocks of {@code centre},
     * as a cube. <b>Game thread only.</b>
     *
     * @return the copy, or null out of a world
     */
    public static WorldSnapshot around(Vec3 centre, int radius) {
        MinecraftClient client = MinecraftClient.getInstance();
        ClientWorld world = client.world;
        if (world == null) {
            return null;
        }
        int lowX = ChunkSectionPos.getSectionCoord((int) Math.floor(centre.getX()) - radius);
        int highX = ChunkSectionPos.getSectionCoord((int) Math.floor(centre.getX()) + radius);
        int lowY = ChunkSectionPos.getSectionCoord((int) Math.floor(centre.getY()) - radius);
        int highY = ChunkSectionPos.getSectionCoord((int) Math.floor(centre.getY()) + radius);
        int lowZ = ChunkSectionPos.getSectionCoord((int) Math.floor(centre.getZ()) - radius);
        int highZ = ChunkSectionPos.getSectionCoord((int) Math.floor(centre.getZ()) + radius);

        Map<Long, PalettedContainer<BlockState>> copied = new HashMap<>();
        for (int cx = lowX; cx <= highX; cx++) {
            for (int cz = lowZ; cz <= highZ; cz++) {
                if (!world.getChunkManager().isChunkLoaded(cx, cz)) {
                    continue;
                }
                WorldChunk chunk = world.getChunk(cx, cz);
                for (int cy = lowY; cy <= highY; cy++) {
                    int index = world.sectionCoordToIndex(cy);
                    if (index < 0 || index >= chunk.getSectionArray().length) {
                        continue;
                    }
                    ChunkSection section = chunk.getSection(index);
                    if (section.isEmpty()) {
                        continue;
                    }
                    copied.put(ChunkSectionPos.asLong(cx, cy, cz), section.getBlockStateContainer().copy());
                }
            }
        }
        ShapeContext context = client.player != null ? ShapeContext.of(client.player) : ShapeContext.absent();
        return new WorldSnapshot(copied, world.getBottomY(), world.getHeight(), context);
    }

    // ------------------------------------------------------------- reading

    @Override
    public BlockState getBlockState(BlockPos pos) {
        int y = pos.getY();
        if (y < bottomY || y >= bottomY + height) {
            return Blocks.VOID_AIR.getDefaultState();
        }
        PalettedContainer<BlockState> section = sections.get(ChunkSectionPos.asLong(
                ChunkSectionPos.getSectionCoord(pos.getX()), ChunkSectionPos.getSectionCoord(y),
                ChunkSectionPos.getSectionCoord(pos.getZ())));
        return section == null ? Blocks.AIR.getDefaultState() : section.get(pos.getX() & 15, y & 15, pos.getZ() & 15);
    }

    @Override
    public FluidState getFluidState(BlockPos pos) {
        return getBlockState(pos).getFluidState();
    }

    /** No block entities are copied; no vanilla collision shape asks for one. */
    @Override
    public BlockEntity getBlockEntity(BlockPos pos) {
        return null;
    }

    @Override
    public int getHeight() {
        return height;
    }

    @Override
    public int getBottomY() {
        return bottomY;
    }

    /** @return whether standing in or on this cell hurts, as {@link dev.px.karatsuba.adapter.world.Blocks#hurts(Vec3i)} */
    public boolean hurts(Vec3i cell) {
        return dev.px.karatsuba.adapter.world.Blocks.hurts(getBlockState(Voxels.toBlockPos(cell)));
    }

    /** @return whether this cell holds any fluid */
    public boolean isLiquid(Vec3i cell) {
        return !getBlockState(Voxels.toBlockPos(cell)).getFluidState().isEmpty();
    }

    // ----------------------------------------------------------- collision

    @Override
    public List<Box> boxesIn(Box region) {
        net.minecraft.util.math.Box area = Voxels.toGameBox(region);
        int minX = (int) Math.floor(area.minX - 1.0E-7d) - 1;
        int maxX = (int) Math.floor(area.maxX + 1.0E-7d) + 1;
        int minY = (int) Math.floor(area.minY - 1.0E-7d) - 1;
        int maxY = (int) Math.floor(area.maxY + 1.0E-7d) + 1;
        int minZ = (int) Math.floor(area.minZ - 1.0E-7d) - 1;
        int maxZ = (int) Math.floor(area.maxZ + 1.0E-7d) + 1;

        List<Box> boxes = null;
        BlockPos.Mutable pos = new BlockPos.Mutable();
        for (int x = minX; x <= maxX; x++) {
            for (int y = minY; y <= maxY; y++) {
                for (int z = minZ; z <= maxZ; z++) {
                    int edges = (x == minX || x == maxX ? 1 : 0) + (y == minY || y == maxY ? 1 : 0)
                            + (z == minZ || z == maxZ ? 1 : 0);
                    if (edges == 3) {
                        continue;
                    }
                    pos.set(x, y, z);
                    BlockState state = getBlockState(pos);
                    if (state.isAir()
                            || (edges == 1 && !state.exceedsCube())
                            || (edges == 2 && !state.isOf(Blocks.MOVING_PISTON))) {
                        continue;
                    }
                    VoxelShape shape = state.getCollisionShape(this, pos, context);
                    if (shape.isEmpty()) {
                        continue;
                    }
                    for (net.minecraft.util.math.Box part : shape.getBoundingBoxes()) {
                        net.minecraft.util.math.Box placed = part.offset(x, y, z);
                        if (placed.intersects(area)) {
                            if (boxes == null) {
                                boxes = new ArrayList<>();
                            }
                            boxes.add(Voxels.toBox(placed));
                        }
                    }
                }
            }
        }
        return boxes == null ? Collections.emptyList() : boxes;
    }

    @Override
    public double slipperinessAt(Vec3 position) {
        BlockPos pos = BlockPos.ofFloored(position.getX(), position.getY(), position.getZ());
        BlockState state = getBlockState(pos);
        return state.isAir() ? CollisionSpace.super.slipperinessAt(position) : state.getBlock().getSlipperiness();
    }
}
