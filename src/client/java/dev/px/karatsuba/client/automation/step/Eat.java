package dev.px.karatsuba.client.automation.step;

import dev.px.core.control.Click;
import dev.px.core.flow.Control;
import dev.px.core.flow.FlowContext;
import dev.px.core.flow.Status;
import dev.px.core.flow.Step;
import dev.px.core.flow.StopReason;
import dev.px.karatsuba.adapter.game.item.Inventories;
import dev.px.karatsuba.adapter.game.player.Players;
import dev.px.karatsuba.client.service.Services;
import dev.px.karatsuba.client.service.action.Action;
import dev.px.karatsuba.client.service.claim.ClaimPriority;

import java.util.function.IntSupplier;

/**
 * Eats or drinks one item from the hotbar, start to finish.
 *
 * <p>Done the way a player does it, because Grim tracks item use from the
 * packets: the item is switched to in the open, used, and held until the game
 * finishes it. A silent eat &mdash; swapped to for a packet and back &mdash; is
 * slowed on the server and not on the client, which is a Simulation flag.
 *
 * <ol>
 *   <li>The slot is asked of {@code Services.inventory()}, which selects it as
 *       the player's scroll wheel would.</li>
 *   <li>Once the server holds it, the use goes out through
 *       {@code Services.actions()}: straight at the item, never at whatever block
 *       the crosshair is on, so a chest in view is not opened instead.</li>
 *   <li>Once the game is using it, the use button is held through Core's
 *       controls, so the game carries on eating &mdash; the slowdown, the sprint
 *       stopping, the finish &mdash; exactly as for a held right mouse button. It
 *       is let go the tick the game finishes, before the game could start
 *       another.</li>
 * </ol>
 *
 * <pre>{@code
 * Eat.food()                        // the best food for hunger; done at once if not hungry
 * Eat.item("golden_apple")          // this item, hungry or not
 * Eat.food().priority(ClaimPriority.NORMAL)   // the hotbar and the use claimed lower
 * }</pre>
 */
public final class Eat extends Step {

    /** Ticks to wait for the slot and the first use before giving up. */
    private static final int PATIENCE = 40;

    /** The item to eat, or null for the best food for hunger. */
    private final String item;

    /** What the slot and the use are claimed at. */
    private IntSupplier priority = () -> ClaimPriority.HIGH;

    private int slot;
    private boolean started;
    private int waited;

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

    @Override
    protected void start(FlowContext c) {
        // Resumed after a pause, an eat in progress was let go: start it over.
        started = false;
        waited = 0;
    }

    @Override
    protected Status tick(FlowContext c) {
        if (started) {
            if (!Players.usingItem()) {
                return Status.DONE;
            }
            // Kept selected to the last bite: a slot let go swaps back mid-eat.
            Services.inventory().request(this, slot, priority.getAsInt());
            c.hold(Click.USE);
            return Status.RUNNING;
        }
        slot = item == null ? Inventories.bestFoodSlot() : Inventories.findInHotbar(item);
        if (slot == Inventories.NOT_FOUND) {
            return c.fail(item == null ? "no food in the hotbar" : "no " + item + " in the hotbar");
        }
        if (item == null && Players.foodLevel() >= 20 && !Inventories.alwaysEdible(slot)) {
            return Status.DONE;
        }
        if (++waited > PATIENCE) {
            return c.fail("the game would not start eating");
        }
        Services.inventory().request(this, slot, priority.getAsInt());
        if (Inventories.selectedSlot() != slot || Services.inventory().getServerSlot() != slot) {
            return Status.RUNNING;
        }
        if (Players.usingItem()) {
            started = true;
            c.hold(Click.USE);
            return Status.RUNNING;
        }
        Services.actions().submit(this, Action.use().priority(priority.getAsInt()));
        return Status.RUNNING;
    }

    @Override
    protected void stop(FlowContext c, StopReason why) {
        Services.inventory().release(this);
        Services.actions().release(this);
    }
}
