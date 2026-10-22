package dev.px.karatsuba.client.automation.step;

import dev.px.combat.place.PlacementPlanner;
import dev.px.combat.place.Plan;
import dev.px.core.flow.Control;
import dev.px.core.flow.FlowContext;
import dev.px.core.flow.Status;
import dev.px.core.flow.Step;
import dev.px.core.flow.StopReason;
import dev.px.core.math.Vec3i;
import dev.px.karatsuba.adapter.combat.Combat;
import dev.px.karatsuba.adapter.combat.CombatKit;
import dev.px.karatsuba.adapter.game.item.Inventories;
import dev.px.karatsuba.adapter.game.player.Players;
import dev.px.karatsuba.adapter.world.Blocks;
import dev.px.karatsuba.client.service.Services;
import dev.px.karatsuba.client.service.action.Action;
import dev.px.karatsuba.client.service.action.Outcome;

import java.util.List;

/**
 * Places one block from the hotbar into a cell, the way every placing module does:
 * a click planned by the combat library's placement planner (strict faces, nothing
 * placed underneath first), sent through {@code Services.actions()}, which turns,
 * swaps to the item and waits for the server's view of the head before it clicks.
 *
 * <p>Done once the cell holds the block. The item must already be in the hotbar
 * ({@link ToHotbar} first). Fails when the cell is taken by something else, when
 * there is nothing to place against for long enough, or when the click is refused.
 */
public final class PlaceBlock extends Step {

    /** Ticks to wait for a placeable click and the server's look before giving up. */
    private static final int PATIENCE = 60;

    private final Vec3i cell;
    private final String item;

    private PlacementPlanner planner;
    private Outcome outcome;
    private int waited;

    /**
     * @param item the registry id, namespace included ({@code minecraft:ender_chest});
     *             it is also what the cell holds once placed
     */
    public PlaceBlock(Vec3i cell, String item) {
        super("place " + shortName(item) + " at " + cell.getX() + ", " + cell.getY() + ", " + cell.getZ());
        this.cell = cell;
        this.item = item;
        uses(Control.ROTATION, Control.USE);
    }

    @Override
    protected void start(FlowContext c) {
        if (planner == null) {
            planner = build(Combat.kit());
        }
        outcome = null;
        waited = 0;
    }

    private static <E> PlacementPlanner build(CombatKit<E> kit) {
        return kit.planner(kit.strictStyle())
                .perTick(() -> 1)
                .supports(0)
                .build();
    }

    @Override
    protected Status tick(FlowContext c) {
        if (item.equals(Blocks.idAt(cell))) {
            return Status.DONE;
        }
        if (!Blocks.isReplaceable(cell)) {
            return c.fail("the cell is taken by " + shortName(Blocks.idAt(cell)));
        }
        if (outcome == Outcome.OUT_OF_REACH || outcome == Outcome.REFUSED || outcome == Outcome.NOT_ALLOWED) {
            return c.fail("the click was not sent: " + outcome.name().toLowerCase());
        }
        if (!Inventories.hotbarHas(item)) {
            return c.fail("no " + shortName(item) + " in the hotbar");
        }
        if (++waited > PATIENCE) {
            return c.fail("nothing to place against, or no look at it");
        }
        Plan plan = planner.plan(Players.eyePosition(), List.of(cell), Players.serverRotation());
        if (!plan.getSteps().isEmpty()) {
            Services.actions().submit(this, Action.click(plan.getSteps().get(0).getClick())
                    .holding(item)
                    .priority(c.priority())
                    .then(sent -> outcome = sent));
        }
        return Status.RUNNING;
    }

    @Override
    protected void stop(FlowContext c, StopReason why) {
        Services.actions().release(this);
    }

    private static String shortName(String id) {
        int colon = id.indexOf(':');
        return (colon < 0 ? id : id.substring(colon + 1)).replace('_', ' ');
    }
}
