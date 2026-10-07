package dev.px.karatsuba.client.module.movement;

import dev.px.core.event.Priority;
import dev.px.core.event.Stage;
import dev.px.core.event.Subscribe;
import dev.px.core.event.impl.TickEvent;
import dev.px.core.math.Vec3;
import dev.px.core.module.Module;
import dev.px.core.module.ModuleInfo;
import dev.px.core.movement.simulation.MotionState;
import dev.px.core.setting.impl.NumberSetting;
import dev.px.karatsuba.adapter.game.player.Inputs;
import dev.px.karatsuba.adapter.game.player.Players;

import java.util.List;

/**
 * Jumps from the very edge of a block: on the last tick the player is on the ground.
 *
 * <p>The longest jump from a ledge is the one taken as late as possible, which by
 * hand is a matter of timing to the tick. Core's simulation knows the tick: it
 * walks the player one tick forward with the keys they hold, and if that move
 * leaves the ground over a drop, jump is pressed now, while there is still
 * ground under them to jump from.
 *
 * <p>A real key press for one tick, so the game jumps as it would for the player
 * and reports the key to the server; nothing about the jump itself is changed.
 * Steps down shallower than {@code Min Drop} (a slab, a stair) are walked, not
 * jumped.
 *
 * <p>Runs ahead of Safe Walk, which steps aside when jump is held, so the two can
 * be on together: Parkour jumps the gaps and Safe Walk stops at the edges a jump
 * was not taken from.
 */
@ModuleInfo(name = "Parkour", description = "Jumps automatically at the edge of a block", category = "Movement")
public final class ParkourModule extends Module {

    private final NumberSetting<Integer> minDrop = integer("Min Drop", 1, 1, 10)
            .describe("Smallest drop, in blocks, worth jumping instead of walking off");

    private final TickKey jump = new TickKey(Inputs.Control.JUMP);

    @Subscribe(stage = Stage.PRE, priority = Priority.HIGH)
    private void onTickStart(TickEvent event) {
        if (!Players.present() || !Players.onGround() || Players.inWater() || !Ledges.moving()
                || Inputs.pressed(Inputs.Control.SNEAK) || Inputs.pressed(Inputs.Control.JUMP)) {
            return;
        }
        MotionState now = Ledges.now();
        if (now == null) {
            return;
        }
        List<MotionState> walk = Ledges.ahead(Ledges.held(false, false), 1);
        if (walk.isEmpty() || walk.get(0).isOnGround()) {
            return;
        }
        Vec3 next = walk.get(0).getPosition();
        Vec3 above = Vec3.of(next.getX(), now.getPosition().getY(), next.getZ());
        if (Ledges.dropBelow(above, minDrop.getInt()) >= minDrop.getInt()) {
            jump.press();
        }
    }

    @Override
    protected void onDisable() {
        jump.release();
    }
}
