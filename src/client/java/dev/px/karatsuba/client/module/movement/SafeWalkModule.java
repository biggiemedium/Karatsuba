package dev.px.karatsuba.client.module.movement;

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
 * Stops at edges by crouching on the tick the player would walk off one.
 *
 * <h2>Why it sneaks</h2>
 * The usual Safe Walk tells the game the player is sneaking for the ledge check
 * only, so the client stops at the edge while every key it reports says it is
 * walking. Grim simulates the move from those keys, finds a player who should
 * have stepped off, and sets them back. This presses the real sneak key instead,
 * for one tick: the game clips the move at the ledge exactly as it does for a
 * crouching player, and the server sees shift held and the same clip.
 *
 * <h2>When</h2>
 * At the start of each tick, Core's simulation walks the player forward with
 * the keys they hold for {@code Look Ahead} ticks. If that walk leaves the
 * ground over a drop of at least {@code Min Drop} blocks, sneak is held this
 * tick. A step down onto a slab, or a drop shallower than the setting, is let
 * through. The crouch costs speed only on the ticks it is held, so walking along
 * an edge stays close to full speed.
 *
 * <p>Steps aside when jump is held, by the player or by Parkour or Step: a jump
 * is a decision to leave the ground, and sneaking under it would only shorten it.
 */
@ModuleInfo(name = "Safe Walk", description = "Crouches at edges so you never walk off", category = "Movement")
public final class SafeWalkModule extends Module {

    private final NumberSetting<Integer> minDrop = integer("Min Drop", 1, 1, 10)
            .describe("Smallest fall, in blocks, worth stopping for");

    private final NumberSetting<Integer> lookAhead = integer("Look Ahead", 2, 1, 3)
            .describe("Ticks ahead to check; more stops a little earlier at speed");

    private final TickKey sneak = new TickKey(Inputs.Control.SNEAK);

    @Subscribe(stage = Stage.PRE)
    private void onTickStart(TickEvent event) {
        if (!Players.present() || !Players.onGround() || Players.inWater() || !Ledges.moving()
                || Inputs.pressed(Inputs.Control.SNEAK) || Inputs.pressed(Inputs.Control.JUMP)) {
            return;
        }
        MotionState now = Ledges.now();
        if (now != null && walksOff(now)) {
            sneak.press();
        }
    }

    /** @return whether walking on leaves the ground over a drop worth stopping for */
    private boolean walksOff(MotionState now) {
        List<MotionState> walk = Ledges.ahead(Ledges.held(false, false), lookAhead.getInt());
        for (MotionState next : walk) {
            if (next.isOnGround()) {
                continue;
            }
            // Measured from the level the player stands at, so the fall already
            // begun in the simulation does not count against the drop.
            Vec3 above = Vec3.of(next.getPosition().getX(), now.getPosition().getY(), next.getPosition().getZ());
            return Ledges.dropBelow(above, minDrop.getInt()) >= minDrop.getInt();
        }
        return false;
    }

    @Override
    protected void onDisable() {
        sneak.release();
    }
}
