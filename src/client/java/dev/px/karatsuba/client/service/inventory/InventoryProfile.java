package dev.px.karatsuba.client.service.inventory;

/**
 * How careful {@code InventoryService} is with inventory clicks, for the server
 * being played on.
 *
 * <p>The Interaction section of Client Settings supplies it; until that is
 * registered these defaults, which are Grim's, apply. Read every tick.
 */
public interface InventoryProfile {

    /**
     * Whether clicks wait until a player could have made them: standing still, not
     * sprinting, nothing in use. Off, a click goes out whenever it wins.
     */
    default boolean checks() {
        return true;
    }

    /** Ticks at least between two inventory clicks; 0 allows one every tick. */
    default int inventoryDelay() {
        return 1;
    }

    /**
     * Whether to click only while the player's own inventory screen is open. Grim
     * does not ask; some anticheats flag a click with no inventory open.
     */
    default boolean inventoryOpenOnly() {
        return false;
    }

    /** Whether to click while an item is in use: eating, drinking, blocking. */
    default boolean multitask() {
        return false;
    }
}
