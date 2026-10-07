package dev.px.karatsuba.adapter.game.item;

import it.unimi.dsi.fastutil.objects.Object2IntMap;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.enchantment.Enchantment;
import net.minecraft.enchantment.Enchantments;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.entry.RegistryEntry;

/**
 * What experience can repair on the player right now.
 *
 * <p>Mending repairs a damaged item with Mending in an armour slot or a hand,
 * chosen by the game among those that qualify, from each experience orb picked
 * up. An item without Mending is never repaired by experience however long the
 * player throws, which is why "is everything at full durability" is the wrong
 * question to stop on: only these items count.
 *
 * <p>Durability is a fraction, 1 for undamaged. <b>Game thread only.</b>
 */
public final class Mending {

    private Mending() {
    }

    /**
     * @param hands whether held items count, as well as armour
     * @return the durability of the most worn item experience can repair, 0 to 1;
     *         1 when there is no damaged Mending item
     */
    public static float lowest(boolean hands) {
        ClientPlayerEntity player = MinecraftClient.getInstance().player;
        if (player == null) {
            return 1f;
        }
        float lowest = 1f;
        for (EquipmentSlot slot : EquipmentSlot.values()) {
            if (!counts(slot, hands)) {
                continue;
            }
            ItemStack stack = player.getEquippedStack(slot);
            if (repairable(stack)) {
                lowest = Math.min(lowest, 1f - (float) stack.getDamage() / stack.getMaxDamage());
            }
        }
        return lowest;
    }

    /** @return whether anything experience could repair is worn or, with {@code hands}, held */
    public static boolean any(boolean hands) {
        ClientPlayerEntity player = MinecraftClient.getInstance().player;
        if (player == null) {
            return false;
        }
        for (EquipmentSlot slot : EquipmentSlot.values()) {
            if (counts(slot, hands) && hasMending(player.getEquippedStack(slot))) {
                return true;
            }
        }
        return false;
    }

    private static boolean counts(EquipmentSlot slot, boolean hands) {
        return slot.getType() == EquipmentSlot.Type.HUMANOID_ARMOR
                || (hands && slot.getType() == EquipmentSlot.Type.HAND);
    }

    private static boolean repairable(ItemStack stack) {
        return stack.isDamageable() && stack.getMaxDamage() > 0 && stack.getDamage() > 0 && hasMending(stack);
    }

    private static boolean hasMending(ItemStack stack) {
        if (stack.isEmpty() || !stack.hasEnchantments()) {
            return false;
        }
        for (Object2IntMap.Entry<RegistryEntry<Enchantment>> entry : stack.getEnchantments().getEnchantmentEntries()) {
            if (entry.getKey().matchesKey(Enchantments.MENDING)) {
                return true;
            }
        }
        return false;
    }
}
