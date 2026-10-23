package dev.px.karatsuba.client.module.render;

import dev.px.core.event.Stage;
import dev.px.core.event.Subscribe;
import dev.px.core.event.impl.TickEvent;
import dev.px.core.math.Vec2;
import dev.px.core.math.Vec3;
import dev.px.karatsuba.client.module.ClientModule;
import dev.px.core.module.Module;
import dev.px.core.module.ModuleInfo;
import dev.px.core.setting.impl.BooleanSetting;
import dev.px.core.setting.impl.NumberSetting;
import dev.px.karatsuba.adapter.event.game.render.CameraEvent;
import dev.px.karatsuba.adapter.event.game.player.PlayerJumpEvent;
import dev.px.karatsuba.adapter.event.game.player.PlayerMoveEvent;
import dev.px.core.util.math.RotationMath;
import dev.px.karatsuba.adapter.event.game.player.LookEvent;
import dev.px.karatsuba.adapter.game.entity.FakePlayers;
import dev.px.karatsuba.adapter.game.player.Inputs;
import dev.px.karatsuba.adapter.game.player.Players;

/**
 * Detaches the camera and flies it around while the body stays put.
 *
 * <p>The worked example for <b>three game events cooperating</b>, and for the
 * difference between moving the camera and moving the player:
 *
 * <ul>
 *   <li>{@link CameraEvent} moves the view. That is the whole visible effect, and
 *       on its own it is all this module would need &mdash; except that the
 *       player would carry on walking underneath it.</li>
 *   <li>{@link PlayerMoveEvent} is cancelled, which freezes the body. The
 *       movement tick is skipped entirely, so held keys do not accumulate
 *       velocity and the server is told nothing new.</li>
 *   <li>{@link PlayerJumpEvent} is cancelled, because the jump does not go
 *       through the movement tick and would otherwise still launch the body.</li>
 *   <li>{@link LookEvent} is cancelled and the turn kept for the camera, which
 *       has its own look. The player's head stays where it was, so the server is
 *       not sent a head swinging round with the mouse.</li>
 * </ul>
 *
 * <p><b>Client-side only, and it looks it.</b> Nothing here is sent anywhere: the
 * server's idea of where the player is never changes, which is exactly why the
 * body has to be frozen rather than moved. Chunks outside render distance of the
 * real position are not loaded, so flying far enough shows the void.
 *
 * <h2>Why the camera moves on wall-clock time</h2>
 * The obvious place to move it is the tick, and the obvious result is a camera
 * that visibly steps twenty times a second while the world around it renders at
 * two hundred. So the step is taken in the camera event, scaled by how long since
 * the last frame &mdash; which makes the speed setting mean blocks per second at
 * any frame rate.
 */
@ModuleInfo(name = "Free Cam", description = "Flies the camera while the player stays put",
        category = "Render")
public final class FreeCamModule extends ClientModule {

    private static final double NANOS_PER_SECOND = 1_000_000_000d;

    /** A frame longer than this is a stutter, not a movement; clamped so the camera never jumps. */
    private static final double MAX_FRAME_SECONDS = 0.1d;

    private final NumberSetting<Double> speed = decimal("Speed", 8d, 1d, 40d)
            .describe("Blocks per second");

    private final NumberSetting<Double> verticalSpeed = decimal("Vertical Speed", 6d, 1d, 40d)
            .describe("Blocks per second for jump and sneak");

    private final BooleanSetting freezeBody = bool("Freeze Body", true)
            .describe("Stop the player moving while the camera is away; off lets them walk blind");

    private final BooleanSetting showBody = bool("Show Body", true)
            .describe("Draw yourself where you stand, so you can see yourself from the camera");

    /** Where the detached camera is. Null while the module is off. */
    private Vec3 position;

    /** Where the detached camera looks; the mouse turns this, not the player. */
    private float yaw;
    private float pitch;

    /** The body double's entity id, or {@link FakePlayers#NO_ENTITY} when there is none. */
    private int body = FakePlayers.NO_ENTITY;

    /** Frame clock for the wall-clock step. Zero on the first frame after enabling. */
    private long lastFrameNanos;

    @Override
    protected void onEnable() {
        // Starts at the eyes rather than the feet, so switching on does not drop
        // the view to the floor before the first keypress.
        position = Players.eyePosition();
        Vec2 look = Players.rotation();
        yaw = look.getYaw();
        pitch = look.getPitch();
        lastFrameNanos = 0L;
    }

    /** The mouse turns the camera instead of the player, as the game would have turned the player. */
    @Subscribe
    private void onLook(LookEvent event) {
        if (position == null) {
            return;
        }
        event.cancel();
        yaw += event.getDeltaYaw();
        pitch = RotationMath.clampPitch(pitch + event.getDeltaPitch());
    }

    @Override
    protected void onDisable() {
        position = null;
        lastFrameNanos = 0L;
        removeBody();
    }

    // ------------------------------------------------------------------- body

    /**
     * Keeps the body double where the player is, as they are.
     *
     * <p>In first person the game never draws the player, and the camera being
     * elsewhere does not change that, so without it there is nothing to see where
     * you stand. Spawned on the first tick rather than on enabling, and again if a
     * world change took it.
     */
    @Subscribe(stage = Stage.POST)
    private void onTick(TickEvent event) {
        if (!showBody.isOn() || !Players.present()) {
            removeBody();
            return;
        }
        if (!FakePlayers.exists(body)) {
            body = FakePlayers.spawnBodyDouble();
        }
        FakePlayers.mirrorSelf(body);
    }

    private void removeBody() {
        FakePlayers.remove(body);
        body = FakePlayers.NO_ENTITY;
    }

    // --------------------------------------------------------------- freezing

    /**
     * Freezes the body.
     *
     * <p>Cancelling PRE skips the movement tick outright: no input is read, no
     * velocity accumulates and no gravity is applied. Setting the velocity to zero
     * instead would leave the player falling, because gravity is applied inside the
     * tick that is being left to run.
     */
    @Subscribe(stage = Stage.PRE)
    private void onMove(PlayerMoveEvent event) {
        if (freezeBody.isOn()) {
            event.cancel();
        }
    }

    /** The jump does not go through the movement tick, so it needs cancelling too. */
    @Subscribe
    private void onJump(PlayerJumpEvent event) {
        if (freezeBody.isOn()) {
            event.cancel();
        }
    }

    // ----------------------------------------------------------------- camera

    @Subscribe
    private void onCamera(CameraEvent event) {
        if (position == null) {
            return;
        }
        // In third person the game draws the player itself; the double would sit inside them.
        FakePlayers.setShown(body, !event.isThirdPerson());
        position = position.add(step(secondsSinceLastFrame()));
        event.setPosition(position);
        event.setRotation(Vec2.rotation(yaw, pitch));
    }

    /**
     * How far to travel this frame, in world space.
     *
     * <p>The horizontal part is rotated by the camera's own yaw, so forward means
     * where the camera is looking rather than north. Pitch is deliberately
     * ignored: a camera that dives when you look down
     * is much harder to fly than one where up and down are their own keys.
     */
    private Vec3 step(double seconds) {
        double forward = axis(Inputs.Control.FORWARD, Inputs.Control.BACK);
        double strafe = axis(Inputs.Control.RIGHT, Inputs.Control.LEFT);
        double vertical = axis(Inputs.Control.JUMP, Inputs.Control.SNEAK);

        Vec3 lift = Vec3.of(0d, vertical * verticalSpeed.getDouble() * seconds, 0d);
        if (forward == 0d && strafe == 0d) {
            return lift;
        }

        // Normalised, so holding two keys is not faster than holding one.
        double length = Math.sqrt(forward * forward + strafe * strafe);
        double distance = speed.getDouble() * seconds / length;

        double heading = Math.toRadians(yaw);
        double sin = Math.sin(heading);
        double cos = Math.cos(heading);

        // The game's yaw 0 faces south (+Z): forward is (-sin, cos) and the
        // player's right is (-cos, -sin), so at yaw 0 right is west (-X).
        return lift.add(
                (-forward * sin - strafe * cos) * distance,
                0d,
                (forward * cos - strafe * sin) * distance);
    }

    /** @return 1, -1 or 0, for a pair of opposed controls */
    private static double axis(Inputs.Control positive, Inputs.Control negative) {
        int value = (Inputs.pressed(positive) ? 1 : 0) - (Inputs.pressed(negative) ? 1 : 0);
        return value;
    }

    /**
     * @return seconds since the last camera frame, clamped
     *
     * <p>Clamped because the first frame after enabling, and any frame after a
     * chunk-loading stall, would otherwise translate a long pause into one large
     * jump. Losing a little travel during a stutter is much less noticeable than
     * teleporting.
     */
    private double secondsSinceLastFrame() {
        long now = System.nanoTime();
        long previous = lastFrameNanos;
        lastFrameNanos = now;

        if (previous == 0L) {
            return 0d;
        }
        return Math.min((now - previous) / NANOS_PER_SECOND, MAX_FRAME_SECONDS);
    }

    @Override
    public String getDisplayInfo() {
        return speed.displayValue();
    }
}
