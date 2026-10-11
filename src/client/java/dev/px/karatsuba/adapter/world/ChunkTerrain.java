package dev.px.karatsuba.adapter.world;

import dev.px.core.Core;
import dev.px.core.event.impl.WorldEvent;
import dev.px.karatsuba.adapter.event.game.world.BlockChangeEvent;
import dev.px.karatsuba.adapter.event.game.world.ChunkEvent;
import dev.px.karatsuba.pathing.Terrain;
import net.minecraft.block.BlockState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.world.EmptyBlockView;
import net.minecraft.world.chunk.ChunkSection;
import net.minecraft.world.chunk.PalettedContainer;
import net.minecraft.world.chunk.WorldChunk;

import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.Executor;

/**
 * The world as long-range planning sees it: two bits a block, for every chunk
 * the client has loaded, kept on a thread of the planner's own.
 *
 * <p>Each block is one of four things to a walking player: <b>open</b> (nothing to
 * hit, or so little a player walks on it: carpet, a snow layer), <b>floor</b>
 * (something to stand on top of, its collision topping out between 0.8 and 1
 * block: full blocks, farmland, paths, soul sand, chests), <b>blocked</b>
 * (anything else solid: fences, walls, slabs, stairs, a coarse planner's loss and
 * the local planner's to walk) and <b>avoid</b> (fluids and anything that hurts).
 * Twenty-four kilobytes a chunk, the same budget Baritone's chunk cache spends.
 *
 * <h2>Threads</h2>
 * Chunk access is only safe on the game thread, so the game thread only copies:
 * when a chunk loads, its sections' block palettes (microseconds); when a block
 * changes, its new state. Everything else (turning a chunk into bits, every
 * {@link Terrain} query) happens on the executor given, which must be a single
 * thread: the planner's. Nothing here locks, because only that thread ever reads
 * or writes the bits.
 *
 * <p>Chunks the client unloads are kept, up to {@link #KEPT_UNLOADED}, so a route
 * back the way the player came still knows the ground.
 */
public final class ChunkTerrain implements Terrain {

    /** Unloaded chunks remembered, beyond those loaded: about 6 MB. */
    public static final int KEPT_UNLOADED = 256;

    private static final byte OPEN = 0;
    private static final byte FLOOR = 1;
    private static final byte BLOCKED = 2;
    private static final byte AVOID = 3;

    /** What changed, in blocks inclusive; told on the planner's thread. */
    public interface Listener {
        void changed(int minX, int minY, int minZ, int maxX, int maxY, int maxZ);

        /** The world itself changed: everything known is gone. */
        void cleared();
    }

    private final Executor worker;
    private final Listener listener;

    // ---- the planner's thread only ---------------------------------------------

    private final Map<Long, long[]> chunks = new HashMap<>();
    /** Unloaded chunks still remembered, oldest first. */
    private final LinkedHashMap<Long, Boolean> unloaded = new LinkedHashMap<>();
    private final Map<BlockState, Byte> classes = new HashMap<>();
    private int bottomY;
    private int height;

    private ChunkTerrain(Executor worker, Listener listener) {
        this.worker = worker;
        this.listener = listener;
    }

    /**
     * Starts keeping the terrain: listens for chunks and blocks on Core's bus.
     * Call once, on the game thread.
     *
     * @param worker   the single thread every query will be asked on
     * @param listener told, on that thread, what changed
     */
    public static ChunkTerrain start(Executor worker, Listener listener) {
        ChunkTerrain terrain = new ChunkTerrain(worker, listener);
        Core.bus().on(ChunkEvent.class, terrain::onChunk);
        Core.bus().on(BlockChangeEvent.class, terrain::onBlock);
        Core.bus().on(WorldEvent.class, event -> terrain.worker.execute(terrain::clear));
        return terrain;
    }

    // ---------------------------------------------------------- game thread

    private void onChunk(ChunkEvent event) {
        long key = ChunkPos.toLong(event.getChunkX(), event.getChunkZ());
        if (!event.isLoaded()) {
            worker.execute(() -> forget(key));
            return;
        }
        ClientWorld world = MinecraftClient.getInstance().world;
        if (world == null || !world.getChunkManager().isChunkLoaded(event.getChunkX(), event.getChunkZ())) {
            return;
        }
        WorldChunk chunk = world.getChunk(event.getChunkX(), event.getChunkZ());
        ChunkSection[] sections = chunk.getSectionArray();
        @SuppressWarnings("unchecked")
        PalettedContainer<BlockState>[] copies = new PalettedContainer[sections.length];
        for (int i = 0; i < sections.length; i++) {
            if (!sections[i].isEmpty()) {
                copies[i] = sections[i].getBlockStateContainer().copy();
            }
        }
        int bottom = world.getBottomY();
        int tall = world.getHeight();
        int cx = event.getChunkX();
        int cz = event.getChunkZ();
        worker.execute(() -> store(key, cx, cz, copies, bottom, tall));
    }

    private void onBlock(BlockChangeEvent event) {
        ClientWorld world = MinecraftClient.getInstance().world;
        if (world == null) {
            return;
        }
        BlockPos pos = Voxels.toBlockPos(event.getPosition());
        BlockState state = world.getBlockState(pos);
        int x = pos.getX();
        int y = pos.getY();
        int z = pos.getZ();
        worker.execute(() -> set(x, y, z, state));
    }

    // -------------------------------------------------------- planner thread

    private void store(long key, int cx, int cz, PalettedContainer<BlockState>[] copies, int bottom, int tall) {
        if (bottom != bottomY || tall != height) {
            clear();
            bottomY = bottom;
            height = tall;
        }
        long[] bits = new long[(16 * 16 * height * 2 + 63) / 64];
        for (int s = 0; s < copies.length; s++) {
            PalettedContainer<BlockState> section = copies[s];
            if (section == null) {
                continue;
            }
            for (int y = 0; y < 16; y++) {
                int cellY = s * 16 + y;
                if (cellY >= height) {
                    break;
                }
                for (int z = 0; z < 16; z++) {
                    for (int x = 0; x < 16; x++) {
                        byte kind = classify(section.get(x, y, z));
                        if (kind != OPEN) {
                            write(bits, index(x, cellY, z), kind);
                        }
                    }
                }
            }
        }
        chunks.put(key, bits);
        unloaded.remove(key);
        listener.changed(cx * 16, bottomY, cz * 16, cx * 16 + 15, bottomY + height - 1, cz * 16 + 15);
    }

    private void set(int x, int y, int z, BlockState state) {
        long[] bits = chunks.get(ChunkPos.toLong(x >> 4, z >> 4));
        if (bits == null || y < bottomY || y >= bottomY + height) {
            return;
        }
        int index = index(x & 15, y - bottomY, z & 15);
        byte kind = classify(state);
        if (read(bits, index) == kind) {
            return;
        }
        write(bits, index, kind);
        listener.changed(x, y, z, x, y, z);
    }

    private void forget(long key) {
        if (!chunks.containsKey(key)) {
            return;
        }
        unloaded.put(key, Boolean.TRUE);
        Iterator<Long> oldest = unloaded.keySet().iterator();
        while (unloaded.size() > KEPT_UNLOADED && oldest.hasNext()) {
            chunks.remove(oldest.next());
            oldest.remove();
        }
    }

    private void clear() {
        chunks.clear();
        unloaded.clear();
        listener.cleared();
    }

    private byte classify(BlockState state) {
        Byte known = classes.get(state);
        if (known != null) {
            return known;
        }
        byte kind;
        if (!state.getFluidState().isEmpty() || Blocks.hurts(state)) {
            kind = AVOID;
        } else {
            VoxelShape shape = state.getCollisionShape(EmptyBlockView.INSTANCE, BlockPos.ORIGIN);
            double top = shape.isEmpty() ? 0d : shape.getMax(Direction.Axis.Y);
            if (top <= 0.2d) {
                kind = OPEN;
            } else if (top >= 0.8d && top <= 1d + 1e-6d) {
                kind = FLOOR;
            } else {
                kind = BLOCKED;
            }
        }
        classes.put(state, kind);
        return kind;
    }

    // ------------------------------------------------------------ queries

    private byte kind(int x, int y, int z) {
        if (y < bottomY) {
            return BLOCKED;
        }
        if (y >= bottomY + height) {
            return OPEN;
        }
        long[] bits = chunks.get(ChunkPos.toLong(x >> 4, z >> 4));
        return bits == null ? OPEN : read(bits, index(x & 15, y - bottomY, z & 15));
    }

    @Override
    public boolean isOpen(int x, int y, int z) {
        return kind(x, y, z) == OPEN && kind(x, y + 1, z) == OPEN;
    }

    @Override
    public boolean isStandable(int x, int y, int z) {
        return isOpen(x, y, z) && kind(x, y - 1, z) == FLOOR;
    }

    @Override
    public boolean isKnown(int x, int z) {
        return chunks.containsKey(ChunkPos.toLong(x >> 4, z >> 4));
    }

    /** @return chunks known, loaded or remembered */
    public int size() {
        return chunks.size();
    }

    // -------------------------------------------------------------- bits

    private static int index(int x, int y, int z) {
        return (y << 8) | (z << 4) | x;
    }

    private static byte read(long[] bits, int index) {
        int bit = index << 1;
        return (byte) ((bits[bit >>> 6] >>> (bit & 63)) & 3L);
    }

    private static void write(long[] bits, int index, byte kind) {
        int bit = index << 1;
        int word = bit >>> 6;
        int shift = bit & 63;
        bits[word] = (bits[word] & ~(3L << shift)) | ((long) kind << shift);
    }
}
