package dev.px.karatsuba.client.automation.step;

import dev.px.core.flow.Control;
import dev.px.core.flow.FlowContext;
import dev.px.core.flow.Status;
import dev.px.core.flow.Step;
import dev.px.core.flow.StopReason;
import dev.px.core.math.Vec3i;
import dev.px.karatsuba.adapter.game.item.Inventories;
import dev.px.karatsuba.adapter.game.player.Digging;
import dev.px.karatsuba.adapter.world.Blocks;
import dev.px.karatsuba.client.service.Services;
import dev.px.karatsuba.client.service.dig.DigReport;
import dev.px.karatsuba.client.service.dig.DigRequest;

import java.util.function.IntSupplier;

/**
 * Digs one block through the dig service: the game's own breaking, with a silent
 * look, and Client Settings &gt; Mining (Quiet Mining, Early Break) applied, exactly
 * as Smart Mine and Ender Chest Farmer dig.
 *
 * <p>Where {@link BreakBlock} turns the camera and holds the attack button, as a
 * player visibly would, this leaves the camera alone: the dig service turns the head
 * silently and only as Grim needs it. Use it for work done while the player is not
 * steering.
 *
 * <p>The tool is the caller's choice, a hotbar slot asked for each tick, so a step
 * that must avoid Silk Touch (or wants one) can. Done once the block is gone or
 * replaced. Its progress is the dig's, for a flow's {@code stuckAfter}. Fails when
 * the block stays out of reach or sight, or the tool never comes.
 */
public final class Dig extends Step {

    /** Ticks a dig may stay blocked or waiting before the step gives up. */
    private static final int PATIENCE = 100;

    private final Vec3i block;
    private final IntSupplier tool;

    private String original;
    private int stalled;

    private Dig(Vec3i block, IntSupplier tool) {
        super("dig " + block.getX() + ", " + block.getY() + ", " + block.getZ());
        this.block = block;
        this.tool = tool;
        uses(Control.ROTATION, Control.ATTACK);
    }

    /** @return a step that digs the block with the fastest tool in the hotbar, or the hand */
    public static Dig block(Vec3i block) {
        return new Dig(block, () -> Inventories.bestToolSlotFor(Blocks.idAt(block)));
    }

    /** @return a step that digs the block holding the hotbar slot {@code tool} gives each tick */
    public static Dig block(Vec3i block, IntSupplier tool) {
        return new Dig(block, tool);
    }

    @Override
    protected void start(FlowContext c) {
        if (original == null || !c.isResuming()) {
            original = Blocks.idAt(block);
        }
        stalled = 0;
    }

    @Override
    protected Status tick(FlowContext c) {
        if (Blocks.isAir(block) || !Blocks.idAt(block).equals(original)) {
            return Status.DONE;
        }
        int slot = tool.getAsInt();
        if (slot >= Inventories.HOTBAR_SIZE) {
            return c.fail("the tool is not in the hotbar");
        }
        Services.digging().dig(this, DigRequest.of(block).tool(slot).priority(c.priority()));

        DigReport report = Services.digging().report(this);
        boolean going = report.state() == DigReport.State.DIGGING || report.state() == DigReport.State.COOLING;
        stalled = going ? 0 : stalled + 1;
        if (stalled > PATIENCE) {
            return c.fail(report.reason().isEmpty() ? "the dig never started" : "waited too long for " + report.reason());
        }
        return Status.RUNNING;
    }

    /** @return how far through the block the dig is, 0 to 1; for a flow's stuck check */
    @Override
    protected double progress(FlowContext c) {
        return Math.max(0f, Digging.progress(block));
    }

    @Override
    protected void stop(FlowContext c, StopReason why) {
        Services.digging().release(this);
        Services.inventory().release(this);
    }
}
