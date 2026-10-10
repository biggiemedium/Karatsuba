package dev.px.karatsuba.client.automation.step;

import dev.px.core.control.Click;
import dev.px.core.flow.Control;
import dev.px.core.flow.FlowContext;
import dev.px.core.flow.Status;
import dev.px.core.flow.Step;
import dev.px.core.flow.StopReason;
import dev.px.core.math.Vec3;
import dev.px.core.math.Vec3i;
import dev.px.karatsuba.adapter.game.entity.Targets;
import dev.px.karatsuba.adapter.game.item.Inventories;
import dev.px.karatsuba.adapter.game.player.Interactions;
import dev.px.karatsuba.adapter.game.player.Players;
import dev.px.karatsuba.adapter.world.Blocks;
import dev.px.karatsuba.client.service.Services;
import dev.px.karatsuba.client.service.claim.ClaimPriority;

/**
 * Breaks one block the way a player does: the best tool in hand, the crosshair on
 * the block, the attack button held until it goes.
 *
 * <p>The breaking itself is the game's. With the button held and the crosshair on
 * the block, vanilla starts, progresses, swings and finishes it, at the tool's
 * real speed and with its own packets, so there is nothing for Grim's break
 * checks to tell from a player. What this step does is what a player's hand and
 * eyes do:
 *
 * <ul>
 *   <li>picks the fastest tool in the hotbar, switched to in the open;</li>
 *   <li>turns the <em>camera</em> to the nearest part of the block, eased &mdash;
 *       the camera, not a silent rotation, because the game breaks whatever the
 *       crosshair is on;</li>
 *   <li>holds the button only while the crosshair is on that block, so nothing
 *       on the way past is chipped.</li>
 * </ul>
 *
 * <p>Done once the block is gone or replaced. Its progress is the crack stage, so
 * a flow's {@code stuckAfter} notices a block that is not breaking. Fails when the
 * block is out of reach, or when the crosshair cannot be brought onto it.
 */
public final class BreakBlock extends Step {

    /** Ticks to wait for the crosshair to land on the block and breaking to begin. */
    private static final int PATIENCE = 60;

    /** How far inside the block's outline to look, so the crosshair lands on it, not its edge. */
    private static final double INSET = 0.15d;

    private final Vec3i block;

    private String original;
    private int waited;

    public BreakBlock(Vec3i block) {
        super("break " + block.getX() + ", " + block.getY() + ", " + block.getZ());
        this.block = block;
        uses(Control.ROTATION, Control.ATTACK);
    }

    @Override
    protected void start(FlowContext c) {
        if (original == null || !c.isResuming()) {
            original = Blocks.idAt(block);
        }
        waited = 0;
    }

    @Override
    protected Status tick(FlowContext c) {
        if (Blocks.isAir(block) || !Blocks.idAt(block).equals(original)) {
            return Status.DONE;
        }
        Vec3 eyes = Players.eyePosition();
        Vec3 aim = Blocks.outline(block).inset(INSET).closestPoint(eyes);
        if (eyes.distanceTo(aim) > Players.blockReach()) {
            return c.fail("the block is out of reach");
        }
        boolean breaking = Interactions.breakingProgress() >= 0f;
        if (!breaking && ++waited > PATIENCE) {
            return c.fail("could not get the crosshair onto " + original);
        }

        int tool = Inventories.bestToolSlotFor(original);
        if (tool != Inventories.NOT_FOUND) {
            Services.inventory().request(this, tool, ClaimPriority.HIGH);
        }
        c.look(Gaze.camera(aim));
        Targets.Block crosshair = Targets.block();
        if (crosshair != null && crosshair.getPosition().equals(block)) {
            c.hold(Click.ATTACK);
        }
        return Status.RUNNING;
    }

    /** @return how cracked the block is, 0 to 1; for a flow's stuck check */
    @Override
    protected double progress(FlowContext c) {
        return Math.max(0f, Interactions.breakingProgress());
    }

    @Override
    protected void stop(FlowContext c, StopReason why) {
        Services.inventory().release(this);
    }
}
