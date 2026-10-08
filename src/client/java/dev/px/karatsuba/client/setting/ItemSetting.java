package dev.px.karatsuba.client.setting;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import dev.px.core.setting.Setting;
import lombok.Getter;

import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Locale;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

/**
 * A chosen set of items, picked from a searchable list of every item in the game.
 *
 * <p>Holds registry ids ({@code minecraft:cobblestone}), the same names the game
 * shows in F3 and the ones {@code Inventories} already finds items by, so a module
 * checks a stack with one {@link #contains} call. What the set <em>means</em>
 * belongs to the module: the inventory cleaner pairs it with a whitelist or
 * blacklist mode, a block search with what to highlight.
 *
 * <pre>{@code
 * private final ItemSetting junk = ItemSetting.items("Items", "minecraft:dirt", "minecraft:gravel")
 *         .describe("What the cleaner throws out");
 *
 * private final ItemSetting targets = ItemSetting.blocks("Blocks", "minecraft:spawner");
 * }</pre>
 *
 * <p>Ids are kept even when the game has no such item, such as one from a newer
 * version or a mod that is not loaded, so loading a profile somewhere else never
 * quietly empties it. The picker lists only what exists here.
 *
 * <p>Declared as a field like any other setting, and saved with the profile. The
 * click GUI draws it with {@code ItemSettingRow}, registered in {@link SettingTypes}.
 */
@Getter
public final class ItemSetting extends Setting<Set<String>> {

    private static final String DEFAULT_NAMESPACE = "minecraft:";

    /** Which items the picker lists. */
    public enum Scope {
        /** Every item. */
        ITEMS,
        /** Every block, held by block id: {@code redstone_wire}, not the {@code redstone} that places it, and blocks with no item such as {@code nether_portal}. */
        BLOCKS
    }

    private final Scope scope;

    public ItemSetting(String name, Scope scope, String... selectedByDefault) {
        super(name, normalise(Arrays.asList(selectedByDefault)));
        this.scope = scope == null ? Scope.ITEMS : scope;
    }

    /** A picker over every item. */
    public static ItemSetting items(String name, String... selectedByDefault) {
        return new ItemSetting(name, Scope.ITEMS, selectedByDefault);
    }

    /** A picker over blocks, by block id: what a search or a render filter asks about. */
    public static ItemSetting blocks(String name, String... selectedByDefault) {
        return new ItemSetting(name, Scope.BLOCKS, selectedByDefault);
    }

    // ---------------------------------------------------------------- reads

    /** @param id a registry id; {@code dirt} is read as {@code minecraft:dirt} */
    public boolean contains(String id) {
        String key = normalise(id);
        return key != null && get().contains(key);
    }

    public boolean isEmpty() {
        return get().isEmpty();
    }

    public int size() {
        return get().size();
    }

    // --------------------------------------------------------------- writes

    public void toggle(String id) {
        select(id, !contains(id));
    }

    public void select(String id, boolean selected) {
        String key = normalise(id);
        if (key == null) {
            return;
        }
        Set<String> updated = new TreeSet<>(get());
        if (selected) {
            updated.add(key);
        } else {
            updated.remove(key);
        }
        set(updated);
    }

    public void clear() {
        set(Collections.emptySet());
    }

    /** Every write ends up an unmodifiable, sorted, normalised copy, so equal sets compare equal. */
    @Override
    protected Set<String> coerce(Set<String> incoming) {
        return normalise(incoming);
    }

    // ---------------------------------------------------------- persistence

    @Override
    public String getTypeId() {
        return "item-list";
    }

    @Override
    public JsonElement toJson() {
        JsonArray array = new JsonArray();
        for (String id : get()) {
            array.add(id);
        }
        return array;
    }

    @Override
    public void fromJson(JsonElement json) {
        if (json == null || !json.isJsonArray()) {
            return;
        }
        Set<String> loaded = new TreeSet<>();
        for (JsonElement element : json.getAsJsonArray()) {
            if (element.isJsonPrimitive()) {
                String key = normalise(element.getAsString());
                if (key != null) {
                    loaded.add(key);
                }
            }
        }
        setSilently(loaded);
    }

    @Override
    public String displayValue() {
        int count = get().size();
        return count == 0 ? "None" : count + (count == 1 ? " item" : " items");
    }

    // ------------------------------------------------------------- internal

    private static Set<String> normalise(Collection<String> ids) {
        Set<String> result = new TreeSet<>();
        for (String id : ids) {
            String key = normalise(id);
            if (key != null) {
                result.add(key);
            }
        }
        return Collections.unmodifiableSet(result);
    }

    /** @return the id lower-cased with a namespace, or null if blank */
    private static String normalise(String id) {
        if (id == null) {
            return null;
        }
        String trimmed = id.trim().toLowerCase(Locale.ROOT);
        if (trimmed.isEmpty()) {
            return null;
        }
        return trimmed.indexOf(':') < 0 ? DEFAULT_NAMESPACE + trimmed : trimmed;
    }

    // --------------------------------------------------------------- fluent

    @Override
    public ItemSetting describe(String text) {
        super.describe(text);
        return this;
    }

    @Override
    public ItemSetting visibleWhen(BooleanSupplier condition) {
        super.visibleWhen(condition);
        return this;
    }

    @Override
    public ItemSetting onChange(Consumer<Set<String>> listener) {
        super.onChange(listener);
        return this;
    }
}
