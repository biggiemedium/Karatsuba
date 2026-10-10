package dev.px.karatsuba.client.automation.step;

import dev.px.core.flow.Control;
import dev.px.core.flow.FlowContext;
import dev.px.core.flow.Status;
import dev.px.core.flow.Step;
import dev.px.core.movement.simulation.MovementInput;
import dev.px.karatsuba.adapter.game.player.Inputs;
import dev.px.karatsuba.adapter.game.player.Players;

/**
 * Closes the open container, standing still to do it.
 *
 * <p>Grim flags a window closed while the last input packet held a movement key
 * or jump, or while sprinting (MultiActionsD). So the step holds no keys until the
 * server has been told the player stopped, then closes it, a tick or two later
 * at most. Done at once when nothing is open.
 */
public final class CloseContainer extends Step {

    public CloseContainer() {
        super("close the container");
        uses(Control.MOVEMENT);
    }

    @Override
    protected Status tick(FlowContext c) {
        if (!Players.inOtherContainer()) {
            return Status.DONE;
        }
        c.move(MovementInput.none(Players.rotation().getYaw()));
        if (!Inputs.reportedMovement() && !Players.sprinting()) {
            Players.closeContainer();
        }
        return Status.RUNNING;
    }
}
