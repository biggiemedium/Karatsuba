package dev.px.karatsuba.client.automation.step;

import dev.px.core.flow.FlowContext;
import dev.px.core.flow.Status;
import dev.px.core.flow.Step;
import dev.px.core.flow.StopReason;
import dev.px.karatsuba.adapter.game.item.Inventories;
import dev.px.karatsuba.client.service.Services;
import dev.px.karatsuba.client.service.action.Action;
import dev.px.karatsuba.client.service.action.Outcome;
import dev.px.karatsuba.client.service.claim.ClaimPriority;

/**
 * Puts an item in the offhand: a totem before a fight, a shield, a stack of
 * crystals.
 *
 * <p>Through {@code Services.actions()}, which does it the way Grim allows: from
 * the hotbar with the swap-hands key, alone in its tick; from the main inventory
 * with one click, only while the server believes the player is standing still,
 * and standing them still for that one tick if moving is all that is in the way.
 *
 * <p>Done once the offhand holds it, at once if it already does. Fails when there
 * is none to equip.
 */
public final class Equip extends Step {

    private final String item;

    private Outcome outcome;

    private Equip(String item) {
        super("offhand " + item);
        this.item = item;
    }

    /** @return a step that puts {@code item} in the offhand; namespace optional */
    public static Equip offhand(String item) {
        return new Equip(item);
    }

    @Override
    protected void start(FlowContext c) {
        outcome = null;
    }

    @Override
    protected Status tick(FlowContext c) {
        if (Inventories.offhandHolds(item)) {
            return Status.DONE;
        }
        if (outcome == Outcome.NO_ITEM || Inventories.count(item) == 0) {
            return c.fail("no " + item + " to put in the offhand");
        }
        Services.actions().submit(this, Action.offhand(item)
                .stopping()
                .priority(ClaimPriority.HIGH)
                .then(sent -> outcome = sent));
        return Status.RUNNING;
    }

    @Override
    protected void stop(FlowContext c, StopReason why) {
        Services.actions().release(this);
    }
}
