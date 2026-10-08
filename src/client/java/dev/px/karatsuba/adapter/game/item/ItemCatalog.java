package dev.px.karatsuba.adapter.game.item;

import lombok.Getter;
import net.minecraft.block.Block;
import net.minecraft.client.MinecraftClient;
import net.minecraft.item.BlockItem;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.registry.Registries;
import net.minecraft.resource.featuretoggle.FeatureFlags;
import net.minecraft.resource.featuretoggle.FeatureSet;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Every item the game knows, by registry id and display name.
 *
 * <p>What a picker lists: the inventory cleaner's whitelist, the block search's
 * targets. Each {@link Entry} is plain values, so the client can sort, filter and
 * store ids without touching an {@code Item}; the stack an icon is drawn from stays
 * package-private, as it does in {@link GameItem}.
 *
 * <p>Built on first use and rebuilt only when what it shows would change: the
 * language (names are translated) or the features the world has switched on (an
 * experimental item is listed only where it exists). Air is never listed.
 *
 * <p><b>Game thread only.</b>
 */
public final class ItemCatalog {

    private static List<Entry> entries = Collections.emptyList();
    private static List<Entry> blockEntries = Collections.emptyList();
    private static Map<String, Entry> byId = Collections.emptyMap();

    /** What the current list was built for; a change to either rebuilds it. */
    private static String builtLanguage;
    private static FeatureSet builtFeatures;

    private ItemCatalog() {
    }

    /** @return every item, sorted by display name; never null, unmodifiable */
    public static List<Entry> all() {
        refresh();
        return entries;
    }

    /**
     * @return every block, by its <em>block</em> id, sorted by display name; never
     *         null, unmodifiable
     *
     * <p>Not the same list as the items that place blocks. Some blocks have no item
     * at all (a nether portal, fire, an end gateway), and some are placed by an
     * item with another id (redstone places {@code redstone_wire}, wheat seeds
     * place {@code wheat}). A search or a render filter asks about the block in the
     * world, so it needs the block's own id. Each entry's icon is the block's item,
     * or nothing for a block without one. Air is never listed.
     */
    public static List<Entry> blocks() {
        refresh();
        return blockEntries;
    }

    /** @return the entry for a registry id such as {@code minecraft:dirt}, or null if none is listed */
    public static Entry find(String id) {
        refresh();
        return id == null ? null : byId.get(id);
    }

    private static void refresh() {
        MinecraftClient client = MinecraftClient.getInstance();
        String language = client.getLanguageManager().getLanguage();
        FeatureSet features = client.world != null
                ? client.world.getEnabledFeatures()
                : FeatureFlags.DEFAULT_ENABLED_FEATURES;
        if (Objects.equals(language, builtLanguage) && features.equals(builtFeatures)) {
            return;
        }

        List<Entry> built = new ArrayList<>();
        for (Item item : Registries.ITEM) {
            if (item == Items.AIR || !item.isEnabled(features)) {
                continue;
            }
            ItemStack stack = item.getDefaultStack();
            built.add(new Entry(Registries.ITEM.getId(item).toString(),
                    stack.getName().getString(), item instanceof BlockItem, stack));
        }
        built.sort(Comparator.comparing(Entry::getName, String.CASE_INSENSITIVE_ORDER)
                .thenComparing(Entry::getId));

        Map<String, Entry> index = new HashMap<>(built.size() * 2);
        for (Entry entry : built) {
            index.put(entry.getId(), entry);
        }

        List<Entry> blocks = new ArrayList<>();
        for (Block block : Registries.BLOCK) {
            if (block.getDefaultState().isAir() || !block.isEnabled(features)) {
                continue;
            }
            Item item = block.asItem();
            ItemStack stack = item == Items.AIR ? ItemStack.EMPTY : item.getDefaultStack();
            blocks.add(new Entry(Registries.BLOCK.getId(block).toString(), block.getName().getString(), true, stack));
        }
        blocks.sort(Comparator.comparing(Entry::getName, String.CASE_INSENSITIVE_ORDER)
                .thenComparing(Entry::getId));

        entries = Collections.unmodifiableList(built);
        blockEntries = Collections.unmodifiableList(blocks);
        byId = index;
        builtLanguage = language;
        builtFeatures = features;
    }

    /** One item, as a picker shows it. */
    @Getter
    public static final class Entry {

        /** The registry id, such as {@code minecraft:oak_log}. */
        private final String id;

        /** The name in the player's language. */
        private final String name;

        /** Whether it places a block: what a block search can look for. */
        private final boolean block;

        @Getter(lombok.AccessLevel.NONE)
        private final ItemStack stack;

        Entry(String id, String name, boolean block, ItemStack stack) {
            this.id = id;
            this.name = name;
            this.block = block;
            this.stack = stack;
        }

        /** The stack to draw. Package-private on purpose; see the class comment. */
        ItemStack stack() {
            return stack;
        }
    }
}
