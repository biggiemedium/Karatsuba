package dev.px.karatsuba.client.service.inventory;

import dev.px.karatsuba.adapter.game.item.Inventories;
import lombok.Getter;

/**
 * One click a module wants made on the player's own inventory.
 *
 * <p>A value, not an action: the module says which slot and what kind of click,
 * and {@code InventoryService} decides whether this tick is one where a player
 * could have made it. Slots use {@link Inventories}' numbering: 0-8 the hotbar,
 * 9-35 the rest, 36-39 armour from the feet up, 40 the offhand.
 *
 * <pre>{@code
 * Services.inventory().click(this, InventoryClick.quickMove(slot), ClaimPriority.NORMAL);
 * Services.inventory().click(this, InventoryClick.drop(slot).stopping(), ClaimPriority.LOW);
 * }</pre>
 *
 * <p>Only clicks that move whole stacks with nothing left on the cursor. A pick-up
 * click leaves an item on the cursor between clicks, and Grim flags a pick-up in
 * the same tick as a shift-click (PacketOrderA).
 */
@Getter
public final class InventoryClick {

    public enum Kind {
        /** Swap with a hotbar slot or the offhand: a number key, or F, over the slot. */
        SWAP,
        /** Shift-click: armour on or off, hotbar to the rest and back. */
        QUICK_MOVE,
        /** Throw the whole stack out: Ctrl+Q over the slot. */
        DROP
    }

    private final Kind kind;
    private final int slot;

    /** For {@link Kind#SWAP}: the hotbar slot or {@link Inventories#OFFHAND}. Otherwise unused. */
    private final int target;

    /** Whether to stand the player still for a tick when moving is all that stops the click. */
    private final boolean stopping;

    private InventoryClick(Kind kind, int slot, int target, boolean stopping) {
        this.kind = kind;
        this.slot = slot;
        this.target = target;
        this.stopping = stopping;
    }

    /** Swaps {@code slot} with hotbar slot {@code hotbar} (0-8) or {@link Inventories#OFFHAND}. */
    public static InventoryClick swap(int slot, int hotbar) {
        return new InventoryClick(Kind.SWAP, slot, hotbar, false);
    }

    public static InventoryClick quickMove(int slot) {
        return new InventoryClick(Kind.QUICK_MOVE, slot, 0, false);
    }

    public static InventoryClick drop(int slot) {
        return new InventoryClick(Kind.DROP, slot, 0, false);
    }

    /**
     * Lets the service stand the player still for one tick to make this click,
     * rather than wait for the player to stop on their own.
     *
     * <p>For a click worth a one-tick stutter: armour in a fight, not a clean-up.
     */
    public InventoryClick stopping() {
        return new InventoryClick(kind, slot, target, true);
    }

    @Override
    public String toString() {
        return kind == Kind.SWAP ? "swap " + slot + "->" + target : kind.name().toLowerCase() + " " + slot;
    }
}
