package dev.px.karatsuba.client.module.movement.strafe;

import dev.px.core.Core;
import dev.px.core.event.Stage;
import dev.px.core.event.Subscribe;
import dev.px.core.event.impl.TickEvent;
import dev.px.core.math.Vec2;
import dev.px.core.math.Vec3;
import dev.px.core.movement.rotation.RotationMode;
import dev.px.core.movement.rotation.RotationPriority;
import dev.px.core.movement.rotation.RotationRequest;
import dev.px.core.setting.impl.BooleanSetting;
import dev.px.core.setting.impl.NumberSetting;
import dev.px.core.util.math.MovementMath;
import dev.px.karatsuba.adapter.event.game.player.PlayerMoveEvent;
import dev.px.karatsuba.adapter.game.player.Inputs;
import dev.px.karatsuba.adapter.game.player.Players;
import dev.px.karatsuba.adapter.movement.AttackSlowdown;
import dev.px.karatsuba.adapter.movement.Displacement;
import dev.px.karatsuba.adapter.movement.EntityPush;
import dev.px.karatsuba.client.module.mode.SubMode;
import dev.px.karatsuba.client.module.movement.StrafeModule;
import dev.px.karatsuba.client.module.movement.control.TickKey;
import dev.px.karatsuba.client.service.Services;
import dev.px.karatsuba.client.service.claim.ClaimPriority;
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
 * <h2>Where Grim widens what it accepts</h2>
 * Four extras, each off by default and each staying inside an allowance read
 * from Grim 2.3.73's bytecode (see {@code Grim.md}, "Strafe extras"):
 *
 * <ul>
 *   <li><b>Entity Collide.</b> Grim does not simulate entity pushing. It lets the
 *       starting velocity sit anywhere within 0.08 per pushing entity on X and
 *       on Z, and carries on from whatever fitted. So beside other entities the
 *       push vanilla gave is swapped for the largest one along the heading.</li>
 *   <li><b>Boat Burst.</b> Within reach of a boat or shulker, Grim takes 1.2 off
 *       the Simulation offset for that tick and the three after. The move goes
 *       further by the burst distance, in position only, for three of them.</li>
 *   <li><b>Tap Jump.</b> A held jump is let go mid-air, so vanilla's jump
 *       cooldown is zeroed and the landing jumps at once.</li>
 *   <li><b>Keep Sprint.</b> A sprinting hit keeps the sprint and the speed. Grim
 *       keeps the unslowed velocity as a candidate for 1.21+ clients.</li>
 * </ul>
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

    private final BooleanSetting entityCollide = bool("Entity Collide", false)
            .describe("Beside other entities, take the biggest push Grim allows: 0.08 a tick each");

    private final BooleanSetting boatBurst = bool("Boat Burst", false)
            .describe("Lunge forward for three ticks beside a boat or shulker");

    private final NumberSetting<Double> burstDistance = decimal("Burst Distance", 1d, 0.1d, 1.15d)
            .step(0.05d)
            .visibleWhen(boatBurst::isOn)
            .describe("Extra blocks a tick; Grim forgives up to 1.2 in all");

    private final BooleanSetting tapJump = bool("Tap Jump", false)
            .describe("Let go of a held jump in the air, so the next one comes the moment you land");

    private final BooleanSetting keepSprint = bool("Keep Sprint", false)
            .describe("Keep your sprint and speed when you hit something");

    /** Facing this far right of the heading, the keys that walk along it are W+A exactly. */
    private static final float DIAGONAL_OFFSET = 45f;

    /** How much Grim widens the starting velocity per pushing entity, on X and on Z. */
    private static final double PUSH_PER_ENTITY = 0.08d;

    /** Grim grows the player's box by 0.2 to count pushers; less here, so every one counted is one Grim counts. */
    private static final double PUSHER_GROW = 0.1d;

    /** Stays this fraction inside Grim's allowance, against rounding on either side. */
    private static final double PUSH_MARGIN = 0.95d;

    /** Grim looks 1.0 around the player's box for a boat or shulker; less here, for the same reason. */
    private static final double HARD_COLLIDER_GROW = 0.8d;

    /**
     * Ticks a burst may run, counting the one the boat or shulker was seen in.
     * Grim forgives that tick and the three after; the last is left as margin.
     */
    private static final int BURST_TICKS = 3;

    private final TickKey sprintKey = new TickKey(Inputs.Control.SPRINT);
    private final TickKey jumpKey = new TickKey(Inputs.Control.JUMP);

    /** The way the keys point this tick, or NaN when none are held. Set at the start of the tick. */
    private float heading = Float.NaN;

    /** Ticks since a boat or shulker was within reach; starts out of range. */
    private int sinceHardCollider = BURST_TICKS;

    public GrimStrafe() {
        super(StrafeModule.Mode.GRIM);
        AttackSlowdown.skipWhen(() -> isListening() && keepSprint.isOn());
    }

    /** Before the keyboard is read and before the rotation resolves at the head of the move. */
    @Subscribe(stage = Stage.PRE)
    private void onTickStart(TickEvent event) {
        heading = Float.NaN;
        if (!Players.present() || !ClientSettings.interaction().moveFix()) {
            return;
        }
        if (tapJump.isOn() && tapsJump()) {
            Inputs.letGoOfJump();
        }

        double forward = axis(Inputs.Control.FORWARD, Inputs.Control.BACK);
        double strafe = axis(Inputs.Control.LEFT, Inputs.Control.RIGHT);
        if (!MovementMath.isMoving(forward, strafe)) {
            return;
        }

        boolean jumping = Players.onGround() && !Players.inWater()
                && (autoJump.isOn() || Inputs.pressed(Inputs.Control.JUMP));

        Vec2 camera = Players.rotation();
        heading = MovementMath.movementYaw(camera.getYaw(), forward, strafe);
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
     * At the head of the move, after the keys are read and before {@code travel}.
     * The push since the last move is read every tick, so a later tick never sees
     * an old one.
     */
    @Subscribe(stage = Stage.PRE)
    private void onMove(PlayerMoveEvent event) {
        Vec3 pushed = EntityPush.sinceTravel();
        // Counted standing still too: the burst is timed from the tick it was seen.
        boolean nearHard = boatBurst.isOn() && EntityPush.nearHardCollider(HARD_COLLIDER_GROW);
        sinceHardCollider = nearHard ? 0 : Math.min(sinceHardCollider + 1, BURST_TICKS);

        if (Float.isNaN(heading) || Players.gliding()) {
            return;
        }
        if (boatBurst.isOn() && sinceHardCollider < BURST_TICKS) {
            Displacement.next(MovementMath.forward(heading, burstDistance.getDouble()));
        }
        if (entityCollide.isOn() && pushed != null) {
            collide(event.getVelocity(), pushed);
        }
    }

    /** A burst that the move did not use must not wait for a later one. */
    @Subscribe(stage = Stage.POST)
    private void afterMove(PlayerMoveEvent event) {
        Displacement.cancel();
    }

    /**
     * Swaps the push vanilla gave since the last move for the biggest one along
     * the heading that Grim's widening still covers.
     *
     * <p>Grim's box is per axis, so the push is scaled until its larger axis
     * fills it; it stays on the heading rather than pulling to a corner. Only
     * taken when it beats what vanilla already gave.
     */
    private void collide(Vec3 velocity, Vec3 pushed) {
        int pushers = EntityPush.pushers(PUSHER_GROW);
        if (pushers == 0) {
            return;
        }
        double limit = pushers * PUSH_PER_ENTITY * PUSH_MARGIN;
        Vec3 along = MovementMath.forward(heading, 1d);
        double scale = limit / Math.max(Math.abs(along.getX()), Math.abs(along.getZ()));
        double pushX = along.getX() * scale;
        double pushZ = along.getZ() * scale;
        double gained = pushX * along.getX() + pushZ * along.getZ();
        double given = pushed.getX() * along.getX() + pushed.getZ() * along.getZ();
        if (gained <= given) {
            return;
        }
        Services.motion().setHorizontal(this,
                velocity.getX() - pushed.getX() + pushX,
                velocity.getZ() - pushed.getZ() + pushZ,
                ClaimPriority.NORMAL);
    }

    /**
     * @return whether letting go of jump this tick changes nothing but the
     *         cooldown: it is held, the player is in the air, and jump is not
     *         what swims, climbs or opens an elytra right now
     */
    private static boolean tapsJump() {
        return Inputs.pressed(Inputs.Control.JUMP)
                && !Players.onGround()
                && !Players.inWater()
                && !Players.inLava()
                && !Players.climbing()
                && !Players.canGlide();
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
        Displacement.cancel();
        heading = Float.NaN;
    }

    private static double axis(Inputs.Control positive, Inputs.Control negative) {
        return (Inputs.pressed(positive) ? 1d : 0d) - (Inputs.pressed(negative) ? 1d : 0d);
    }
}
