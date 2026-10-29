package dev.px.karatsuba.client.module.inventory.replenish;

import dev.px.karatsuba.adapter.game.item.GameItem;
import dev.px.karatsuba.adapter.game.item.Inventories;
import dev.px.karatsuba.client.service.inventory.InventoryClick;

import java.util.function.Predicate;

/**
 * Keeps the hotbar stocked from the rest of the inventory, with no kit to set up:
 * whatever is in the hotbar is what the player wants there.
 *
 * <ul>
 *   <li><b>Topping up:</b> a stack that drops below a share of its size gets the
 *       rest of the inventory's matching stack shift-clicked onto it, but only a
 *       stack that fits whole, so nothing spills into an empty hotbar slot.</li>
 *   <li><b>Running out:</b> a slot whose item was used up (placed, eaten, thrown,
 *       broken) gets another one of it, into that same slot. Only used up: an
 *       item the player moved elsewhere is still carried, so it is not refilled,
 *       and rearranging the hotbar by hand works as it always did.</li>
 * </ul>
 *
 * <p>"Another one" is {@link Inventories#sameItem}: the same potion, not any
 * potion; any sword of the same kind, however worn.
 */
public final class RefillPlanner {

    /** How long a used-up slot waits for a refill before it is forgotten, in ticks. */
    private static final int PENDING_TICKS = 100;

    private final GameItem[] last = new GameItem[Inventories.HOTBAR_SIZE];
    private final int[] lastTotal = new int[Inventories.HOTBAR_SIZE];

    /** What each used-up slot is waiting for, or null. */
    private final GameItem[] pending = new GameItem[Inventories.HOTBAR_SIZE];
    private final int[] pendingAge = new int[Inventories.HOTBAR_SIZE];

    /**
     * Notices slots that were used up since the last tick. Call once a tick,
     * whether or not a click is wanted, so nothing is missed.
     */
    public void observe() {
        for (int slot = 0; slot < Inventories.HOTBAR_SIZE; slot++) {
            GameItem now = Inventories.stackAt(slot);
            GameItem before = last[slot];
            if (now.isEmpty() && before != null && !before.isEmpty()
                    && Inventories.count(before.getId()) < lastTotal[slot]) {
                pending[slot] = before;
                pendingAge[slot] = 0;
            }
            if (!now.isEmpty() || (pending[slot] != null && ++pendingAge[slot] > PENDING_TICKS)) {
                pending[slot] = null;
            }
            last[slot] = now;
            lastTotal[slot] = now.isEmpty() ? 0 : Inventories.count(now.getId());
        }
    }

    /** Forgets everything, for a world change or the module turning off. */
    public void reset() {
        for (int slot = 0; slot < Inventories.HOTBAR_SIZE; slot++) {
            last[slot] = null;
            pending[slot] = null;
            lastTotal[slot] = 0;
        }
    }

    /**
     * @param below    top up a stack below this share of its size, 0 to 1
     * @param emptied  whether to refill slots that were used up
     * @param excluded items never brought to the hotbar: the cleaner's junk
     * @return the next click, or null
     */
    public InventoryClick next(float below, boolean emptied, Predicate<GameItem> excluded) {
        if (emptied) {
            for (int slot = 0; slot < Inventories.HOTBAR_SIZE; slot++) {
                GameItem wanted = pending[slot];
                if (wanted == null || excluded.test(wanted)) {
                    continue;
                }
                int source = largestMatching(wanted, Integer.MAX_VALUE);
                if (source != Inventories.NOT_FOUND) {
                    return InventoryClick.swap(source, slot);
                }
            }
        }
        for (int slot = 0; slot < Inventories.HOTBAR_SIZE; slot++) {
            GameItem held = Inventories.stackAt(slot);
            if (held.isEmpty() || held.getMaxCount() <= 1 || excluded.test(held)
                    || held.getCount() >= held.getMaxCount() * below) {
                continue;
            }
            int source = largestMatching(held, held.getMaxCount() - held.getCount());
            if (source != Inventories.NOT_FOUND) {
                return InventoryClick.quickMove(source);
            }
        }
        return null;
    }

    /** @return the biggest matching stack outside the hotbar of at most {@code room} items, or NOT_FOUND */
    private static int largestMatching(GameItem item, int room) {
        int best = Inventories.NOT_FOUND;
        int bestCount = 0;
        for (int slot = Inventories.MAIN_START; slot < Inventories.ARMOR_START; slot++) {
            if (!Inventories.sameItem(item, slot)) {
                continue;
            }
            int count = Inventories.stackAt(slot).getCount();
            if (count <= room && count > bestCount) {
                best = slot;
                bestCount = count;
            }
        }
        return best;
    }
}
