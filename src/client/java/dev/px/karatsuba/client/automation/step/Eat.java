package dev.px.karatsuba.client.automation.step;

import dev.px.core.Core;
import dev.px.core.control.Click;
import dev.px.core.flow.Control;
import dev.px.core.flow.FlowContext;
import dev.px.core.flow.Status;
import dev.px.core.flow.Step;
import dev.px.core.flow.StopReason;
import dev.px.karatsuba.adapter.game.item.Inventories;
import dev.px.karatsuba.adapter.game.player.PlayerList;
import dev.px.karatsuba.adapter.game.player.Players;
import dev.px.karatsuba.client.service.Services;
import dev.px.karatsuba.client.service.action.Action;
import dev.px.karatsuba.client.service.action.Outcome;
import dev.px.karatsuba.client.service.claim.ClaimPriority;

import java.util.function.BooleanSupplier;
import java.util.function.IntSupplier;

/**
 * Eats or drinks one item from the hotbar, start to finish, and finishes only
 * when the server says the item was eaten.
 *
 * <p>Done the way a player does it, because Grim tracks item use from the
 * packets: the item is switched to in the open, used, and held until the game
 * finishes it. A silent eat &mdash; swapped to for a packet and back &mdash; is
 * slowed on the server and not on the client, which is a Simulation flag.
 *
 * <ol>
 *   <li>The slot is asked of {@code Services.inventory()}, which selects it as
 *       the player's scroll wheel would. With {@link #silent}, the hotbar and the
 *       hand keep showing the player's own slot meanwhile; only the drawing
 *       changes, never the packets.</li>
 *   <li>Once the server holds it, the use goes out through
 *       {@code Services.actions()}: straight at the item, never at whatever block
 *       the crosshair is on, so a chest in view is not opened instead. An item
 *       cooling down is never used, and a use the client refuses is sent again
 *       at most {@link #MAX_USES} times.</li>
 *   <li>Once the game is using it, the use button is held through Core's
 *       controls, so the game carries on eating &mdash; the slowdown, the sprint
 *       stopping &mdash; exactly as for a held right mouse button.</li>
 * </ol>
 *
 * <h2>Only the server finishes an item</h2>
 * The client never finishes eating by itself: it counts down, then waits for the
 * server's {@code CONSUME_ITEM} status ({@link Players#finishedUses()}), and only
 * then finishes the item and stops. So the step is {@link Status#DONE} on that
 * status alone, and the slot is kept to the last bite, since any slot packet
 * before it ends the use on the server (vanilla {@code onUpdateSelectedSlot}).
 *
 * <p>Two ways that goes wrong, both failures here rather than a step that quietly
 * says it ate:
 * <ul>
 *   <li><b>Cut short:</b> the use stopped without the status &mdash; another slot
 *       claim won, the player picked a slot, the server or a plugin stopped it.
 *       Client and server stopped together, so nothing is left to undo.</li>
 *   <li><b>Never finished:</b> the client is still eating well past when the
 *       server should have finished, counting the item's use time at the server's
 *       TPS plus the round trip. The server refused the use and never said so (a
 *       plugin, a cooldown the client did not know). The button is let go, so the
 *       game sends the release itself and both sides agree it stopped, rather
 *       than the player walking slowed forever while Grim expects full speed.</li>
 * </ul>
 *
 * <pre>{@code
 * Eat.food()                        // the best food for hunger; done at once if not hungry
 * Eat.item("golden_apple")          // this item, hungry or not
 * Eat.food().priority(ClaimPriority.NORMAL)   // the hotbar and the use claimed lower
 * Eat.food().silent(() -> true)     // the hotbar keeps showing the player's slot
 * }</pre>
 */
public final class Eat extends Step {

    /** Ticks to get the slot and the first use out before giving up. */
    private static final int PATIENCE = 40;

    /** Ticks after a use went out for the client to show it eating; it does at once if it accepted. */
    private static final int START_WAIT = 2;

    /** Uses sent before giving up: one the client keeps refusing is not sent every tick. */
    private static final int MAX_USES = 3;

    /** Ticks past the server's expected finish, on top of the round trip, before deciding it never will. */
    private static final int FINISH_SLACK = 10;

    /** The round trip, in ticks, when the tab list has no ping for the player. */
    private static final int UNKNOWN_ROUND_TRIP = 10;

    /** The item to eat, or null for the best food for hunger. */
    private final String item;

    /** What the slot and the use are claimed at. */
    private IntSupplier priority = () -> ClaimPriority.HIGH;

    /** Whether the hotbar and the hand keep showing the player's own slot. */
    private BooleanSupplier silent = () -> false;

    private int slot;
    private boolean started;
    private int waited;
    private int usesSent;
    private long lastUseTick;
    private boolean usePending;
    private int finishedBefore;
    private boolean ate;

    private Eat(String item, String name) {
        super(name);
        this.item = item;
        uses(Control.USE);
    }

    /** @return a step that eats the best food in the hotbar, if hungry */
    public static Eat food() {
        return new Eat(null, "eat");
    }

    /** @return a step that eats or drinks this item from the hotbar; namespace optional */
    public static Eat item(String itemId) {
        return new Eat(itemId, "eat " + itemId);
    }

    /**
     * Claims the hotbar slot and the use at {@code priority} rather than
     * {@link ClaimPriority#HIGH}. Equal claims go to whoever asked first, so to
     * eat while a combat module holds the hotbar at {@code HIGH} this has to be
     * higher; to wait for it, lower.
     *
     * @return this step
     */
    public Eat priority(int priority) {
        return priority(() -> priority);
    }

    /** @return this step, claiming at whatever {@code priority} says when it asks; for a setting */
    public Eat priority(IntSupplier priority) {
        this.priority = priority;
        return this;
    }

    /**
     * Keeps the hotbar and the first-person hand showing the player's own slot
     * while this eats, whenever {@code silent} says so. The food is still really
     * selected, on the client and the server alike, so the eat is an ordinary one
     * to Grim; see {@code InventoryService#conceal}.
     *
     * @return this step
     */
    public Eat silent(BooleanSupplier silent) {
        this.silent = silent;
        return this;
    }

    /** @return whether the last run ended with the server saying the item was eaten */
    public boolean ate() {
        return ate;
    }

    @Override
    protected void start(FlowContext c) {
        // Resumed after a pause, an eat in progress was let go: start it over.
        started = false;
        waited = 0;
        usesSent = 0;
        usePending = false;
        ate = false;
    }

    @Override
    protected Status tick(FlowContext c) {
        if (started) {
            return eating(c);
        }
        slot = item == null ? Inventories.bestFoodSlot() : Inventories.findInHotbar(item);
        if (slot == Inventories.NOT_FOUND) {
            return c.fail("no " + label() + " in the hotbar");
        }
        if (item == null && Players.foodLevel() >= 20 && !Inventories.alwaysEdible(slot)) {
            return Status.DONE;
        }
        if (Inventories.onCooldown(slot)) {
            return c.fail(label() + " is cooling down");
        }
        if (++waited > PATIENCE) {
            return c.fail("the game would not start eating");
        }
        claim();
        if (Inventories.selectedSlot() != slot || Services.inventory().getServerSlot() != slot) {
            return Status.RUNNING;
        }
        if (Players.usingMainHand()) {
            // Ours, from the use sent last tick, or the player's own right click on it.
            started = true;
            if (usesSent == 0) {
                finishedBefore = Players.finishedUses();
            }
            c.hold(Click.USE);
            return Status.RUNNING;
        }
        if (Players.usingItem()) {
            // The offhand is busy (a shield, a bow): eating now would fight it.
            return Status.RUNNING;
        }
        if (usePending || (usesSent > 0 && c.tick() - lastUseTick < START_WAIT)) {
            return Status.RUNNING;
        }
        if (usesSent >= MAX_USES) {
            return c.fail("the game refused to start eating " + label());
        }
        usePending = true;
        finishedBefore = Players.finishedUses();
        Services.actions().submit(this, Action.use().priority(priority.getAsInt()).then(outcome -> {
            usePending = false;
            if (outcome == Outcome.SENT) {
                usesSent++;
                lastUseTick = c.tick();
            }
        }));
        return Status.RUNNING;
    }

    /** The bite under way: held until the server finishes it, and no longer than it could take. */
    private Status eating(FlowContext c) {
        if (Players.finishedUses() != finishedBefore) {
            ate = true;
            return Status.DONE;
        }
        if (!Players.usingMainHand()) {
            return c.fail("eating " + label() + " was cut short");
        }
        if (Players.itemUseTicks() > deadline()) {
            // Not holding the button again is what ends it: the game sends the release.
            return c.fail("the server never finished " + label());
        }
        // Kept selected to the last bite: a slot let go swaps back mid-eat.
        claim();
        c.hold(Click.USE);
        return Status.RUNNING;
    }

    /**
     * @return client ticks of use after which the server has certainly finished:
     *         the item's use time at the server's TPS, the round trip, and slack
     */
    private static int deadline() {
        double tps = Core.tps().hasEstimate() ? Math.max(1d, Math.min(20d, Core.tps().getTps())) : 20d;
        int serverTicks = (int) Math.ceil(Players.itemUseDuration() * 20d / tps);
        int ping = PlayerList.ping(Players.entityId());
        int roundTrip = ping == PlayerList.UNKNOWN_PING ? UNKNOWN_ROUND_TRIP : (int) Math.ceil(ping / 50d);
        return serverTicks + roundTrip + FINISH_SLACK;
    }

    private void claim() {
        int at = priority.getAsInt();
        Services.inventory().request(this, slot, at);
        if (silent.getAsBoolean()) {
            Services.inventory().conceal(this);
        }
    }

    private String label() {
        return item == null ? "food" : item;
    }

    @Override
    protected void stop(FlowContext c, StopReason why) {
        Services.inventory().release(this);
        Services.actions().release(this);
        usePending = false;
    }
}
