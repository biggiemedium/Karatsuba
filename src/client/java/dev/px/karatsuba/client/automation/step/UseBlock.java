package dev.px.karatsuba.client.automation.step;

import dev.px.core.flow.Control;
import dev.px.core.flow.FlowContext;
import dev.px.core.flow.Status;
import dev.px.core.flow.Step;
import dev.px.core.flow.StopReason;
import dev.px.core.math.Vec2;
import dev.px.core.math.Vec3;
import dev.px.core.math.Vec3i;
import dev.px.karatsuba.adapter.game.entity.Targets;
import dev.px.karatsuba.adapter.game.player.Players;
import dev.px.karatsuba.adapter.world.Blocks;
import dev.px.karatsuba.client.service.Services;
import dev.px.karatsuba.client.service.action.Action;
import dev.px.karatsuba.client.service.action.Outcome;
import dev.px.karatsuba.client.service.claim.ClaimPriority;

/**
 * Right-clicks a block once: opens a chest, presses a button, pulls a lever.
 *
 * <p>Looks at the nearest part of the block, as a player reaches for what is in
 * front of them, then clicks it through {@code Services.actions()}, which sends
 * it only once the rotation the server has looks at the block, from within reach,
 * at the start of a tick &mdash; Grim's RotationPlace and Post checks both. Done
 * the moment the click goes out; what the block does about it is the next step's
 * to wait for.
 *
 * <p>The block must be within reach: walk there first. Fails when it is not, when
 * something is in the way for long enough, or when the click is refused.
 */
public final class UseBlock extends Step {

    /** Ticks to wait for a clear look and the click to go out. */
    private static final int PATIENCE = 60;

    /** How far inside the block's outline to look, so the ray lands on it, not its edge. */
    private static final double INSET = 0.15d;

    private final Vec3i block;

    private Outcome outcome;
    private int waited;

    public UseBlock(Vec3i block) {
        super("use " + block.getX() + ", " + block.getY() + ", " + block.getZ());
        this.block = block;
        uses(Control.ROTATION, Control.USE);
    }

    @Override
    protected void start(FlowContext c) {
        outcome = null;
        waited = 0;
    }

    @Override
    protected Status tick(FlowContext c) {
        if (outcome == Outcome.SENT) {
            return Status.DONE;
        }
        if (outcome == Outcome.OUT_OF_REACH || outcome == Outcome.REFUSED || outcome == Outcome.NOT_ALLOWED) {
            return c.fail("the click was not sent: " + outcome.name().toLowerCase());
        }
        if (Blocks.isAir(block)) {
            return c.fail("there is no block to use");
        }
        if (++waited > PATIENCE) {
            return c.fail("could not get a clear look at the block");
        }
        Vec3 eyes = Players.eyePosition();
        Vec3 aim = Blocks.outline(block).inset(INSET).closestPoint(eyes);
        if (eyes.distanceTo(aim) > Players.blockReach()) {
            return c.fail("the block is out of reach");
        }
        Vec2 look = eyes.rotationTo(aim);
        c.look(Gaze.toward(look));

        Targets.Hit hit = Targets.raycast(eyes, look, Players.blockReach());
        if (hit != null && hit.getBlock().equals(block)) {
            Services.actions().submit(this, Action.click(block, hit.getFace(), hit.getPoint())
                    .anyFace()
                    .rotate(false)
                    .priority(ClaimPriority.HIGH)
                    .then(sent -> outcome = sent));
        }
        return Status.RUNNING;
    }

    @Override
    protected void stop(FlowContext c, StopReason why) {
        Services.actions().release(this);
    }
}
