package dev.px.karatsuba.client.service;

import dev.px.core.event.EventBus;
import dev.px.core.event.Priority;
import dev.px.core.event.Stage;
import dev.px.core.event.Subscription;
import dev.px.core.event.impl.TickEvent;
import dev.px.core.event.impl.WorldEvent;
import dev.px.core.service.Service;
import dev.px.karatsuba.adapter.event.game.player.HotbarPickEvent;
import dev.px.karatsuba.adapter.game.item.Inventories;
import dev.px.karatsuba.adapter.game.player.Inputs;
import dev.px.karatsuba.adapter.game.player.Players;
import dev.px.karatsuba.client.service.claim.ClaimPriority;
import dev.px.karatsuba.client.service.claim.Claims;
import dev.px.karatsuba.client.service.inventory.InventoryClick;
import dev.px.karatsuba.client.service.inventory.InventoryProfile;
import lombok.Getter;

import java.util.function.Supplier;

/**
 * The one owner of the hotbar selection, the offhand and every inventory click:
 * what the player sees held, what the server believes is held, and every move
 * between slots.
 *
 * <h2>Four jobs, one owner</h2>
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
 *   <li><b>Inventory clicks</b> &mdash; {@link #click}: armour, refills and the
 *       cleaner each ask for the next click they want, and one a tick goes out,
 *       the highest priority first. The offhand's click counts as that tick's one.
 *       <pre>{@code
 * Services.inventory().click(this, InventoryClick.drop(slot), ClaimPriority.LOW);
 *       }</pre></li>
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
 *       reaches a hand only by an inventory click, which Grim flags (MultiActionsC)
 *       from a player it believes is sprinting, or whose last input packet held a
 *       movement key or jump. Every click here waits for a tick where neither was
 *       sent; sneaking is fine on a 1.21 client.</li>
 *   <li>Only whole-stack clicks (swap, shift-click, throw): nothing is ever left on
 *       the cursor, and no pick-up click shares a tick with a shift-click
 *       (PacketOrderA).</li>
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

    /** When the winning inventory click is sent, within {@link Stage#PRE}: after the action service. */
    private static final int CLICK_PRIORITY = Priority.HIGH;

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

    private final Claims<InventoryClick> clicks = new Claims<>();

    private InventoryProfile profile = new InventoryProfile() { };

    /** Counts ticks, advanced at the end of each, so the whole of a tick's start reads one value. */
    private long tick;

    /** The tick of the last inventory click, from here or the offhand. */
    private long lastClickTick = Long.MIN_VALUE / 2;

    /**
     * The slot the player had before visible claims took over, or
     * {@link Inventories#NOT_FOUND} when nothing is claimed. Doubles as the "are
     * we holding the hotbar" flag.
     */
    @Getter
    private int playerSlot = Inventories.NOT_FOUND;

    private Subscription tickStart;
    private Subscription tickEnd;
    private Subscription clickStart;
    private Subscription worldSubscription;
    private Subscription pickSubscription;

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
                tick++;
            }
        });
        // At the start of the tick, where a player's own clicks land, and after the
        // action service (HIGHEST), so an offhand click it made this tick takes the
        // tick's one click.
        clickStart = bus.on(TickEvent.class, CLICK_PRIORITY, event -> {
            if (event.getStage() == Stage.PRE) {
                runClick();
            }
        });
        // A claim cannot survive a world change: the slot it names may not hold
        // what it did, and the baseline certainly does not.
        worldSubscription = bus.on(WorldEvent.class, event -> reset());
        // The player picking a slot while the hotbar is claimed: that pick, not the
        // slot from before the claim, is what they get back when it ends.
        pickSubscription = bus.on(HotbarPickEvent.class, event -> {
            if (playerSlot != Inventories.NOT_FOUND) {
                playerSlot = event.getSlot();
            }
        });
    }

    @Override
    public void stop() {
        for (Subscription subscription : new Subscription[]{tickStart, tickEnd, clickStart, worldSubscription,
                pickSubscription}) {
            if (subscription != null) {
                subscription.close();
            }
        }
        tickStart = tickEnd = clickStart = worldSubscription = pickSubscription = null;
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
     * inventory, one inventory click &mdash; which, when {@code strict}, waits for a
     * tick where the server was told no movement key or jump and no sprint, with
     * nothing in use, since Grim flags a click from a player who is (MultiActionsC).
     *
     * <p>Whatever was in the offhand goes where the item came from.
     */
    public OffhandResult toOffhand(String itemId, boolean strict) {
        return toOffhand(itemId, strict, false);
    }

    /**
     * As {@link #toOffhand(String, boolean)}, and with {@code stopping}, a click held
     * back only by the player moving stands them still for one tick
     * ({@link Inputs#holdStill()}), so it goes out at the start of the next.
     */
    public OffhandResult toOffhand(String itemId, boolean strict, boolean stopping) {
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
        if (strict && (clickedThisTick() || Players.usingItem())) {
            return OffhandResult.WAITING;
        }
        if (strict && !standingStill()) {
            if (stopping) {
                Inputs.holdStill();
            }
            return OffhandResult.WAITING;
        }
        if (!Inventories.swapInventoryWithOffhand(stored)) {
            return OffhandResult.FAILED;
        }
        lastClickTick = tick;
        return OffhandResult.SENT;
    }

    // ---------------------------------------------------------- inventory clicks

    /**
     * Asks for one inventory click, at the start of the next tick.
     *
     * <p>One click goes out per tick at most, and only the highest-priority one
     * asked for. A module asks each tick for the next click it wants, worked out
     * from the inventory as it is now; once one is sent the module sees its result
     * straight away, because the game applies the click before the server
     * answers. A click not sent this tick (it lost, or the player is moving) is
     * simply asked for again. See {@link #isClickAllowed()} for when a click may go.
     */
    public void click(Object owner, InventoryClick click, int priority) {
        if (click == null || click.getSlot() < 0 || click.getSlot() >= Inventories.SLOT_COUNT) {
            return;
        }
        // Two ticks: asked for during this tick, sent at the start of the next.
        clicks.file(owner, click, priority, 2);
    }

    /** @return whether this module's click is the one that will go out next */
    public boolean isClickHeldBy(Object owner) {
        return clicks.isHeldBy(owner);
    }

    /** @return a name for whoever's click goes next, or null. For debug readouts. */
    public String getClickHolderName() {
        Claims.Claim<InventoryClick> winner = clicks.best();
        return winner == null ? null : winner.getOwnerName();
    }

    /**
     * @return whether an inventory click could go out this tick, ignoring the delay
     *
     * <p>For a module deciding whether there is any point planning one. Mirrors the
     * gate the click itself goes through: no other container open, nothing on the
     * cursor, and with the profile's checks on, a player the server believes is
     * standing still with nothing in use.
     */
    public boolean isClickAllowed() {
        return readyExceptMoving() && (!profile.checks() || standingStill());
    }

    public void setProfile(InventoryProfile profile) {
        this.profile = profile == null ? new InventoryProfile() { } : profile;
    }

    /**
     * Sends the winning click, if this tick allows one.
     *
     * <p>Grim 2.3.73 flags an inventory click (MultiActionsC) from a player it
     * believes is sprinting, or whose last input packet held forward, back, left,
     * right or jump. Both are what the server was told last tick, which is what
     * {@link #standingStill()} reads. A click marked {@link InventoryClick#stopping()}
     * that is held back only by that stands the player still for one tick, and goes
     * out at the start of the next.
     */
    private void runClick() {
        clicks.beginTick();
        Claims.Claim<InventoryClick> winner = clicks.best();
        if (winner == null || clickedThisTick() || tick - lastClickTick <= profile.inventoryDelay()) {
            return;
        }
        InventoryClick click = winner.getValue();
        if (!readyExceptMoving()) {
            return;
        }
        if (profile.checks() && !standingStill()) {
            if (click.isStopping()) {
                Inputs.holdStill();
            }
            return;
        }
        if (send(click)) {
            lastClickTick = tick;
        }
        // Sent or refused, it is done: the module asks again from what it sees next tick.
        clicks.release(winner.getOwner());
    }

    private static boolean send(InventoryClick click) {
        switch (click.getKind()) {
            case SWAP:
                return Inventories.clickSwap(click.getSlot(), click.getTarget());
            case QUICK_MOVE:
                return Inventories.clickQuickMove(click.getSlot());
            case DROP:
                return Inventories.clickThrow(click.getSlot());
            default:
                return false;
        }
    }

    /** @return whether everything but moving allows a click: the movement half is {@link #standingStill()} */
    private boolean readyExceptMoving() {
        if (!Players.present() || Players.inOtherContainer() || !Inventories.cursorEmpty()) {
            return false;
        }
        if (profile.inventoryOpenOnly() && !Players.inOwnInventory()) {
            return false;
        }
        return !profile.checks() || profile.multitask() || !Players.usingItem();
    }

    /** @return whether the server was last told the player is still: no movement keys, not sprinting */
    private static boolean standingStill() {
        return !Inputs.reportedMovement() && !Players.sprinting();
    }

    private boolean clickedThisTick() {
        return lastClickTick == tick;
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
        clicks.clear();
        playerSlot = Inventories.NOT_FOUND;
    }
}
