package dev.px.karatsuba.adapter.movement;

import dev.px.core.Core;
import dev.px.core.event.Stage;
import dev.px.core.event.impl.MotionUpdateEvent;
import dev.px.core.event.impl.WorldEvent;
import dev.px.core.math.Vec2;
import dev.px.core.math.Vec3;
import dev.px.core.movement.simulation.MotionState;
import dev.px.core.movement.simulation.MovementInput;
import dev.px.core.util.math.PhysicsProfile;
import dev.px.karatsuba.adapter.event.Events;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.util.PlayerInput;
import net.minecraft.util.math.Vec3d;

/**
 * The local player's movement, handed to Core once a tick.
 *
 * <p>Three of Core's consumers want the same moment &mdash; the point just
 * before the client reports its position, when this tick's movement is done
 * and nothing has been sent yet:
 *
 * <ul>
 *   <li>{@link MotionUpdateEvent}, PRE and POST around the movement packets,
 *       which is what the timeline recorder writes down as what the client
 *       believed next to what it told the server.</li>
 *   <li>{@code SimulationService.observe}, which replays last tick through
 *       Core's simulation and scores it against where the game actually put the
 *       player. That is what makes {@code isReliable()} mean something.</li>
 *   <li>The simulation's {@link PhysicsProfile}, rebuilt from the player's
 *       attributes, so Speed, Slow Falling, Jump Boost, a step-height change or
 *       a crouched hitbox reach every prediction without a module having to
 *       know about any of them.</li>
 * </ul>
 *
 * <h2>Two inputs, on purpose</h2>
 * What the player <em>pressed</em> and what the movement code <em>used</em> are
 * different numbers. The game scales the keys for sneaking and for using an
 * item, and then by 0.98. So the event carries the keys as held, and the drift
 * monitor is fed the game's own {@code forwardSpeed} / {@code sidewaysSpeed},
 * read back after the game has finished with them. Scoring against the pressed
 * keys would measure a gap this adapter can simply close. Core (2.2+) models the
 * 0.98 itself, as {@code PhysicsProfile.inputScale}, so that one step is divided
 * back out of what it is fed; left in, every tick would be damped twice and read
 * as two percent of drift.
 *
 * <h2>Other entities</h2>
 * Nothing here. {@code Core.prediction()} reads where they have been from
 * {@code Core.entities()}' own trackers, so there is no per-entity feed.
 *
 * <h2>When the movement tick did not run</h2>
 * A module that cancels the movement tick (Free Cam does) leaves nothing to
 * score: the player did not move by the rules Core models, they did not move at
 * all. The drift history is reset rather than fed a false sample, so the first
 * real tick afterwards starts a fresh comparison instead of one against a state
 * from before the freeze.
 *
 * <p><b>Game thread only</b>, like the mixin that calls it.
 */
public final class MotionBridge {

    /** The keys as pressed this tick, for the event. */
    private static MovementInput held;

    /** What the movement code consumed this tick, for the drift monitor. */
    private static MovementInput effective;

    /** The physics that applied during this tick's move. */
    private static PhysicsProfile profile;

    /** Whether the movement tick ran to the end this tick. */
    private static boolean moved;

    /** Whether the game actually jumped this tick, rather than the key merely being down. */
    private static boolean jumped;

    /** What the last PRE reported, so the next one can say what it was. */
    private static Vec2 lastReported;

    private MotionBridge() {
    }

    // ------------------------------------------------------ movement tick

    /** Start of {@code tickMovement}. */
    public static void beforeMovementTick() {
        moved = false;
        jumped = false;
    }

    /** From {@code LivingEntity.jump}, for the local player, once the jump has happened. */
    public static void jumped() {
        jumped = true;
    }

    /**
     * End of {@code tickMovement}: this tick's move is done, and the game's
     * inputs for it are still in their fields.
     */
    public static void afterMovementTick(ClientPlayerEntity self) {
        if (!Events.isArmed()) {
            return;
        }
        // The yaw the move was made along. A CLIENT rotation claim is written
        // later, at the movement packets, and reaches the movement code next tick.
        float yaw = self.getYaw();
        PlayerInput keys = self.input.playerInput;

        held = MovementInput.of(yaw, axis(keys.forward(), keys.backward()), axis(keys.left(), keys.right()))
                .withJump(keys.jump())
                .withSneak(keys.sneak())
                .withSprint(keys.sprint());

        // Already scaled for sneaking, so sneak stays off here: Core would scale
        // it a second time. Already scaled by 0.98 too, which Core applies
        // itself (PhysicsProfile.inputScale), so that is divided back out.
        double unscale = 1d / PhysicsProfile.vanilla().getInputScale();
        effective = MovementInput.of(yaw, self.forwardSpeed * unscale, self.sidewaysSpeed * unscale)
                .withJump(jumped)
                .withSprint(self.isSprinting());

        profile = profileOf(self);
        moved = true;
    }

    // --------------------------------------------------- movement packets

    /** Start of {@code sendMovementPackets}, after the tick's rotation is in place. */
    public static void beforeMovementPackets(ClientPlayerEntity self) {
        if (!Events.isArmed()) {
            return;
        }
        MotionState state = stateOf(self);

        if (moved) {
            Core.simulation().setProfile(profile);
            Core.simulation().observe(state, effective);
        } else {
            Core.simulation().getDrift().reset();
        }
        moved = false;

        Events.post(motionEvent(Stage.PRE, self, state));
    }

    /** End of {@code sendMovementPackets}, before a silent rotation is put back. */
    public static void afterMovementPackets(ClientPlayerEntity self) {
        if (!Events.isArmed()) {
            return;
        }
        MotionUpdateEvent event = Events.post(motionEvent(Stage.POST, self, stateOf(self)));
        lastReported = event.getRotation();
    }

    /**
     * @return the rotation the last movement packets carried, silent rotations
     *         included; null before any were sent in this world
     *
     * <p>Read at the end of {@code sendMovementPackets}, while a silent rotation
     * is still in place, and after every handler has had its say. A packet that
     * carried no rotation leaves the server with the one before, which is also
     * what this keeps.
     */
    public static Vec2 lastSentRotation() {
        return lastReported;
    }

    /**
     * Resets on a world change: the last position observed was in another world,
     * so neither this tick's state nor the drift history carries over.
     */
    public static void install(Core core) {
        core.getBus().on(WorldEvent.class, event -> {
            core.getSimulationService().getDrift().reset();
            reset();
        });
    }

    /** Forgets the last tick. Called on a world change, where nothing carries over. */
    public static void reset() {
        held = null;
        effective = null;
        profile = null;
        moved = false;
        jumped = false;
        lastReported = null;
    }

    // ------------------------------------------------------------ helpers

    private static MotionUpdateEvent motionEvent(Stage stage, ClientPlayerEntity self, MotionState state) {
        return new MotionUpdateEvent(stage, state, held,
                self.isSprinting(), self.isSneaking(),
                Vec2.rotation(self.getYaw(), self.getPitch()),
                lastReported);
    }

    private static MotionState stateOf(ClientPlayerEntity self) {
        Vec3d velocity = self.getVelocity();
        return MotionState.of(
                Vec3.of(self.getX(), self.getY(), self.getZ()),
                Vec3.of(velocity.x, velocity.y, velocity.z),
                self.isOnGround());
    }

    /** The player's physics as the game has them this tick. See {@link EntityRules#read}. */
    private static PhysicsProfile profileOf(ClientPlayerEntity self) {
        return EntityRules.read(self, PhysicsProfile.vanilla())
                .withHitbox(self.getWidth(), self.getHeight());
    }

    private static double axis(boolean positive, boolean negative) {
        return (positive ? 1d : 0d) - (negative ? 1d : 0d);
    }
}
