package dev.px.karatsuba.adapter.game.item;

import it.unimi.dsi.fastutil.objects.Object2IntMap;
import lombok.Getter;
import net.minecraft.enchantment.Enchantment;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.registry.tag.EnchantmentTags;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * One stack of items, in plain values.
 *
 * <p>The item counterpart of {@code GameEntity}, and a snapshot for the same
 * reason: everything a module reads is copied out when it is built, on the game
 * thread, so there is nothing live to dereference later.
 *
 * <p>The one thing that cannot be a plain value is the picture. Drawing an item
 * needs the game's item renderer and the stack itself, so a private copy of the
 * stack is kept for {@link ItemIcons} &mdash; which is why that class lives in
 * this package. A module never sees it.
 */
@Getter
public final class GameItem {

    /** No item at all. Every field reads as zero or empty. */
    public static final GameItem EMPTY = new GameItem(ItemStack.EMPTY);

    /** The registry id, such as {@code minecraft:diamond_sword}, or empty for {@link #EMPTY}. */
    private final String id;

    /** The name as the player sees it, renamed or not. */
    private final String name;

    private final int count;

    /** Damage taken so far. Always 0 for an item that cannot take damage. */
    private final int damage;

    /** The damage it can take before breaking. 0 for an item that cannot take damage. */
    private final int maxDamage;

    /** Its enchantments, in the order the game lists them. Never null. */
    private final List<Enchant> enchantments;

    @Getter(lombok.AccessLevel.NONE)
    private final ItemStack stack;

    private GameItem(ItemStack stack) {
        this.stack = stack;
        boolean empty = stack.isEmpty();
        this.id = empty ? "" : Registries.ITEM.getId(stack.getItem()).toString();
        this.name = empty ? "" : stack.getName().getString();
        this.count = stack.getCount();
        this.damage = stack.isDamageable() ? stack.getDamage() : 0;
        this.maxDamage = stack.isDamageable() ? stack.getMaxDamage() : 0;
        this.enchantments = enchantmentsOf(stack);
    }

    /** @return a snapshot of the stack; a copy is taken, so later changes to it are not seen */
    static GameItem of(ItemStack stack) {
        return stack == null || stack.isEmpty() ? EMPTY : new GameItem(stack.copy());
    }

    private static List<Enchant> enchantmentsOf(ItemStack stack) {
        if (!stack.hasEnchantments()) {
            return Collections.emptyList();
        }
        List<Enchant> found = new ArrayList<>();
        for (Object2IntMap.Entry<RegistryEntry<Enchantment>> entry : stack.getEnchantments().getEnchantmentEntries()) {
            RegistryEntry<Enchantment> enchantment = entry.getKey();
            found.add(new Enchant(
                    enchantment.value().description().getString(),
                    entry.getIntValue(),
                    enchantment.isIn(EnchantmentTags.CURSE)));
        }
        return Collections.unmodifiableList(found);
    }

    /** The stack to draw. Package-private on purpose; see the class comment. */
    ItemStack stack() {
        return stack;
    }

    public boolean isEmpty() {
        return count <= 0 || id.isEmpty();
    }

    /** @return whether this item wears out at all */
    public boolean isDamageable() {
        return maxDamage > 0;
    }

    /** @return how much use is left, 1 when new and 0 when about to break; 1 for anything that cannot wear out */
    public float getDurabilityFraction() {
        return maxDamage <= 0 ? 1f : Math.max(0f, (maxDamage - damage) / (float) maxDamage);
    }

    /** One enchantment on an item. */
    @Getter
    public static final class Enchant {

        /** The enchantment's name without its level, such as "Protection". */
        private final String name;

        private final int level;

        /** Whether it is a curse: Binding or Vanishing, in vanilla. */
        private final boolean curse;

        Enchant(String name, int level, boolean curse) {
            this.name = name;
            this.level = level;
            this.curse = curse;
        }
    }
}
