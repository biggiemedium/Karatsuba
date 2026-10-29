package dev.px.karatsuba.adapter.game.item;

import dev.px.karatsuba.adapter.mixin.player.ClientPlayerInteractionManagerInvoker;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.BlockWithEntity;
import net.minecraft.block.FallingBlock;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.network.ClientPlayerInteractionManager;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.AttributeModifiersComponent;
import net.minecraft.component.type.FoodComponent;
import net.minecraft.enchantment.Enchantment;
import net.minecraft.enchantment.Enchantments;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.network.packet.c2s.play.PlayerActionC2SPacket;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.item.BlockItem;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.util.Identifier;
import net.minecraft.util.Hand;

import java.util.Set;

/**
 * The player's inventory, as questions with Core-typed answers.
 *
 * <p>Items are named by their registry id &mdash; {@code minecraft:ender_pearl}
 * &mdash; rather than handed over as {@code ItemStack}s. That is the whole trick
 * that keeps this usable from a module: a string is version-proof, it is what a
 * setting would store anyway, and it is what the player sees in F3.
 *
 * <p>Slots are numbered as the game's own inventory numbers them: 0-8 the hotbar,
 * left to right, then the rest, the armour and the offhand (see
 * {@link #MAIN_START}). Reaching past the hotbar means an inventory click, and
 * the clicks here only send; when one is allowed is {@code InventoryService}'s
 * call, because Grim has rules about it.
 *
 * <p><b>Game thread only.</b>
 */
public final class Inventories {

    /** Slots 0..8. The game's constant, named here so a module does not hard-code 9. */
    public static final int HOTBAR_SIZE = PlayerInventory.getHotbarSize();

    /** What {@link #findInHotbar} returns when nothing matches. */
    public static final int NOT_FOUND = -1;

    // Every slot the player owns, numbered as the game's own inventory numbers
    // them: 0-8 the hotbar, 9-35 the rest, 36-39 armour from the feet up, 40 the
    // offhand. One numbering for every method that takes a slot, so a module never
    // learns the different one the inventory screen uses.

    /** First slot past the hotbar. */
    public static final int MAIN_START = HOTBAR_SIZE;

    /** First armour slot (boots); one past the main inventory. */
    public static final int ARMOR_START = PlayerInventory.MAIN_SIZE;

    /** The offhand. */
    public static final int OFFHAND = PlayerInventory.OFF_HAND_SLOT;

    /** One past the last slot. */
    public static final int SLOT_COUNT = OFFHAND + 1;

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

    /** @return whether the item in the main hand is this one; namespace optional, as for {@link #findInHotbar} */
    public static boolean holding(String itemId) {
        return itemId != null && matches(heldItem(), itemId);
    }

    /** @return whether the item in the offhand is this one; namespace optional, as for {@link #findInHotbar} */
    public static boolean offhandHolds(String itemId) {
        ClientPlayerEntity player = MinecraftClient.getInstance().player;
        return player != null && itemId != null && matches(idOf(player.getOffHandStack()), itemId);
    }

    /**
     * @return how many of this item the player carries: the whole inventory, the
     *         offhand and the armour slots; namespace optional, as for {@link #findInHotbar}
     */
    public static int count(String itemId) {
        PlayerInventory inventory = inventory();
        if (inventory == null || itemId == null) {
            return 0;
        }
        int total = 0;
        for (int slot = 0; slot < inventory.size(); slot++) {
            ItemStack stack = inventory.getStack(slot);
            if (matches(idOf(stack), itemId)) {
                total += stack.getCount();
            }
        }
        return total;
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
     * Foods never eaten for hunger: they poison, teleport, or are worth more in a
     * fight. Golden apples are eaten by name, when they are what is wanted.
     */
    private static final Set<String> NOT_FOR_HUNGER = Set.of(
            "minecraft:rotten_flesh", "minecraft:spider_eye", "minecraft:poisonous_potato",
            "minecraft:pufferfish", "minecraft:chorus_fruit", "minecraft:suspicious_stew",
            "minecraft:golden_apple", "minecraft:enchanted_golden_apple");

    /**
     * @return the hotbar slot of the best food to eat for hunger, or {@link #NOT_FOUND}
     *
     * <p>Ranked on what the game says the food restores, hunger plus saturation,
     * so modded and future foods rank themselves. Never anything that poisons or
     * teleports, and never a golden apple: those are for a fight.
     */
    public static int bestFoodSlot() {
        PlayerInventory inventory = inventory();
        if (inventory == null) {
            return NOT_FOUND;
        }
        int best = NOT_FOUND;
        float bestValue = 0f;
        for (int slot = 0; slot < HOTBAR_SIZE; slot++) {
            ItemStack stack = inventory.getStack(slot);
            FoodComponent food = stack.get(DataComponentTypes.FOOD);
            if (food == null || NOT_FOR_HUNGER.contains(Registries.ITEM.getId(stack.getItem()).toString())) {
                continue;
            }
            float value = food.nutrition() + food.saturation();
            if (value > bestValue) {
                bestValue = value;
                best = slot;
            }
        }
        return best;
    }

    /**
     * @return whether the item in this slot is cooling down, so using it does
     *         nothing: the client refuses, and Grim does not expect the slowdown
     */
    public static boolean onCooldown(int slot) {
        ClientPlayerEntity player = MinecraftClient.getInstance().player;
        if (player == null || slot < 0 || slot >= SLOT_COUNT) {
            return false;
        }
        ItemStack stack = player.getInventory().getStack(slot);
        return !stack.isEmpty() && player.getItemCooldownManager().isCoolingDown(stack);
    }

    /** @return whether this hotbar slot holds something edible, even with a full hunger bar */
    public static boolean alwaysEdible(int slot) {
        PlayerInventory inventory = inventory();
        if (inventory == null || slot < 0 || slot >= HOTBAR_SIZE) {
            return false;
        }
        FoodComponent food = inventory.getStack(slot).get(DataComponentTypes.FOOD);
        return food != null && food.canAlwaysEat();
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
     * @return the slot of the fastest item that <em>harvests</em> this block (gets
     *         its drops, as a pickaxe does an ender chest), with or without Silk
     *         Touch as asked: the hotbar first, then the main inventory (0 to 35);
     *         {@link #NOT_FOUND} when nothing carried does
     *
     * <p>Silk Touch decides what a block drops: an ender chest gives eight obsidian
     * without it and itself with it, so the choice is the caller's, not a speed.
     */
    public static int bestHarvestingTool(String blockId, boolean silkTouch) {
        PlayerInventory inventory = inventory();
        if (inventory == null || blockId == null || blockId.isEmpty()) {
            return NOT_FOUND;
        }
        Block block = Registries.BLOCK.get(Identifier.tryParse(blockId));
        if (block == null) {
            return NOT_FOUND;
        }
        BlockState state = block.getDefaultState();
        int best = NOT_FOUND;
        float bestSpeed = 0f;
        for (int slot = 0; slot < PlayerInventory.MAIN_SIZE; slot++) {
            ItemStack stack = inventory.getStack(slot);
            if (stack.isEmpty() || !stack.isSuitableFor(state) || hasSilkTouch(stack) != silkTouch) {
                continue;
            }
            // Strictly faster to move off the hotbar, so a tie keeps the one already there.
            float speed = stack.getMiningSpeedMultiplier(state);
            if (speed > bestSpeed) {
                bestSpeed = speed;
                best = slot;
            }
        }
        return best;
    }

    private static boolean hasSilkTouch(ItemStack stack) {
        for (RegistryEntry<Enchantment> enchantment : stack.getEnchantments().getEnchantments()) {
            if (enchantment.matchesKey(Enchantments.SILK_TOUCH)) {
                return true;
            }
        }
        return false;
    }

    /**
     * @return whether one more of this item would be picked up: an empty slot in the
     *         hotbar or main inventory, or a stack of it with room; namespace optional
     */
    public static boolean hasRoomFor(String itemId) {
        PlayerInventory inventory = inventory();
        if (inventory == null || itemId == null) {
            return false;
        }
        for (int slot = 0; slot < PlayerInventory.MAIN_SIZE; slot++) {
            ItemStack stack = inventory.getStack(slot);
            if (stack.isEmpty() || (matches(idOf(stack), itemId) && stack.getCount() < stack.getMaxCount())) {
                return true;
            }
        }
        return false;
    }

    /**
     * @return the registry id of the hotbar's best block to build with, or null
     *
     * <p>A full cube that stays where it is put: no sand or gravel, which falls
     * off a bridge, and no chests, shulkers or other block entities, which are
     * worth more than a floor. The held stack wins if it qualifies, so building
     * does not swap away from what the player chose; otherwise the biggest stack,
     * so the run lasts longest.
     */
    public static String bestBuildingBlock() {
        PlayerInventory inventory = inventory();
        ClientWorld world = MinecraftClient.getInstance().world;
        if (inventory == null || world == null) {
            return null;
        }
        ItemStack held = inventory.getMainHandStack();
        if (isBuildingBlock(held, world)) {
            return idOf(held);
        }
        ItemStack best = null;
        for (int slot = 0; slot < HOTBAR_SIZE; slot++) {
            ItemStack stack = inventory.getStack(slot);
            if (isBuildingBlock(stack, world) && (best == null || stack.getCount() > best.getCount())) {
                best = stack;
            }
        }
        return best == null ? null : idOf(best);
    }

    private static boolean isBuildingBlock(ItemStack stack, ClientWorld world) {
        if (stack.isEmpty() || !(stack.getItem() instanceof BlockItem item)) {
            return false;
        }
        Block block = item.getBlock();
        return !(block instanceof FallingBlock)
                && !(block instanceof BlockWithEntity)
                && block.getDefaultState().isFullCube(world, BlockPos.ORIGIN);
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

    // -------------------------------------------------------------- offhand

    /** @return the registry id of the item in the offhand, or an empty string */
    public static String offhandItem() {
        ClientPlayerEntity player = MinecraftClient.getInstance().player;
        return player == null ? "" : idOf(player.getOffHandStack());
    }

    /**
     * @return the main-inventory slot (9 to 35, not the hotbar) holding this item,
     *         or {@link #NOT_FOUND}; namespace optional
     */
    public static int findInInventory(String itemId) {
        PlayerInventory inventory = inventory();
        if (inventory == null || itemId == null) {
            return NOT_FOUND;
        }
        for (int slot = HOTBAR_SIZE; slot < PlayerInventory.MAIN_SIZE; slot++) {
            if (matches(idOf(inventory.getStack(slot)), itemId)) {
                return slot;
            }
        }
        return NOT_FOUND;
    }

    /**
     * Swaps a hotbar slot's item with the offhand, as pressing the swap-hands key
     * (F) with that slot selected does.
     *
     * <p>No inventory click: the slot is selected and told to the server, the
     * swap-hands action sent, and the slot the player had selected put back and
     * told to the server again. What was in the offhand ends up in that hotbar
     * slot. The server makes the swap and sends both slots back.
     *
     * @return whether it was sent
     */
    public static boolean swapHotbarWithOffhand(int slot) {
        ClientPlayerEntity player = MinecraftClient.getInstance().player;
        PlayerInventory inventory = inventory();
        if (player == null || inventory == null || player.isSpectator() || slot < 0 || slot >= HOTBAR_SIZE) {
            return false;
        }
        int previous = inventory.selectedSlot;
        selectSlotNow(slot);
        player.networkHandler.sendPacket(new PlayerActionC2SPacket(
                PlayerActionC2SPacket.Action.SWAP_ITEM_WITH_OFFHAND, BlockPos.ORIGIN, Direction.DOWN));
        selectSlotNow(previous);
        return true;
    }

    // --------------------------------------------------------- server's view

    /**
     * @return the hotbar slot the server believes is selected: the last one the
     *         game told it, or {@link #NOT_FOUND} out of a world
     *
     * <p>Differs from {@link #selectedSlot()} between a selection and the next
     * time the game sends it, which is when a silent swap holds an item the hotbar
     * does not show.
     */
    public static int serverSlot() {
        ClientPlayerInteractionManager interactions = MinecraftClient.getInstance().interactionManager;
        if (interactions == null || inventory() == null) {
            return NOT_FOUND;
        }
        return ((ClientPlayerInteractionManagerInvoker) interactions).karatsuba$getLastSelectedSlot();
    }

    /** @return the registry id of the item in the slot the server believes is selected, or an empty string */
    public static String serverHeldItem() {
        int slot = serverSlot();
        return slot == NOT_FOUND ? "" : itemAt(slot);
    }

    /** @return whether the server believes this item is in the main hand; namespace optional */
    public static boolean serverHolding(String itemId) {
        return itemId != null && matches(serverHeldItem(), itemId);
    }

    /**
     * Selects a hotbar slot and tells the server at once, through the game's own
     * sync &mdash; which sends nothing when the server already has that slot, so a
     * slot is never sent twice in a row (Grim flags that, BadPacketsA).
     */
    public static void selectSlotNow(int slot) {
        MinecraftClient client = MinecraftClient.getInstance();
        PlayerInventory inventory = inventory();
        if (inventory == null || client.interactionManager == null || slot < 0 || slot >= HOTBAR_SIZE) {
            return;
        }
        inventory.setSelectedSlot(slot);
        ((ClientPlayerInteractionManagerInvoker) client.interactionManager).karatsuba$syncSelectedSlot();
    }

    /**
     * Swaps a main-inventory slot's item with the offhand: one inventory click,
     * the one the game sends for pressing the swap-hands key over a slot in the
     * open inventory.
     *
     * <p>An inventory click, which a strict anticheat expects only from a player
     * standing still with nothing else in hand; deciding when that is, is the
     * caller's.
     *
     * @param slot 9 to 35
     * @return whether it was sent
     */
    public static boolean swapInventoryWithOffhand(int slot) {
        MinecraftClient client = MinecraftClient.getInstance();
        ClientPlayerEntity player = client.player;
        ClientPlayerInteractionManager interactions = client.interactionManager;
        if (player == null || interactions == null || slot < HOTBAR_SIZE || slot >= PlayerInventory.MAIN_SIZE) {
            return false;
        }
        // In the player's screen, main-inventory slots keep their index; the
        // offhand is button 40 of a SWAP click, as the swap-hands key sends.
        interactions.clickSlot(player.playerScreenHandler.syncId, slot, PlayerInventory.OFF_HAND_SLOT,
                SlotActionType.SWAP, player);
        return true;
    }

    // ----------------------------------------------------- any slot, any click

    /**
     * @param slot 0 to {@link #SLOT_COUNT}, as numbered above
     * @return a snapshot of what is in it, or {@link GameItem#EMPTY}
     */
    public static GameItem stackAt(int slot) {
        PlayerInventory inventory = inventory();
        if (inventory == null || slot < 0 || slot >= SLOT_COUNT) {
            return GameItem.EMPTY;
        }
        return GameItem.of(inventory.getStack(slot));
    }

    /**
     * @return whether a slot holds the same item as a snapshot: the same id, and
     *         for anything that does not wear out, the same enchantments, potion
     *         and name too
     *
     * <p>What "another one of these" means for a refill. Two potions share an id
     * and nothing else; two swords differ in damage taken, which should not stop
     * one standing in for the other.
     */
    public static boolean sameItem(GameItem snapshot, int slot) {
        PlayerInventory inventory = inventory();
        if (inventory == null || snapshot == null || snapshot.isEmpty() || slot < 0 || slot >= SLOT_COUNT) {
            return false;
        }
        ItemStack stack = inventory.getStack(slot);
        if (stack.isEmpty() || !idOf(stack).equals(snapshot.getId())) {
            return false;
        }
        return snapshot.isDamageable() || ItemStack.areItemsAndComponentsEqual(snapshot.stack(), stack);
    }

    /** @return whether nothing is held on the cursor, as when an inventory screen is open mid-drag */
    public static boolean cursorEmpty() {
        ClientPlayerEntity player = MinecraftClient.getInstance().player;
        return player == null || player.currentScreenHandler.getCursorStack().isEmpty();
    }

    /**
     * Swaps a slot with a hotbar slot or the offhand: what pressing a number key,
     * or the swap-hands key, over a slot in the open inventory sends.
     *
     * @param slot   any slot
     * @param target a hotbar slot 0 to 8, or {@link #OFFHAND}
     * @return whether it was sent
     */
    public static boolean clickSwap(int slot, int target) {
        if (target != OFFHAND && (target < 0 || target >= HOTBAR_SIZE)) {
            return false;
        }
        return click(slot, target, SlotActionType.SWAP);
    }

    /**
     * Shift-clicks a slot: armour to its slot or out of it, the hotbar to the rest
     * and back, merging into stacks of the same item first.
     *
     * @return whether it was sent
     */
    public static boolean clickQuickMove(int slot) {
        return click(slot, 0, SlotActionType.QUICK_MOVE);
    }

    /**
     * Throws a whole stack out of a slot: Ctrl+Q over it in the open inventory.
     *
     * @return whether it was sent
     */
    public static boolean clickThrow(int slot) {
        return click(slot, 1, SlotActionType.THROW);
    }

    /**
     * One click on the player's own inventory, as the open inventory screen sends
     * it. The game applies it at once and tells the server; the server corrects
     * anything it disagrees with.
     *
     * <p>Refused while another container is open: the click would name a window
     * the server is not showing.
     */
    private static boolean click(int slot, int button, SlotActionType type) {
        MinecraftClient client = MinecraftClient.getInstance();
        ClientPlayerEntity player = client.player;
        ClientPlayerInteractionManager interactions = client.interactionManager;
        int screenSlot = screenSlotOf(slot);
        if (player == null || interactions == null || screenSlot == NOT_FOUND || player.isSpectator()
                || player.currentScreenHandler != player.playerScreenHandler) {
            return false;
        }
        interactions.clickSlot(player.playerScreenHandler.syncId, screenSlot, button, type, player);
        return true;
    }

    /**
     * @return where a slot sits in the player's inventory screen, or {@link #NOT_FOUND}
     *
     * <p>The screen puts crafting first (0-4), then armour head to feet (5-8), the
     * main inventory as is (9-35), the hotbar after it (36-44) and the offhand last (45).
     */
    private static int screenSlotOf(int slot) {
        if (slot >= 0 && slot < HOTBAR_SIZE) {
            return slot + 36;
        }
        if (slot >= MAIN_START && slot < ARMOR_START) {
            return slot;
        }
        if (slot >= ARMOR_START && slot < OFFHAND) {
            return 8 - (slot - ARMOR_START);
        }
        return slot == OFFHAND ? 45 : NOT_FOUND;
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
