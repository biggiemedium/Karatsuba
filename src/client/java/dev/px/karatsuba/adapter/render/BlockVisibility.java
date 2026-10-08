package dev.px.karatsuba.adapter.render;

import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.registry.Registries;
import net.minecraft.util.Identifier;

import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

/**
 * Which blocks the world is drawn with: all of them, or only a chosen few.
 *
 * <p>Read by the chunk mixins ({@code mixin/render/BlockRenderManagerMixin} and
 * the rest) while chunks are built. That happens on the game's worker threads, so
 * the whole state is one immutable value behind a volatile field: a reader sees
 * the old filter or the new one, never half of each.
 *
 * <p>While a filter is on:
 * <ul>
 *   <li>a block not on the list is not drawn at all: no model, no fluid, no block
 *       entity (chests, signs);</li>
 *   <li>a listed block draws every face that a hidden block would have covered,
 *       so an ore inside stone shows as a whole cube;</li>
 *   <li>chunk occlusion culling is off, since solid walls no longer hide what is
 *       behind them;</li>
 *   <li>with full bright, every drawn block is lit fully and without ambient
 *       occlusion, since an ore buried in stone gets no light of its own.</li>
 * </ul>
 * Changing the filter rebuilds every chunk, which takes a moment.
 *
 * <p>Vanilla's renderer only. A renderer replacement such as Sodium builds chunks
 * its own way and ignores this.
 */
public final class BlockVisibility {

    private static final class Filter {

        final Set<Block> shown;
        final boolean fullBright;

        Filter(Set<Block> shown, boolean fullBright) {
            this.shown = shown;
            this.fullBright = fullBright;
        }
    }

    private static volatile Filter filter;

    private BlockVisibility() {
    }

    /**
     * Draws only these blocks, by block id, and rebuilds the world's chunks.
     *
     * @param fullBright light every drawn block fully, without ambient occlusion
     */
    public static void only(Collection<String> blockIds, boolean fullBright) {
        Set<Block> shown = new HashSet<>();
        for (String id : blockIds) {
            Identifier identifier = Identifier.tryParse(id);
            if (identifier != null && Registries.BLOCK.containsId(identifier)) {
                shown.add(Registries.BLOCK.get(identifier));
            }
        }
        filter = new Filter(Collections.unmodifiableSet(shown), fullBright);
        reload();
    }

    /** Draws every block again, and rebuilds the world's chunks. Nothing if no filter was on. */
    public static void all() {
        if (filter != null) {
            filter = null;
            reload();
        }
    }

    // ------------------------------------------------------- for the mixins

    /** @return whether a filter is on */
    public static boolean isFiltering() {
        return filter != null;
    }

    /** @return whether this block is filtered out; false with no filter on */
    public static boolean isHidden(BlockState state) {
        Filter current = filter;
        return current != null && !current.shown.contains(state.getBlock());
    }

    /** @return whether drawn blocks are lit fully: a filter is on, with full bright */
    public static boolean isFullBright() {
        Filter current = filter;
        return current != null && current.fullBright;
    }

    private static void reload() {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.worldRenderer != null && client.world != null) {
            client.worldRenderer.reload();
        }
    }
}
