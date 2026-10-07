package dev.px.karatsuba.client.service;

import dev.px.core.event.EventBus;
import dev.px.core.event.Priority;
import dev.px.core.event.Stage;
import dev.px.core.event.Subscription;
import dev.px.core.event.impl.TickEvent;
import dev.px.core.event.impl.WorldEvent;
import dev.px.core.service.Service;
import dev.px.karatsuba.adapter.game.item.Inventories;
import dev.px.karatsuba.adapter.game.player.Inputs;
import dev.px.karatsuba.adapter.game.player.Players;
import dev.px.karatsuba.client.service.claim.ClaimPriority;
import dev.px.karatsuba.client.service.claim.Claims;
import lombok.Getter;

import java.util.function.Supplier;

/**
 * The one owner of the hotbar selection and the offhand: what the player sees
 * held, what the server believes is held, and every swap between them.
 *
 * <h2>Three jobs, one owner</h2>
 * <ul>
 *   <li><b>Visible claims</b> &mdash; a module asks for a hotbar slot and the
 *       winner is selected, the hotbar following, until nobody asks any more and
 *       the player's own slot is put back:
 *       <pre>{@code
 * Services.inventory().request(this, sword, ClaimPriority.HIGH);
 *       }</pre></li>
 *   <li><b>Silent swaps</b> &mdash; {@link #silently}: the server is told another
 *       slot, the action runs, and the slot is put back and told to the server
 *       again, all in the same call. The hotbar never moves.</li>
 *   <li><b>The offhand</b> &mdash; {@link #toOffhand}: an item put in the
 *       offhand by the swap-hands key from the hotbar, or one inventory click from
 *       the main inventory.</li>
 * </ul>
 * It also answers what the <em>server</em> believes the player holds
 * ({@link #getServerSlot}, {@link #isServerHolding}), which differs from the
 * hotbar during a silent swap.
 *
 * <h2>Kept to what Grim accepts</h2>
 * Checked against Grim 2.3.73's own checks:
 * <ul>
 *   <li>Every selection is told to the server through the game's own sync, which
 *       sends nothing when the server already has that slot. Grim flags a slot
 *       sent twice in a row (BadPacketsA).</li>
 *   <li>A silent swap lasts one action and is put back in the same call; there
 *       is no silent hold across ticks. Grim tracks item use, and the slowdown
 *       that comes with it, from the use-item and slot packets, so a server-side
 *       item the client does not know it is using is a Simulation failure.</li>
 *   <li>Silent swaps come only from the hotbar. An item in the main inventory
 *       reaches a hand only by an inventory click, which Grim flags while moving
 *       (MultiActionsC); {@link #toOffhand} makes that click only when allowed to
 *       and the player stands still.</li>
 *   <li>The server's own slot corrections are never dropped. Hiding one is how a
 *       real inventory desync starts.</li>
 * </ul>
 *
 * <h2>Where the visible selection applies</h2>
 * Once per tick, at {@link Stage#POST} &mdash; after the player's update, so a
 * module that decided during the tick is in time. The game tells the server with
 * the next attack, use or tick.
 */
public final class InventoryService implements Service {

    /** When the winning visible slot is selected, within {@link Stage#POST}. */
    private static final int APPLY_PRIORITY = Priority.HIGH;

    /** What {@link #toOffhand} did. */
    public enum OffhandResult {
        /** The item was already in the offhand; nothing was sent. */
        ALREADY,
        /** Sent: from the hotbar by the swap-hands key, or one inventory click. */
        SENT,
        /** None of the item anywhere in the inventory. */
        NO_ITEM,
        /** Only in the main inventory, and the player is moving or busy: waiting to stand still. */
        WAITING,
        /** A container is open. */
        BUSY,
        /** The game refused. */
        FAILED
    }

    private final EventBus bus;

    private final Claims<Integer> claims = new Claims<>();

    /**
     * The slot the player had before visible claims took over, or
     * {@link Inventories#NOT_FOUND} when nothing is claimed. Doubles as the "are
     * we holding the hotbar" flag.
     */
    @Getter
    private int playerSlot = Inventories.NOT_FOUND;

    private Subscription tickStart;
    private Subscription tickEnd;
    private Subscription worldSubscription;

    public InventoryService(EventBus bus) {
        this.bus = bus;
    }

    @Override
    public String getName() {
        return "Inventory";
    }

    @Override
    public void start() {
        // HIGHEST: modules are on the bus before any service starts, so anything
        // lower would let a claim be filed before last tick's had expired.
        tickStart = bus.on(TickEvent.class, Priority.HIGHEST, event -> {
            if (event.getStage() == Stage.PRE) {
                claims.beginTick();
            }
        });
        tickEnd = bus.on(TickEvent.class, APPLY_PRIORITY, event -> {
            if (event.getStage() == Stage.POST) {
                apply();
            }
        });
        // A claim cannot survive a world change: the slot it names may not hold
        // what it did, and the baseline certainly does not.
        worldSubscription = bus.on(WorldEvent.class, event -> reset());
    }

    @Override
    public void stop() {
        for (Subscription subscription : new Subscription[]{tickStart, tickEnd, worldSubscription}) {
            if (subscription != null) {
                subscription.close();
            }
        }
        tickStart = tickEnd = worldSubscription = null;
        reset();
    }

    // -------------------------------------------------------- visible claims

    /** Asks for a hotbar slot, 0 to 8, at {@link ClaimPriority#NORMAL}. */
    public void request(Object owner, int slot) {
        request(owner, slot, ClaimPriority.NORMAL);
    }

    /** Asks for a hotbar slot, 0 to 8, for this tick. Call again next tick to keep it. */
    public void request(Object owner, int slot, int priority) {
        request(owner, slot, priority, 1);
    }

    /**
     * Asks for a hotbar slot.
     *
     * <p>Out-of-range slots are dropped rather than clamped: a module asking for
     * slot 12 has a bug, and silently giving it slot 8 hides it.
     *
     * @param holdTicks ticks the claim survives unrenewed; 1 for anything asking every tick
     */
    public void request(Object owner, int slot, int priority, int holdTicks) {
        if (slot < 0 || slot >= Inventories.HOTBAR_SIZE) {
            return;
        }
        claims.file(owner, slot, priority, holdTicks);
    }

    /** Lets go early. Never required &mdash; a claim expires on its own. */
    public boolean release(Object owner) {
        return claims.release(owner);
    }

    /** @return whether anything currently owns the hotbar */
    public boolean isActive() {
        return playerSlot != Inventories.NOT_FOUND;
    }

    public boolean isHeldBy(Object owner) {
        return claims.isHeldBy(owner);
    }

    /** @return the slot visible claims will select, or {@link Inventories#NOT_FOUND} */
    public int getResolvedSlot() {
        Claims.Claim<Integer> winner = claims.best();
        return winner == null ? Inventories.NOT_FOUND : winner.getValue();
    }

    /** @return a name for whoever holds the hotbar, or null. For notifications and debug readouts. */
    public String getHolderName() {
        Claims.Claim<Integer> winner = claims.best();
        return winner == null ? null : winner.getOwnerName();
    }

    // ---------------------------------------------------------- server's view

    /** @return the hotbar slot the server believes is selected, or {@link Inventories#NOT_FOUND} */
    public int getServerSlot() {
        return Inventories.serverSlot();
    }

    /** @return whether the server believes this item is in the main hand or the offhand */
    public boolean isServerHolding(String itemId) {
        return Inventories.serverHolding(itemId) || Inventories.offhandHolds(itemId);
    }

    // ------------------------------------------------------------ silent swaps

    /**
     * Runs {@code action} with a hotbar slot selected on the server, and puts the
     * player's slot back &mdash; selected and told to the server &mdash; before
     * returning. The hotbar never visibly moves.
     *
     * <p>Two slot packets at most, never the same slot twice running, and none at
     * all when the slot is already selected. The action should be one the game
     * sends at once (an attack, a click, a use); nothing is held across ticks.
     *
     * @param slot a hotbar slot, or {@link Inventories#NOT_FOUND} to run as is
     */
    public <T> T silently(int slot, Supplier<T> action) {
        int previous = Inventories.selectedSlot();
        if (slot == Inventories.NOT_FOUND || slot == previous || previous == Inventories.NOT_FOUND) {
            return action.get();
        }
        Inventories.selectSlotNow(slot);
        try {
            return action.get();
        } finally {
            Inventories.selectSlotNow(previous);
        }
    }

    // ---------------------------------------------------------------- offhand

    /**
     * Puts an item in the offhand.
     *
     * <p>From the hotbar, the swap-hands key: no inventory click. From the main
     * inventory, one inventory click &mdash; which, when {@code strict}, waits until
     * the player stands still with no key held, not sprinting, sneaking or using
     * an item, since a player with the inventory open can do none of those and
     * Grim flags a click from one who is (MultiActionsC).
     *
     * <p>Whatever was in the offhand goes where the item came from.
     */
    public OffhandResult toOffhand(String itemId, boolean strict) {
        if (Inventories.offhandHolds(itemId)) {
            return OffhandResult.ALREADY;
        }
        if (strict && Players.inContainer()) {
            return OffhandResult.BUSY;
        }
        int hotbar = Inventories.findInHotbar(itemId);
        if (hotbar != Inventories.NOT_FOUND) {
            return Inventories.swapHotbarWithOffhand(hotbar) ? OffhandResult.SENT : OffhandResult.FAILED;
        }
        int stored = Inventories.findInInventory(itemId);
        if (stored == Inventories.NOT_FOUND) {
            return OffhandResult.NO_ITEM;
        }
        if (strict && (Inputs.anyMovement() || Players.sprinting() || Players.sneaking() || Players.usingItem())) {
            return OffhandResult.WAITING;
        }
        return Inventories.swapInventoryWithOffhand(stored) ? OffhandResult.SENT : OffhandResult.FAILED;
    }

    // -------------------------------------------------------------- internals

    /**
     * Selects the winning visible slot, or hands the hotbar back.
     *
     * <p>Handing back is the half that is easy to forget and the half a player
     * notices. It happens exactly once, on the tick the last claim expires.
     */
    private void apply() {
        Claims.Claim<Integer> winner = claims.best();
        if (winner == null) {
            if (playerSlot != Inventories.NOT_FOUND) {
                Inventories.selectSlot(playerSlot);
                playerSlot = Inventories.NOT_FOUND;
            }
            return;
        }
        if (playerSlot == Inventories.NOT_FOUND) {
            // First claim since going idle: whatever is selected now is the
            // player's, and is what they get back afterwards.
            playerSlot = Inventories.selectedSlot();
        }
        Inventories.selectSlot(winner.getValue());
    }

    /** Drops every claim without restoring, for when the world it referred to has gone. */
    private void reset() {
        claims.clear();
        playerSlot = Inventories.NOT_FOUND;
    }
}
