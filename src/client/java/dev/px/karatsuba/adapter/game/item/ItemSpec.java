package dev.px.karatsuba.adapter.game.item;

import dev.px.karatsuba.adapter.world.Voxels;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.enchantment.Enchantment;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.util.Identifier;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * A description of an item to make: which item, how many, which enchantments.
 *
 * <p>The write-side counterpart of {@link GameItem}. A module says what it wants
 * in registry ids &mdash; {@code "netherite_sword"}, {@code "sharpness"} &mdash;
 * and the adapter turns that into a real stack only when one is needed. So a
 * loadout is plain data a module can hold in a constant, and nothing outside this
 * package ever builds an {@code ItemStack}.
 *
 * <pre>{@code
 * ItemSpec sword = ItemSpec.of("netherite_sword")
 *         .enchant("sharpness", 5)
 *         .enchant("unbreaking", 3);
 * }</pre>
 *
 * <p>Immutable: each call returns a new spec, so a shared constant cannot be
 * changed from under its other users. Ids without a namespace are
 * {@code minecraft:}. An unknown item makes an empty stack, and an unknown
 * enchantment is skipped, rather than either throwing mid-game.
 */
public final class ItemSpec {

    /** No item. Equipping it clears the slot. */
    public static final ItemSpec NONE = new ItemSpec("air", 0, Collections.emptyMap());

    private final String itemId;
    private final int count;
    private final Map<String, Integer> enchantments;

    private ItemSpec(String itemId, int count, Map<String, Integer> enchantments) {
        this.itemId = itemId;
        this.count = count;
        this.enchantments = enchantments;
    }

    /** One of an item, by registry id. */
    public static ItemSpec of(String itemId) {
        return new ItemSpec(itemId, 1, Collections.emptyMap());
    }

    /** @return this spec with a different stack size, clamped to at least 1 */
    public ItemSpec count(int newCount) {
        return new ItemSpec(itemId, Math.max(1, newCount), enchantments);
    }

    /** @return this spec with one more enchantment, by registry id; a repeat replaces the level */
    public ItemSpec enchant(String enchantmentId, int level) {
        Map<String, Integer> more = new LinkedHashMap<>(enchantments);
        more.put(enchantmentId, level);
        return new ItemSpec(itemId, count, Collections.unmodifiableMap(more));
    }

    /**
     * Builds the stack.
     *
     * <p>Needs a world: enchantments are data-driven since 1.21 and live in the
     * world's registries, not a static table. Out of a world the item is made
     * without them.
     */
    ItemStack toStack() {
        if (count <= 0) {
            return ItemStack.EMPTY;
        }
        Item item = Registries.ITEM.getOptionalValue(Identifier.of(itemId)).orElse(Items.AIR);
        if (item == Items.AIR) {
            return ItemStack.EMPTY;
        }
        ItemStack stack = new ItemStack(item, count);

        ClientWorld world = Voxels.world();
        if (world != null && !enchantments.isEmpty()) {
            Registry<Enchantment> registry = world.getRegistryManager().getOrThrow(RegistryKeys.ENCHANTMENT);
            enchantments.forEach((id, level) -> registry.getEntry(Identifier.of(id))
                    .ifPresent(entry -> stack.addEnchantment(entry, level)));
        }
        return stack;
    }
}
