package dev.px.karatsuba.adapter.game.item;

import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.network.ClientPlayerInteractionManager;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.AttributeModifiersComponent;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.util.Identifier;
import net.minecraft.util.Hand;

/**
 * The player's inventory, as questions with Core-typed answers.
 *
 * <p>Items are named by their registry id &mdash; {@code minecraft:ender_pearl}
 * &mdash; rather than handed over as {@code ItemStack}s. That is the whole trick
 * that keeps this usable from a module: a string is version-proof, it is what a
 * setting would store anyway, and it is what the player sees in F3.
 *
 * <p>Slot numbers are the hotbar's, 0 to 8, left to right. Anything beyond the
 * hotbar is deliberately absent: reaching into the main inventory means clicking
 * slots, which means a screen handler and a sync id, and that is a bigger seam
 * than anything here needs yet.
 *
 * <p><b>Game thread only.</b>
 */
public final class Inventories {

    /** Slots 0..8. The game's constant, named here so a module does not hard-code 9. */
    public static final int HOTBAR_SIZE = PlayerInventory.getHotbarSize();

    /** What {@link #findInHotbar} returns when nothing matches. */
    public static final int NOT_FOUND = -1;

    private Inventories() {
    }

    // -------------------------------------------------------------- reading

    /** @return the selected hotbar slot, 0 to 8, or {@link #NOT_FOUND} in a menu */
    public static int selectedSlot() {
        PlayerInventory inventory = inventory();
        return inventory == null ? NOT_FOUND : inventory.selectedSlot;
    }

    /** @return the registry id of the item in the main hand, or an empty string */
    public static String heldItem() {
        PlayerInventory inventory = inventory();
        return inventory == null ? "" : idOf(inventory.getMainHandStack());
    }

    /** @return the registry id of the item in a hotbar slot, or an empty string */
    public static String itemAt(int slot) {
        PlayerInventory inventory = inventory();
        if (inventory == null || slot < 0 || slot >= HOTBAR_SIZE) {
            return "";
        }
        return idOf(inventory.getStack(slot));
    }

    /**
     * @param itemId a registry id, with or without the {@code minecraft:} namespace
     * @return the first hotbar slot holding it, or {@link #NOT_FOUND}
     *
     * <p>The namespace is optional because nobody types it: {@code ender_pearl}
     * and {@code minecraft:ender_pearl} both match, and a modded id with its own
     * namespace still matches in full.
     */
    public static int findInHotbar(String itemId) {
        PlayerInventory inventory = inventory();
        if (inventory == null || itemId == null || itemId.isEmpty()) {
            return NOT_FOUND;
        }
        for (int slot = 0; slot < HOTBAR_SIZE; slot++) {
            if (matches(idOf(inventory.getStack(slot)), itemId)) {
                return slot;
            }
        }
        return NOT_FOUND;
    }

    /** @return whether the hotbar holds this item anywhere */
    public static boolean hotbarHas(String itemId) {
        return findInHotbar(itemId) != NOT_FOUND;
    }

    /**
     * @return the hotbar slot that hits hardest, or {@link #NOT_FOUND}
     *
     * <p>Scored on the item's own attack-damage modifier rather than on its
     * class, so a sword beats an axe or does not depending on the numbers, an
     * enchanted or modded weapon is ranked on what it actually does, and nothing
     * here needs a list of which items count as weapons.
     *
     * <p>An empty hotbar, or one holding only things with no attack modifier at
     * all, returns {@link #NOT_FOUND} rather than slot 0: "nothing here is a
     * weapon" is a real answer and a caller should not be handed a shovel.
     */
    public static int bestWeaponSlot() {
        PlayerInventory inventory = inventory();
        if (inventory == null) {
            return NOT_FOUND;
        }
        int best = NOT_FOUND;
        double bestDamage = 0d;

        for (int slot = 0; slot < HOTBAR_SIZE; slot++) {
            double damage = attackDamageOf(inventory.getStack(slot));
            if (damage > bestDamage) {
                bestDamage = damage;
                best = slot;
            }
        }
        return best;
    }

    /**
     * @param blockId a block registry id, as {@code Targets.Block} reports it
     * @return the hotbar slot that mines it fastest, or {@link #NOT_FOUND}
     *
     * <p>Asked per block rather than per tool because the answer depends on both:
     * a pickaxe is useless on leaves and shears are useless on stone. The speed
     * multiplier comes from the game, so enchantments and modded tools are
     * accounted for without this knowing they exist.
     *
     * <p>{@link #NOT_FOUND} when nothing in the hotbar is faster than a bare
     * hand, which is the case that should leave the player's own slot alone.
     */
    public static int bestToolSlotFor(String blockId) {
        PlayerInventory inventory = inventory();
        ClientWorld world = MinecraftClient.getInstance().world;
        if (inventory == null || world == null || blockId == null || blockId.isEmpty()) {
            return NOT_FOUND;
        }
        Block block = Registries.BLOCK.get(Identifier.tryParse(blockId));
        if (block == null) {
            return NOT_FOUND;
        }
        BlockState state = block.getDefaultState();

        int best = NOT_FOUND;
        // Strictly greater than a bare hand, which is 1.0 for every block.
        float bestSpeed = 1f;

        for (int slot = 0; slot < HOTBAR_SIZE; slot++) {
            ItemStack stack = inventory.getStack(slot);
            if (stack.isEmpty()) {
                continue;
            }
            float speed = stack.getMiningSpeedMultiplier(state);
            if (speed > bestSpeed) {
                bestSpeed = speed;
                best = slot;
            }
        }
        return best;
    }

    /**
     * @return the stack's main-hand attack damage modifier, or 0
     *
     * <p>Summed from the item's attribute modifiers for the hand it would be held
     * in, which is where the game itself gets the number it shows in a tooltip.
     */
    private static double attackDamageOf(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return 0d;
        }
        AttributeModifiersComponent modifiers = stack.get(DataComponentTypes.ATTRIBUTE_MODIFIERS);
        if (modifiers == null) {
            return 0d;
        }
        double[] damage = new double[1];
        modifiers.applyModifiers(EquipmentSlot.MAINHAND, (attribute, modifier) -> {
            if (attribute == EntityAttributes.ATTACK_DAMAGE) {
                damage[0] += modifier.value();
            }
        });
        return damage[0];
    }

    // -------------------------------------------------------------- acting

    /**
     * Selects a hotbar slot.
     *
     * <p>Tells the server too, which is what makes the following use land with
     * the right item rather than with whatever the server still thinks is held.
     */
    public static void selectSlot(int slot) {
        PlayerInventory inventory = inventory();
        if (inventory != null && slot >= 0 && slot < HOTBAR_SIZE) {
            inventory.setSelectedSlot(slot);
        }
    }

    /** Right-clicks with whatever is in the main hand. */
    public static boolean useHeld() {
        MinecraftClient client = MinecraftClient.getInstance();
        ClientPlayerEntity player = client.player;
        ClientPlayerInteractionManager interactions = client.interactionManager;
        if (player == null || interactions == null) {
            return false;
        }
        interactions.interactItem(player, Hand.MAIN_HAND);
        return true;
    }

    /**
     * Uses an item from the hotbar without leaving it selected.
     *
     * <p>Selects the slot, uses it, and selects the previous one back. The swap
     * is what a module wants nine times out of ten &mdash; throw a pearl, keep
     * holding the sword &mdash; and getting it wrong leaves the player holding
     * the wrong thing mid-fight.
     *
     * @param itemId a registry id, namespace optional
     * @return whether the item was found and used
     */
    public static boolean useFromHotbar(String itemId) {
        int slot = findInHotbar(itemId);
        if (slot == NOT_FOUND) {
            return false;
        }
        int previous = selectedSlot();
        selectSlot(slot);
        boolean used = useHeld();
        if (previous != NOT_FOUND) {
            selectSlot(previous);
        }
        return used;
    }

    /**
     * @return the sync id of the player's own inventory screen
     *
     * <p>Not useful on its own; it is what tells a container close apart from the
     * player's own, and it is why {@code ScreenCloseEvent} can say which it was.
     */
    public static int playerScreenSyncId() {
        ClientPlayerEntity player = MinecraftClient.getInstance().player;
        return player == null ? NOT_FOUND : player.playerScreenHandler.syncId;
    }

    // ------------------------------------------------------------- internal

    private static PlayerInventory inventory() {
        ClientPlayerEntity player = MinecraftClient.getInstance().player;
        return player == null ? null : player.getInventory();
    }

    private static String idOf(ItemStack stack) {
        return stack == null || stack.isEmpty() ? "" : Registries.ITEM.getId(stack.getItem()).toString();
    }

    /** Exact match, or a match on the path once the default namespace is dropped. */
    private static boolean matches(String actual, String wanted) {
        if (actual.isEmpty()) {
            return false;
        }
        return actual.equals(wanted) || actual.equals("minecraft:" + wanted);
    }
}
