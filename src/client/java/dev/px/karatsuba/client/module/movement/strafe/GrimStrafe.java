package dev.px.karatsuba.client.module.movement.strafe;

import dev.px.core.Core;
import dev.px.core.event.Stage;
import dev.px.core.event.Subscribe;
import dev.px.core.event.impl.TickEvent;
import dev.px.core.math.Vec2;
import dev.px.core.movement.rotation.RotationMode;
import dev.px.core.movement.rotation.RotationPriority;
import dev.px.core.movement.rotation.RotationRequest;
import dev.px.core.setting.impl.BooleanSetting;
import dev.px.core.util.math.MovementMath;
import dev.px.karatsuba.adapter.game.player.Inputs;
import dev.px.karatsuba.adapter.game.player.Players;
import dev.px.karatsuba.client.module.mode.SubMode;
import dev.px.karatsuba.client.module.movement.StrafeModule;
import dev.px.karatsuba.client.module.movement.TickKey;
import dev.px.karatsuba.client.settings.ClientSettings;

/**
 * Strafe that Grim's simulation can reproduce: steer the yaw, never the velocity.
 *
 * <p>Grim re-runs vanilla movement from the keys the client reports and the yaw
 * in its movement packet, so an edited velocity is a Simulation flag and a
 * setback. What it cannot object to is a player who turns to face where they are
 * going. So each tick the head turns silently to the direction the held keys
 * point, and Move Fix rewrites the keys against that yaw &mdash; A under a yaw
 * turned 90&deg; left is exactly W. From there everything is vanilla:
 *
 * <ul>
 *   <li><b>Sprint in every direction.</b> The game only sprints on forward
 *       input, and after the rewrite every direction is forward, so holding the
 *       real sprint key sprints sideways and backwards too.</li>
 *   <li><b>The sprint jump goes your way.</b> Its 0.2 boost is applied along the
 *       yaw, which is now the direction of the keys.</li>
 *   <li><b>Air control is spent on turning.</b> All of the 0.026 air acceleration
 *       pushes along the keys rather than being split against the camera.</li>
 *   <li><b>Diagonal while slowed.</b> Eating, blocking or sneaking, the head
 *       faces 45&deg; off so the keys are W+A, which 1.21.4 does not scale back
 *       down; see {@link #slowed()}. Lambda's idea, written fresh here.</li>
 * </ul>
 *
 * <p>Keys, yaw sent and yaw moved along agree every tick, which is the whole of
 * what {@code Grim.md} asks of movement code. The yaw is exact rather than
 * rounded, so STRICT correction lands on W with no drift.
 *
 * <p>It needs Move Fix and does nothing without it: walking under a silent yaw
 * with uncorrected keys is the Simulation flag this mode exists to avoid. It asks
 * at low priority, so an aura or Scaffold that needs the head takes it, and Move
 * Fix maps the keys against their yaw instead.
 */
public final class GrimStrafe extends SubMode<StrafeModule.Mode> {

    private final BooleanSetting sprint = bool("Sprint", true)
            .describe("Hold sprint, which now works in every direction");

    private final BooleanSetting autoJump = bool("Auto Jump", true)
            .describe("Hop whenever on the ground and moving");

    private final BooleanSetting diagonal = bool("Diagonal", true)
            .describe("Walk on W+A while eating, blocking or sneaking: about 41% faster");

    /** Facing this far right of the heading, the keys that walk along it are W+A exactly. */
    private static final float DIAGONAL_OFFSET = 45f;

    private final TickKey sprintKey = new TickKey(Inputs.Control.SPRINT);
    private final TickKey jumpKey = new TickKey(Inputs.Control.JUMP);

    public GrimStrafe() {
        super(StrafeModule.Mode.GRIM);
    }

    /** Before the keyboard is read and before the rotation resolves at the head of the move. */
    @Subscribe(stage = Stage.PRE)
    private void onTickStart(TickEvent event) {
        if (!Players.present() || !ClientSettings.interaction().moveFix()) {
            return;
        }
        double forward = axis(Inputs.Control.FORWARD, Inputs.Control.BACK);
        double strafe = axis(Inputs.Control.LEFT, Inputs.Control.RIGHT);
        if (!MovementMath.isMoving(forward, strafe)) {
            return;
        }

        boolean jumping = Players.onGround() && !Players.inWater()
                && (autoJump.isOn() || Inputs.pressed(Inputs.Control.JUMP));

        Vec2 camera = Players.rotation();
        float heading = MovementMath.movementYaw(camera.getYaw(), forward, strafe);
        // Facing 45 degrees right of the heading turns the keys into W+A, which
        // still walks along the heading. Not on a jump from the ground: the
        // sprint jump's push goes along the yaw, and would go 45 degrees astray.
        float yaw = diagonal.isOn() && slowed() && !jumping ? heading + DIAGONAL_OFFSET : heading;
        Core.rotations().request(this, RotationRequest.at(yaw, camera.getPitch())
                .priority(RotationPriority.LOW)
                .mode(RotationMode.SILENT));

        if (sprint.isOn()) {
            sprintKey.press();
        }
        if (autoJump.isOn() && jumping) {
            jumpKey.press();
        }
    }

    /**
     * @return whether the game cuts the movement input this tick, which is the
     *         only time a diagonal is faster
     *
     * <p>Using an item scales the input to 0.2 and sneaking to the sneak speed,
     * both in {@code ClientPlayerEntity.tickMovement}, and nothing normalises it
     * there. {@code Entity.movementInputToVelocity} only normalises an input
     * longer than 1, so a cut W+A (0.28 long) keeps its length where a cut W
     * (0.2) is shorter: about 41% faster. Uncut, W+A is normalised back to W's
     * speed and the diagonal gains nothing, so the head is left alone.
     */
    private static boolean slowed() {
        return Players.usingItem() || Players.sneaking();
    }

    /** @return what the ArrayList should say: the one thing that stops this working */
    public String status() {
        return ClientSettings.interaction().moveFix() ? getName() : "Move Fix off";
    }

    /** Lets go of the head and any key held; the module calls it when switched off. */
    public void stop() {
        Core.rotations().release(this);
        sprintKey.release();
        jumpKey.release();
    }

    private static double axis(Inputs.Control positive, Inputs.Control negative) {
        return (Inputs.pressed(positive) ? 1d : 0d) - (Inputs.pressed(negative) ? 1d : 0d);
    }
}
