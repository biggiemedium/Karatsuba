package dev.px.karatsuba.adapter.game.item;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;

/**
 * What the player is wearing.
 *
 * <p>A read seam, and deliberately <em>only</em> a read seam. The obvious
 * instinct is an "armour manager" that also swaps pieces in, but equipping is a
 * contended write on the inventory and belongs with the other contended writes
 * rather than in the class that reports durability. Splitting them is what stops
 * this growing into a module with no name.
 *
 * <p>Items are named by registry id, durability comes back as a fraction, and
 * nothing here hands out an {@code ItemStack} &mdash; the same rule as
 * {@link Inventories}, for the same reason.
 *
 * <p><b>Game thread only.</b>
 */
public final class Armor {

    /** The four worn slots, head to feet. */
    public enum Piece {

        HELMET(EquipmentSlot.HEAD),
        CHESTPLATE(EquipmentSlot.CHEST),
        LEGGINGS(EquipmentSlot.LEGS),
        BOOTS(EquipmentSlot.FEET);

        private final EquipmentSlot slot;

        Piece(EquipmentSlot slot) {
            this.slot = slot;
        }
    }

    private Armor() {
    }

    /** @return the registry id of the piece, or an empty string if that slot is bare */
    public static String itemAt(Piece piece) {
        ItemStack stack = stackAt(piece);
        return stack == null || stack.isEmpty() ? "" : Registries.ITEM.getId(stack.getItem()).toString();
    }

    /** @return whether the slot is empty */
    public static boolean isEmpty(Piece piece) {
        ItemStack stack = stackAt(piece);
        return stack == null || stack.isEmpty();
    }

    /**
     * @return how much of the piece is left, 1 for pristine and 0 for about to break
     *
     * <p>A fraction rather than a damage value, because a damage value means
     * nothing without the maximum beside it and every caller would divide them
     * anyway. An empty slot and an undamageable item both read 1, which is the
     * useful answer for a bar: nothing to warn about.
     */
    public static float durabilityAt(Piece piece) {
        ItemStack stack = stackAt(piece);
        if (stack == null || stack.isEmpty() || !stack.isDamageable() || stack.getMaxDamage() <= 0) {
            return 1f;
        }
        return 1f - (float) stack.getDamage() / stack.getMaxDamage();
    }

    /** @return the lowest durability across all four pieces, for a single warning */
    public static float lowestDurability() {
        float lowest = 1f;
        for (Piece piece : Piece.values()) {
            if (!isEmpty(piece)) {
                lowest = Math.min(lowest, durabilityAt(piece));
            }
        }
        return lowest;
    }

    /** @return how many armour points are worn, 0 to 20, as the vanilla bar shows */
    public static int protection() {
        ClientPlayerEntity player = MinecraftClient.getInstance().player;
        return player == null ? 0 : player.getArmor();
    }

    /** @return how many of the four slots are filled */
    public static int wornCount() {
        int worn = 0;
        for (Piece piece : Piece.values()) {
            if (!isEmpty(piece)) {
                worn++;
            }
        }
        return worn;
    }

    private static ItemStack stackAt(Piece piece) {
        ClientPlayerEntity player = MinecraftClient.getInstance().player;
        return player == null ? null : player.getEquippedStack(piece.slot);
    }
}
