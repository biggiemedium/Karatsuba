package dev.px.karatsuba.client.module.movement.strafe;

import dev.px.core.event.Stage;
import dev.px.core.event.Subscribe;
import dev.px.core.event.impl.TickEvent;
import dev.px.core.math.Vec3;
import dev.px.core.setting.impl.BooleanSetting;
import dev.px.core.setting.impl.NumberSetting;
import dev.px.core.util.math.MovementMath;
import dev.px.karatsuba.adapter.event.game.player.PlayerMoveEvent;
import dev.px.karatsuba.adapter.game.player.Inputs;
import dev.px.karatsuba.adapter.game.player.Players;
import dev.px.karatsuba.client.module.mode.SubMode;
import dev.px.karatsuba.client.module.movement.StrafeModule;
import dev.px.karatsuba.client.module.movement.control.TickKey;
import dev.px.karatsuba.client.service.Services;
import dev.px.karatsuba.client.service.claim.ClaimPriority;

/**
 * Plain strafe: bunny hops and points all of the current speed along the keys.
 *
 * <p>Every tick the horizontal velocity is rotated onto the direction the
 * movement keys point, keeping its length, so turning mid-air costs nothing.
 * With no key held the horizontal motion is zeroed, so letting go stops dead.
 * On the ground it jumps, through a real key press, so the hop is the game's own.
 *
 * <p>Hurt Boost replaces the speed with a fixed one on the first tick after a
 * hit, riding the knockback instead of fighting it.
 *
 * <p>No attempt at an anticheat: velocity is replaced outright, which Grim's
 * simulation rejects the moment it differs from what the keys would give.
 */
public final class VanillaStrafe extends SubMode<StrafeModule.Mode> {

    /** {@code hurtTime} on the first tick the player can act on a hit. */
    private static final int FIRST_HURT_TICK = 9;

    private final BooleanSetting hurtBoost = bool("Hurt Boost", false)
            .describe("Jump to a fixed speed on the tick after taking damage");

    private final NumberSetting<Double> boostSpeed = decimal("Boost Speed", 1d, 0.1d, 4d)
            .step(0.1d)
            .visibleWhen(hurtBoost::isOn)
            .describe("Blocks per tick to move at after a hit");

    private final TickKey jump = new TickKey(Inputs.Control.JUMP);

    public VanillaStrafe() {
        super(StrafeModule.Mode.VANILLA);
    }

    /** Before the keyboard is read, so the hop happens this tick. */
    @Subscribe(stage = Stage.PRE)
    private void onTickStart(TickEvent event) {
        if (Players.present() && Players.onGround() && Inputs.anyMovement()) {
            jump.press();
        }
    }

    @Subscribe(stage = Stage.PRE)
    private void onMove(PlayerMoveEvent event) {
        // Keys rather than the game's input: at PRE that input is still last tick's.
        double forward = axis(Inputs.Control.FORWARD, Inputs.Control.BACK);
        double strafe = axis(Inputs.Control.LEFT, Inputs.Control.RIGHT);

        if (!MovementMath.isMoving(forward, strafe)) {
            Services.motion().setHorizontal(this, 0d, 0d, ClaimPriority.NORMAL);
            return;
        }

        double speed = hurtBoost.isOn() && Players.hurtTime() == FIRST_HURT_TICK
                ? boostSpeed.getDouble()
                : MovementMath.speed(event.getVelocity());

        Vec3 motion = MovementMath.velocity(Players.rotation().getYaw(), forward, strafe, speed);
        Services.motion().setHorizontal(this, motion.getX(), motion.getZ(), ClaimPriority.NORMAL);
    }

    /** Lets go of a jump this mode is holding; the module calls it when switched off. */
    public void stop() {
        jump.release();
    }

    private static double axis(Inputs.Control positive, Inputs.Control negative) {
        return (Inputs.pressed(positive) ? 1d : 0d) - (Inputs.pressed(negative) ? 1d : 0d);
    }
}
