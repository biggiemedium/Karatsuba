package dev.px.karatsuba.client.module.movement;

import dev.px.core.event.Priority;
import dev.px.core.event.Stage;
import dev.px.core.event.Subscribe;
import dev.px.core.event.impl.TickEvent;
import dev.px.karatsuba.client.module.ClientModule;
import dev.px.karatsuba.client.module.movement.control.Ledges;
import dev.px.karatsuba.client.module.movement.control.TickKey;
import dev.px.core.module.Module;
import dev.px.core.module.ModuleInfo;
import dev.px.core.movement.simulation.MotionState;
import dev.px.core.setting.impl.BooleanSetting;
import dev.px.karatsuba.adapter.game.player.Inputs;
import dev.px.karatsuba.adapter.game.player.Players;

import java.util.List;

/**
 * Gets up full-block ledges by jumping onto them the moment the player walks into one.
 *
 * <h2>Why not step height</h2>
 * Raising the step-height attribute, or faking the packets of a climb, moves the
 * player up a block in a way their keys cannot explain, and Grim simulates from
 * the keys. A jump they can. So this presses jump for one tick when it is the
 * jump that gets the player up: the climb is the game's own jump, reported as
 * one, and costs what a jump costs.
 *
 * <h2>When</h2>
 * Two questions for Core's simulation at the start of each tick, with the keys
 * the player holds:
 * <ol>
 *   <li><b>Is the way blocked?</b> Walking on for a tick barely moves them.</li>
 *   <li><b>Does a jump clear it?</b> Jumping lands them on the ground higher than
 *       the game's own step (0.6 blocks) would take them.</li>
 * </ol>
 * Both, and jump is held. Ceilings, obstacles too tall to clear and walls with
 * nothing on top all fail the second question by themselves, because the
 * simulation collides with them; steps the game already walks up (slabs, stairs)
 * never fail the first.
 */
@ModuleInfo(name = "Step", description = "Jumps up blocks you walk into", category = "Movement")
public final class StepModule extends ClientModule {

    /** Vanilla's step height: anything lower the game walks up by itself. */
    private static final double STEP_HEIGHT = 0.6d;

    /** Less than this horizontally in a tick of walking is walking into something. */
    private static final double BLOCKED = 0.03d;

    /** Long enough for any jump to come down. */
    private static final int JUMP_TICKS = 14;

    private final BooleanSetting forwardOnly = bool("Forward Only", true)
            .describe("Only step when walking forward, not strafing or backing up");

    private final TickKey jump = new TickKey(Inputs.Control.JUMP);

    @Subscribe(stage = Stage.PRE, priority = Priority.HIGH)
    private void onTickStart(TickEvent event) {
        if (!Players.present() || !Players.onGround() || Players.inWater() || !Ledges.moving()
                || Inputs.pressed(Inputs.Control.SNEAK) || Inputs.pressed(Inputs.Control.JUMP)
                || (forwardOnly.isOn() && !Inputs.pressed(Inputs.Control.FORWARD))) {
            return;
        }
        MotionState now = Ledges.now();
        if (now != null && blocked(now) && jumpClears(now)) {
            jump.press();
        }
    }

    /** @return whether a tick of walking on barely moves the player */
    private boolean blocked(MotionState now) {
        List<MotionState> walk = Ledges.ahead(Ledges.held(false, false), 1);
        return !walk.isEmpty() && walk.get(0).isOnGround()
                && Ledges.horizontal(now.getPosition(), walk.get(0).getPosition()) < BLOCKED;
    }

    /** @return whether a jump now lands on ground above what the game would step */
    private boolean jumpClears(MotionState now) {
        double floor = now.getPosition().getY() + STEP_HEIGHT;
        List<MotionState> arc = Ledges.ahead(Ledges.held(true, false), JUMP_TICKS);
        for (int tick = 1; tick < arc.size(); tick++) {
            MotionState state = arc.get(tick);
            if (state.isOnGround()) {
                return state.getPosition().getY() > floor;
            }
        }
        return false;
    }

    @Override
    protected void onDisable() {
        jump.release();
    }
}
