package dev.px.karatsuba.client.module.movement.elytrafly;

import dev.px.core.Core;
import dev.px.core.event.Priority;
import dev.px.core.event.Stage;
import dev.px.core.event.Subscribe;
import dev.px.core.event.impl.ClientLifecycleEvent;
import dev.px.core.event.impl.TickEvent;
import dev.px.core.event.impl.WorldEvent;
import dev.px.core.math.Vec2;
import dev.px.core.math.Vec3;
import dev.px.core.movement.rotation.RotationMode;
import dev.px.core.movement.rotation.RotationPriority;
import dev.px.core.movement.rotation.RotationRequest;
import dev.px.core.movement.simulation.MovementInput;
import dev.px.core.setting.impl.BooleanSetting;
import dev.px.core.setting.impl.EnumSetting;
import dev.px.core.setting.impl.NumberSetting;
import dev.px.karatsuba.adapter.event.game.network.MovementPacketEvent;
import dev.px.karatsuba.adapter.event.game.player.PlayerJumpEvent;
import dev.px.karatsuba.adapter.event.game.player.ServerTeleportEvent;
import dev.px.karatsuba.adapter.network.PacketHold;
import dev.px.karatsuba.adapter.game.Options;
import dev.px.karatsuba.adapter.game.player.Inputs;
import dev.px.karatsuba.adapter.game.player.Players;
import dev.px.karatsuba.client.module.mode.SubMode;
import dev.px.karatsuba.client.module.movement.ElytraFlyModule;
import dev.px.karatsuba.client.service.claim.ClaimPriority;
import dev.px.karatsuba.client.settings.ClientSettings;

/**
 * Skims the ground on an elytra: land, hop, glide, over and over.
 *
 * <p>Meteor's Bounce mode (Luna's Elytra Recast), with Lambda's Smart Pitch and
 * its trick for keeping the glide through a landing. Forward is held along a
 * heading, the head is pitched for speed, and every landing is a hop straight back
 * into the glide. The speed is the game's own physics; nothing here touches
 * velocity.
 *
 * <h2>Where the speed comes from</h2>
 * The 1.21.4 physics, per tick: a glide keeps 99% of its horizontal speed, plain
 * air 91%, and a tick that starts on the ground 55% (the friction is read before
 * the move, so even a jump pays it). A sprint jump adds 0.2 along the heading.
 * So the fast bounce is a sprint jump made <em>inside</em> the glide, with no
 * ground tick and no plain-air tick anywhere: +0.2 a bounce against 1% a tick,
 * which a model of the formulas puts at 40 blocks a second, about 48 with Smart
 * Pitch. Leaving the glide on every landing, as the first version of this did,
 * caps it near walking pace. Two things make it possible:
 *
 * <ul>
 *   <li><b>Sprint Jump.</b> Vanilla clears sprint on every gliding tick before it
 *       jumps, so the hop is sprinted at the jump itself
 *       ({@link PlayerJumpEvent}); the start-sprint goes out before that tick's
 *       movement, and vanilla ends it the next tick. Grim predicts a sprint jump
 *       inside a glide and accepts it unless a server sets
 *       {@code exploit.allow-sprint-jumping-when-using-elytra} to false
 *       ({@code MovementCheckRunner}). SprintF would object, but it is an
 *       experimental check, and those neither alert nor set back unless a server
 *       turns {@code experimental-checks} on ({@code Check.flag}).</li>
 *   <li><b>Hold Glide.</b> The server ends a glide in its own tick when the last
 *       movement it read was on the ground. So the landing's packet is held
 *       ({@code PacketHold}) and let go together with the hop's: the server reads
 *       both before it ticks the player, never sees them standing, and never ends
 *       the glide. Everything is held in order, the pongs Grim times the client by
 *       included, so to Grim it is a tick of latency and nothing else.</li>
 * </ul>
 *
 * <h2>When the glide ends anyway</h2>
 * With Hold Glide off, or when the two packets straddle a server tick, the server
 * ends the glide. Then the Grim-safe recast applies: hop, let go of jump, and once
 * the last two movement packets were airborne press it again, so vanilla opens the
 * elytra itself (Grim's {@code PacketEntityAction}, ElytraF and ElytraB: two
 * airborne packets and a fresh press). Meteor's own recast sends the take-off the
 * tick you land, with jump held, and is refused for both.
 *
 * <p>Meteor's "stop gliding on a rubberband and re-send the take-off" is left out:
 * ending the glide on the client alone leaves the client and the server
 * simulating two different players. {@code Rubberband Pause} steps back instead.
 */
public final class BounceElytraFly extends SubMode<ElytraFlyModule.Mode> {

    /** Degrees a tick the head turns onto the bounce angle; about three ticks from level. */
    private static final float TURN_SPEED = 30f;

    public enum Heading {
        /** Wherever you look. */
        FACING,
        /** The 45 degree line nearest where you look. */
        NEAREST_45,
        /** A compass yaw you set. */
        FIXED
    }

    private final EnumSetting<Heading> heading = enumOf("Heading", Heading.NEAREST_45)
            .describe("Which way to go: where you look, the nearest 45° line to it (straight down a highway), or Yaw");

    private final NumberSetting<Float> yaw = number("Yaw", 0f, 0f, 359f)
            .step(1f)
            .describe("Compass yaw for Fixed: 0 south, 90 west, 180 north, 270 east")
            .visibleWhen(() -> heading.get() == Heading.FIXED);

    public enum PitchMode {
        /** Straight down while rising, nearly level once falling: the fall becomes speed. */
        SMART,
        /** Always Pitch. */
        FIXED,
        /** Your own. */
        FREE
    }

    private final EnumSetting<PitchMode> pitchMode = enumOf("Pitch Mode", PitchMode.SMART)
            .describe("Smart turns each fall into forward speed (fastest); Fixed holds Pitch; Free leaves it to you");

    private final NumberSetting<Float> pitch = number("Pitch", 72f, 0f, 90f)
            .step(1f)
            .describe("Degrees to look down while bouncing")
            .visibleWhen(() -> pitchMode.get() == PitchMode.FIXED);

    private final BooleanSetting freeLook = bool("Free Look", true)
            .describe("Leave your camera free; only the server sees the bounce angle. Needs Move Fix");

    private final BooleanSetting sprintJump = bool("Sprint Jump", true)
            .describe("Sprint each hop inside the glide, +0.2 a bounce; off for a server that disallows elytra sprint jumps");

    private final BooleanSetting holdGlide = bool("Hold Glide", true)
            .describe("Send each landing together with its hop, so the server never ends the glide; the biggest speed gain");

    private final BooleanSetting autoTakeOff = bool("Auto Take Off", true)
            .describe("Jump and open the elytra for you; off waits until you have opened it yourself");

    private final NumberSetting<Integer> rubberbandPause = integer("Rubberband Pause", 7, 0, 20)
            .describe("Ticks to let go after the server pulls you back, before bouncing again");

    private final BooleanSetting steadyFov = bool("Steady FOV", true)
            .describe("Stop the view pulsing each time sprint starts and stops; your FOV Effects come back when you stop");

    /** The on-ground flag of the last movement packet sent, and of the one before: what Grim checks a take-off against. */
    private boolean sentOnGround = true;
    private boolean sentOnGroundBefore = true;

    /** Ticks left of a rubberband pause. */
    private int pause;

    /** Whether the player has opened the elytra since it last became unusable; what Auto Take Off off waits for. */
    private boolean opened;

    /** The player's FOV Effects while borrowed, or -1. */
    private double borrowedFov = -1d;

    /** Horizontal blocks per tick, smoothed over about a second; and where the last tick started. */
    private double speed;
    private Vec3 lastPosition;

    /** Whether this mode started the packet hold, and whether the hop's packet is in it so it can go. */
    private boolean holding;
    private boolean releaseAtTickEnd;

    public BounceElytraFly() {
        super(ElytraFlyModule.Mode.BOUNCE);
    }

    @Subscribe(stage = Stage.PRE)
    private void onTick(TickEvent event) {
        measure();
        if (!Players.present() || !Players.canGlide()) {
            // No elytra, in water, climbing, riding: nothing to fly. Vanilla would
            // not open it either, and Grim cancels a take-off for each of these.
            opened = false;
            giveBack();
            return;
        }
        if (Players.gliding()) {
            opened = true;
        }
        if (!autoTakeOff.isOn() && !opened) {
            return;
        }
        if (pause > 0) {
            pause--;
            giveBack();
            return;
        }
        if (steadyFov.isOn()) {
            borrowFov();
        } else {
            giveBack();
        }

        float course = course();
        aim(course);
        Core.controls().move(this, MovementInput.forward(course)
                        .withJump(jump())
                        // Hunger is the one sprint rule vanilla leaves to the
                        // caller here (Grim SprintA). Gliding, vanilla drops it.
                        .withSprint(Players.canSprint()),
                ClaimPriority.NORMAL);
    }

    /**
     * Jump for this tick.
     *
     * <ul>
     *   <li>On the ground: held, so the game hops the first tick you touch down.
     *       Still gliding, the hop stays in the glide (and is sprinted, see
     *       {@link #onJump}); the glide over, it is a plain hop.</li>
     *   <li>Gliding in the air: let go, so the jump cooldown resets and any later
     *       press is a new one.</li>
     *   <li>Falling, not gliding, which only happens when the server ended the
     *       glide: pressed, once jump was let go in the last input packet and the
     *       last two movement packets were airborne, the earliest Grim accepts.
     *       The game opens the elytra on that press.</li>
     * </ul>
     */
    private boolean jump() {
        if (Players.onGround()) {
            return true;
        }
        if (Players.gliding()) {
            return false;
        }
        return !Inputs.reportedJump() && !sentOnGround && !sentOnGroundBefore;
    }

    /** @return the yaw to bounce along */
    private float course() {
        float facing = Players.rotation().getYaw();
        return switch (heading.get()) {
            case FACING -> facing;
            case NEAREST_45 -> Math.round(facing / 45f) * 45f;
            case FIXED -> yaw.getFloat();
        };
    }

    /**
     * Turns the head onto the bounce angle.
     *
     * <p>Free Look holds it silently, which only works with Move Fix: that is what
     * makes the silent angle the one the glide is simulated with. Without Move Fix
     * the camera turns instead, since a glide flown by one angle and reported at
     * another is a simulation failure.
     */
    private void aim(float course) {
        boolean silent = freeLook.isOn() && ClientSettings.interaction().moveFix();
        PitchMode mode = pitchMode.get();
        if (mode == PitchMode.FREE && heading.get() == Heading.FACING && !silent) {
            return;
        }
        float look = switch (mode) {
            case SMART -> smartPitch();
            case FIXED -> pitch.getFloat();
            case FREE -> Players.rotation().getPitch();
        };
        // HIGH: while bouncing, the angle is the flight, and an aura turning the
        // head mid-glide would steer it into the ground.
        Core.rotations().request(this, RotationRequest.at(Vec2.rotation(course, look))
                .priority(RotationPriority.HIGH)
                // Smart flips between two angles twice a bounce, and a flip that
                // takes three ticks is a flip that missed the fall it was for.
                .step(mode == PitchMode.SMART ? RotationRequest.SNAP : TURN_SPEED)
                .mode(silent ? RotationMode.SILENT : RotationMode.CLIENT));
    }

    /**
     * Lambda's smart pitch: straight down while rising or barely falling, so the
     * hop comes back down as fast as it can; 4&deg; once falling faster than 0.2 a
     * tick. Near level, the glide turns a tenth of the fall speed into forward
     * speed every tick ({@code calcGlidingVelocity}: {@code vy * -0.1 * cos²(pitch)}),
     * where 85&deg; turns under a hundredth of it. Worth about 15% over any fixed
     * pitch in a model of the 1.21.4 formulas.
     */
    private static float smartPitch() {
        return Players.velocity().getY() > -0.2d ? 90f : 4f;
    }

    /**
     * Counts what was actually sent. Lowest, so it sees the flag after anything
     * that changes it, and not at all for a packet something cancelled. Always
     * on, so the record is current the moment the mode starts.
     */
    @Subscribe(priority = Priority.LOWEST, ignoreListening = true)
    private void onMovementPacket(MovementPacketEvent event) {
        sentOnGroundBefore = sentOnGround;
        sentOnGround = event.isOnGround();

        // Hold Glide: hold from the landing's packet (this one, which the hold
        // takes as soon as this returns) to the hop's, then let both go together.
        if (!isListening() || !holdGlide.isOn()) {
            return;
        }
        if (event.isOnGround() && Players.gliding() && pause == 0) {
            PacketHold.hold();
            holding = true;
        } else if (holding && !event.isOnGround()) {
            releaseAtTickEnd = true;
        }
    }

    /** End of the client tick: the hop's packet is in the hold, so everything goes now. */
    @Subscribe(stage = Stage.POST, ignoreListening = true)
    private void onTickEnd(TickEvent event) {
        // Also when the glide is over or the mode stopped: holding then only adds lag.
        if (holding && (releaseAtTickEnd || !isListening() || !Players.gliding())) {
            letGo();
        }
    }

    /**
     * Sprints the hop that leaves the ground inside a glide.
     *
     * <p>At the jump itself, after vanilla has cleared sprint for the glide this
     * tick and before the boost is worked out, so the jump carries +0.2 along the
     * heading. The game tells the server about the sprint before this tick's
     * movement, so Grim predicts the same jump; vanilla stops it again next tick.
     */
    @Subscribe
    private void onJump(PlayerJumpEvent event) {
        if (sprintJump.isOn() && Players.gliding() && Players.canSprint() && pause == 0) {
            Players.setSprinting(true);
        }
    }

    private void letGo() {
        holding = false;
        releaseAtTickEnd = false;
        PacketHold.release();
    }

    @Subscribe
    private void onTeleport(ServerTeleportEvent event) {
        if (!event.isSpawn()) {
            pause = rubberbandPause.getInt();
            lastPosition = null;
            if (holding) {
                letGo();
            }
        }
    }

    private void measure() {
        Vec3 now = Players.present() ? Players.position() : null;
        if (now != null && lastPosition != null) {
            double dx = now.getX() - lastPosition.getX();
            double dz = now.getZ() - lastPosition.getZ();
            speed += (Math.sqrt(dx * dx + dz * dz) - speed) * 0.1d;
        }
        lastPosition = now;
    }

    /** @return the ArrayList readout: speed in blocks a second */
    public String status() {
        return String.format("%.1f b/s", speed * 20d);
    }

    @Subscribe(ignoreListening = true)
    private void onWorld(WorldEvent event) {
        pause = 0;
        opened = false;
        stop();
    }

    @Subscribe(ignoreListening = true)
    private void onLifecycle(ClientLifecycleEvent event) {
        if (event.getPhase() == ClientLifecycleEvent.Phase.STOPPING) {
            giveBack();
        }
    }

    @Override
    protected void onSelected() {
        pause = 0;
        opened = false;
    }

    private void borrowFov() {
        if (borrowedFov < 0d) {
            borrowedFov = Options.fovEffectScale();
            Options.fovEffectScale(0d);
        }
    }

    /** Lets go of everything this mode holds: packets and the borrowed FOV Effects. Safe to call any time. */
    public void stop() {
        if (holding) {
            letGo();
        }
        giveBack();
    }

    /** Puts the player's FOV Effects back, if borrowed. Safe to call any time. */
    public void giveBack() {
        if (borrowedFov >= 0d) {
            Options.fovEffectScale(borrowedFov);
            borrowedFov = -1d;
        }
    }
}
