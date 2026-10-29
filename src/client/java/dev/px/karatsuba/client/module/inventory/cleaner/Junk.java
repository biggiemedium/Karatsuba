package dev.px.karatsuba.client.module.inventory.cleaner;

import dev.px.karatsuba.adapter.game.item.GameItem;

import java.util.function.Predicate;

/**
 * What Inventory Cleaner would throw out, for anything that must not undo it.
 *
 * <p>Hotbar Replenish asks before bringing an item to the hotbar, so it never
 * stocks a slot with junk for the cleaner to throw straight back out. The cleaner
 * publishes its test while it is on ({@link #use}) and withdraws it when it goes
 * off ({@link #clear}); with no cleaner on, nothing is junk.
 *
 * <p>Game thread only.
 */
public final class Junk {

    private static final Predicate<GameItem> NOTHING = item -> false;

    private static Predicate<GameItem> test = NOTHING;

    private Junk() {
    }

    /** Publishes the cleaner's test, until {@link #clear}. */
    public static void use(Predicate<GameItem> junk) {
        test = junk;
    }

    /** Withdraws it: nothing is junk. */
    public static void clear() {
        test = NOTHING;
    }

    /** @return whether the cleaner, if on, would throw this item out */
    public static boolean is(GameItem item) {
        return !item.isEmpty() && test.test(item);
    }
}
