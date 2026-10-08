package dev.px.karatsuba.client.module.render;

import dev.px.core.event.Stage;
import dev.px.core.event.Subscribe;
import dev.px.core.event.impl.Render3DEvent;
import dev.px.core.event.impl.TickEvent;
import dev.px.core.event.impl.WorldEvent;
import dev.px.core.math.Box;
import dev.px.core.math.Vec3;
import dev.px.core.math.Vec3i;
import dev.px.core.module.Module;
import dev.px.core.module.ModuleInfo;
import dev.px.core.render.Color;
import dev.px.core.render.Render;
import dev.px.core.setting.impl.BooleanSetting;
import dev.px.core.setting.impl.ColorSetting;
import dev.px.core.setting.impl.EnumSetting;
import dev.px.core.setting.impl.GroupSetting;
import dev.px.core.setting.impl.NumberSetting;
import dev.px.karatsuba.adapter.event.game.world.BlockChangeEvent;
import dev.px.karatsuba.adapter.event.game.world.ChunkEvent;
import dev.px.karatsuba.adapter.game.player.Players;
import dev.px.karatsuba.adapter.world.BlockScan;
import dev.px.karatsuba.client.setting.ItemSetting;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Finds blocks across every loaded chunk and marks them through walls: spawners,
 * ancient debris, portals, whatever is on the list.
 *
 * <h2>Scanned once, kept up to date</h2>
 * Each chunk is scanned when it arrives and forgotten when it leaves; after that,
 * single block changes keep the results right, with no rescan. Scanning skips
 * every chunk section whose palette holds none of the blocks
 * ({@link BlockScan}), so a chunk of stone costs a couple of dozen look-ups.
 * Chunks are scanned a few a tick ({@code Scan Speed}), nearest first, so turning
 * the module on or changing the list never stalls a frame.
 *
 * <h2>Drawn</h2>
 * Every frame, as boxes like Hole ESP's, of what the last tick picked: the
 * nearest {@code Max Shown} within {@code Range}. Coloured by the block's map
 * colour (stone grey, diamond ore cyan, debris brown) unless a colour is set.
 * Draws only; it writes nothing, so nothing needs arbitrating.
 */
@ModuleInfo(name = "Search", description = "Finds blocks in loaded chunks and marks them through walls",
        category = "Render")
public final class SearchModule extends Module {

    /** A drawn box is inset this much, so its faces do not z-fight the block's. */
    private static final double INSET = 0.002d;

    private final ItemSetting blocks = ItemSetting.blocks("Blocks",
                    "minecraft:spawner", "minecraft:ancient_debris", "minecraft:diamond_ore",
                    "minecraft:deepslate_diamond_ore")
            .describe("What to find")
            .onChange(ids -> rescan());

    private final NumberSetting<Integer> range = integer("Range", 128, 16, 512)
            .describe("Blocks from you within which finds are drawn");

    private final NumberSetting<Integer> maxShown = integer("Max Shown", 500, 10, 5000)
            .describe("Draw at most this many, nearest first");

    private final NumberSetting<Integer> scanSpeed = integer("Scan Speed", 4, 1, 32)
            .describe("Chunks scanned a tick while catching up");

    private final EnumSetting<ColorMode> colorMode = enumOf("Colour Mode", ColorMode.BLOCK)
            .describe("BLOCK uses each block's map colour; CUSTOM uses one colour for all");

    private final ColorSetting color = color("Colour", Color.of(255, 200, 40, 220))
            .visibleWhen(() -> colorMode.get() == ColorMode.CUSTOM);

    private final BooleanSetting fill = bool("Fill", true);

    private final NumberSetting<Float> fillOpacity = number("Fill Opacity", 0.2f, 0.05f, 1f).step(0.05f)
            .visibleWhen(fill);

    private final NumberSetting<Float> thickness = number("Thickness", 1.2f, 0.5f, 4f).step(0.1f);

    private final BooleanSetting tracers = bool("Tracers", false)
            .describe("A line from the crosshair to each find");

    private final BooleanSetting throughWalls = bool("Through Walls", true);

    private final GroupSetting appearance = group("Appearance", false)
            .containing(colorMode, color, fill, fillOpacity, thickness, tracers, throughWalls);

    /** Finds, by chunk: a chunk's are replaced whole when it is rescanned. */
    private final Map<Long, Map<Vec3i, String>> found = new HashMap<>();

    /** Chunks waiting to be scanned, and the same as a set so nothing is queued twice. */
    private final ArrayDeque<Long> queue = new ArrayDeque<>();
    private final Set<Long> queued = new HashSet<>();

    private BlockScan.Matcher matcher;

    /** What the last tick chose to draw: nearest first, within range. */
    private List<Find> shown = new ArrayList<>();

    private final Map<String, Color> blockColors = new HashMap<>();

    private int total;

    @Override
    protected void onEnable() {
        rescan();
    }

    @Override
    protected void onDisable() {
        clear();
    }

    @Subscribe
    private void onWorld(WorldEvent event) {
        clear();
        if (event.isLoaded()) {
            rescan();
        }
    }

    @Subscribe
    private void onChunk(ChunkEvent event) {
        long key = key(event.getChunkX(), event.getChunkZ());
        if (event.isLoaded()) {
            enqueue(key);
        } else {
            found.remove(key);
            if (queued.remove(key)) {
                queue.remove(key);
            }
        }
    }

    @Subscribe
    private void onBlockChange(BlockChangeEvent event) {
        if (matcher == null) {
            return;
        }
        Vec3i at = event.getPosition();
        long key = key(at.getX() >> 4, at.getZ() >> 4);
        if (matcher.matches(event.getCurrent())) {
            found.computeIfAbsent(key, ignored -> new HashMap<>()).put(at, event.getCurrent());
        } else if (matcher.matches(event.getPrevious())) {
            Map<Vec3i, String> chunk = found.get(key);
            if (chunk != null) {
                chunk.remove(at);
            }
        }
    }

    /** Scans a few queued chunks, then picks what to draw. */
    @Subscribe(stage = Stage.POST)
    private void onTick(TickEvent event) {
        if (!Players.present() || matcher == null) {
            return;
        }
        for (int scanned = 0; scanned < scanSpeed.getInt() && !queue.isEmpty(); scanned++) {
            long key = queue.poll();
            queued.remove(key);
            Map<Vec3i, String> chunk = new HashMap<>();
            if (BlockScan.scan(chunkX(key), chunkZ(key), matcher, chunk::put)) {
                if (chunk.isEmpty()) {
                    found.remove(key);
                } else {
                    found.put(key, chunk);
                }
            }
        }
        pick();
    }

    @Subscribe
    private void onRender3D(Render3DEvent event) {
        if (shown.isEmpty()) {
            return;
        }
        Render.setDepthTest(!throughWalls.isOn());
        try {
            for (Find find : shown) {
                Vec3i at = find.position();
                Box box = Box.of(at.getX() + INSET, at.getY() + INSET, at.getZ() + INSET,
                        at.getX() + 1d - INSET, at.getY() + 1d - INSET, at.getZ() + 1d - INSET);
                Color drawn = colorOf(find.blockId());
                if (fill.isOn()) {
                    Render.box(box, drawn.fade(fillOpacity.getFloat()));
                }
                Render.boxOutline(box, thickness.getFloat(), drawn);
                if (tracers.isOn()) {
                    Render.tracer(at.center(), 1f, drawn);
                }
            }
        } finally {
            Render.setDepthTest(true);
        }
    }

    @Override
    public String getDisplayInfo() {
        return String.valueOf(total);
    }

    // -------------------------------------------------------------- internal

    /** Forgets every find and queues every loaded chunk again, nearest first. */
    private void rescan() {
        clear();
        if (!isEnabled()) {
            return;
        }
        // Set even with no player yet: on joining a world the chunks arrive after
        // this, and each is queued as it does.
        matcher = BlockScan.matcher(blocks.get());
        if (!Players.present()) {
            return;
        }
        Vec3i feet = Players.blockPosition();
        int radius = Math.max(2, range.getInt() / 16 + 2);
        for (int[] chunk : BlockScan.loadedChunks(feet.getX() >> 4, feet.getZ() >> 4, radius)) {
            enqueue(key(chunk[0], chunk[1]));
        }
    }

    private void clear() {
        found.clear();
        queue.clear();
        queued.clear();
        shown = new ArrayList<>();
        blockColors.clear();
        total = 0;
        matcher = null;
    }

    private void enqueue(long key) {
        if (matcher != null && queued.add(key)) {
            queue.add(key);
        }
    }

    /** The nearest {@code Max Shown} finds within range, for the frames until the next tick. */
    private void pick() {
        Vec3 eyes = Players.eyePosition();
        double limit = (double) range.getInt() * range.getInt();
        List<Find> candidates = new ArrayList<>();
        int count = 0;
        for (Map<Vec3i, String> chunk : found.values()) {
            count += chunk.size();
            for (Map.Entry<Vec3i, String> entry : chunk.entrySet()) {
                double distance = entry.getKey().center().squaredDistanceTo(eyes);
                if (distance <= limit) {
                    candidates.add(new Find(entry.getKey(), entry.getValue(), distance));
                }
            }
        }
        candidates.sort(Comparator.comparingDouble(Find::distance));
        shown = candidates.size() > maxShown.getInt()
                ? new ArrayList<>(candidates.subList(0, maxShown.getInt())) : candidates;
        total = count;
    }

    private Color colorOf(String blockId) {
        if (colorMode.get() == ColorMode.CUSTOM) {
            return color.resolve();
        }
        return blockColors.computeIfAbsent(blockId, id -> Color.rgb(BlockScan.mapColor(id)).withAlpha(220));
    }

    private static long key(int chunkX, int chunkZ) {
        return ((long) chunkX << 32) | (chunkZ & 0xFFFFFFFFL);
    }

    private static int chunkX(long key) {
        return (int) (key >> 32);
    }

    private static int chunkZ(long key) {
        return (int) key;
    }

    private record Find(Vec3i position, String blockId, double distance) {
    }

    public enum ColorMode {
        /** Each block in its map colour. */
        BLOCK,
        /** One colour for everything. */
        CUSTOM
    }
}
