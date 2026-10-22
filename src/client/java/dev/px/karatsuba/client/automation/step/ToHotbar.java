package dev.px.karatsuba.client.automation.step;

import dev.px.core.flow.FlowContext;
import dev.px.core.flow.Status;
import dev.px.core.flow.Step;
import dev.px.core.flow.StopReason;
import dev.px.karatsuba.adapter.game.item.Inventories;
import dev.px.karatsuba.client.service.Services;
import dev.px.karatsuba.client.service.inventory.InventoryClick;

import java.util.function.IntSupplier;

/**
 * Brings an item from the main inventory into the hotbar, so the steps after it can
 * hold it: one swap into a hotbar slot that is empty, or else the last one that is
 * neither in hand nor holding what the steps need.
 *
 * <p>The click goes through {@code Services.inventory()}, which sends it only while
 * the server believes the player is standing still (Grim's MultiActionsC), so stand
 * still for it. Done at once when the item is already in the hotbar; fails when
 * there is none of it anywhere, or the swap never lands.
 */
public final class ToHotbar extends Step {

    /** Ticks for the swap to go out and show. */
    private static final int PATIENCE = 60;

    private final String label;
    private final IntSupplier slot;
    private final String[] keep;

    private int waited;

    /**
     * @param label what it brings, for its name and reasons
     * @param slot  where the item is now (0 to 35), or {@link Inventories#NOT_FOUND}
     * @param keep  item ids the borrowed hotbar slot must not hold, so one bring does
     *              not undo another
     */
    public ToHotbar(String label, IntSupplier slot, String... keep) {
        super("bring " + label + " to the hotbar");
        this.label = label;
        this.slot = slot;
        this.keep = keep;
    }

    @Override
    protected void start(FlowContext c) {
        waited = 0;
    }

    @Override
    protected Status tick(FlowContext c) {
        int from = slot.getAsInt();
        if (from == Inventories.NOT_FOUND) {
            return c.fail("no " + label + " to bring");
        }
        if (from < Inventories.HOTBAR_SIZE) {
            return Status.DONE;
        }
        if (++waited > PATIENCE) {
            return c.fail("the swap for " + label + " never went out (keep still)");
        }
        Services.inventory().click(this, InventoryClick.swap(from, borrowed()), c.priority());
        return Status.RUNNING;
    }

    @Override
    protected void stop(FlowContext c, StopReason why) {
        Services.inventory().release(this);
    }

    /** @return an empty hotbar slot, else the last one not in hand and holding nothing to keep */
    private int borrowed() {
        for (int hotbar = 0; hotbar < Inventories.HOTBAR_SIZE; hotbar++) {
            if (Inventories.stackAt(hotbar).isEmpty()) {
                return hotbar;
            }
        }
        for (int hotbar = Inventories.HOTBAR_SIZE - 1; hotbar >= 0; hotbar--) {
            if (hotbar != Inventories.selectedSlot() && !kept(Inventories.itemAt(hotbar))) {
                return hotbar;
            }
        }
        return Inventories.HOTBAR_SIZE - 1;
    }

    private boolean kept(String id) {
        for (String wanted : keep) {
            if (id.equals(wanted) || id.endsWith(wanted)) {
                return true;
            }
        }
        return false;
    }
}
