package dev.px.karatsuba.adapter.world;

import dev.px.core.math.Vec3i;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.registry.Registries;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.chunk.ChunkSection;
import net.minecraft.world.chunk.ChunkStatus;
import net.minecraft.world.chunk.WorldChunk;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.BiConsumer;

/**
 * Finds blocks across loaded chunks, fast enough to run on the game thread.
 *
 * <p>The trick is the palette. Every 16×16×16 section of a chunk keeps the list
 * of block states it contains, so "is there a spawner in here?" is a look at a
 * handful of entries, not four thousand blocks. A section whose palette has none
 * of the targets is skipped whole, which is nearly all of them; only a section
 * that does contain one is walked. A chunk of plain stone and dirt costs about
 * two dozen palette checks.
 *
 * <p>Blocks are named by block id ({@code minecraft:spawner}), and resolved once
 * into a {@link Matcher} the scan reads.
 *
 * <p><b>Game thread only.</b>
 */
public final class BlockScan {

    private BlockScan() {
    }

    /**
     * What a scan looks for: a set of blocks, resolved from ids. Immutable, and
     * opaque outside the adapter. Ids the game does not have are ignored.
     */
    public static final class Matcher {

        static final Matcher NONE = new Matcher(Collections.emptySet());

        private final Set<Block> blocks;

        private Matcher(Set<Block> blocks) {
            this.blocks = blocks;
        }

        public boolean isEmpty() {
            return blocks.isEmpty();
        }

        /** @return whether this block id is one being looked for */
        public boolean matches(String blockId) {
            Block block = blockOf(blockId);
            return block != null && blocks.contains(block);
        }

        boolean matches(BlockState state) {
            return blocks.contains(state.getBlock());
        }
    }

    /** @return a matcher for these block ids */
    public static Matcher matcher(Collection<String> blockIds) {
        Set<Block> blocks = new HashSet<>();
        for (String id : blockIds) {
            Block block = blockOf(id);
            if (block != null) {
                blocks.add(block);
            }
        }
        return blocks.isEmpty() ? Matcher.NONE : new Matcher(Collections.unmodifiableSet(blocks));
    }

    /**
     * @return the chunks loaded within {@code radius} chunks of the given one, as
     *         {@code {x, z}} pairs, nearest first
     */
    public static List<int[]> loadedChunks(int centerX, int centerZ, int radius) {
        ClientWorld world = MinecraftClient.getInstance().world;
        if (world == null) {
            return Collections.emptyList();
        }
        List<int[]> found = new ArrayList<>();
        for (int ring = 0; ring <= radius; ring++) {
            for (int dx = -ring; dx <= ring; dx++) {
                for (int dz = -ring; dz <= ring; dz++) {
                    if (Math.max(Math.abs(dx), Math.abs(dz)) != ring) {
                        continue;
                    }
                    if (chunk(world, centerX + dx, centerZ + dz) != null) {
                        found.add(new int[]{centerX + dx, centerZ + dz});
                    }
                }
            }
        }
        return found;
    }

    /** @return whether that chunk is loaded */
    public static boolean isLoaded(int chunkX, int chunkZ) {
        ClientWorld world = MinecraftClient.getInstance().world;
        return world != null && chunk(world, chunkX, chunkZ) != null;
    }

    /**
     * Reports every matching block in one chunk.
     *
     * @param sink handed each block's position and block id
     * @return false if the chunk is not loaded, so nothing could be scanned
     */
    public static boolean scan(int chunkX, int chunkZ, Matcher matcher, BiConsumer<Vec3i, String> sink) {
        ClientWorld world = MinecraftClient.getInstance().world;
        WorldChunk chunk = world == null ? null : chunk(world, chunkX, chunkZ);
        if (chunk == null) {
            return false;
        }
        if (matcher.isEmpty()) {
            return true;
        }
        ChunkSection[] sections = chunk.getSectionArray();
        int baseX = chunkX << 4;
        int baseZ = chunkZ << 4;
        Map<Block, String> ids = new HashMap<>();
        for (int index = 0; index < sections.length; index++) {
            ChunkSection section = sections[index];
            if (section == null || section.isEmpty() || !section.hasAny(matcher::matches)) {
                continue;
            }
            int baseY = chunk.sectionIndexToCoord(index) << 4;
            for (int y = 0; y < 16; y++) {
                for (int z = 0; z < 16; z++) {
                    for (int x = 0; x < 16; x++) {
                        BlockState state = section.getBlockState(x, y, z);
                        if (matcher.matches(state)) {
                            String id = ids.computeIfAbsent(state.getBlock(),
                                    block -> Registries.BLOCK.getId(block).toString());
                            sink.accept(Vec3i.of(baseX + x, baseY + y, baseZ + z), id);
                        }
                    }
                }
            }
        }
        return true;
    }

    /**
     * @return the colour maps draw this block in, as {@code 0xRRGGBB}; grey for a
     *         block a map leaves clear, such as glass
     */
    public static int mapColor(String blockId) {
        Block block = blockOf(blockId);
        ClientWorld world = MinecraftClient.getInstance().world;
        if (block == null || world == null) {
            return 0xA0A0A0;
        }
        int color = block.getDefaultState().getMapColor(world, BlockPos.ORIGIN).color;
        return color == 0 ? 0xA0A0A0 : color;
    }

    private static WorldChunk chunk(ClientWorld world, int chunkX, int chunkZ) {
        return (WorldChunk) world.getChunkManager().getChunk(chunkX, chunkZ, ChunkStatus.FULL, false);
    }

    private static Block blockOf(String id) {
        Identifier identifier = id == null ? null : Identifier.tryParse(id);
        if (identifier == null || !Registries.BLOCK.containsId(identifier)) {
            return null;
        }
        return Registries.BLOCK.get(identifier);
    }
}
