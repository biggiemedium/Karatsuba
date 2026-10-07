package dev.px.karatsuba.adapter.game.player;

import dev.px.core.Core;
import dev.px.core.math.Vec2;
import dev.px.core.movement.MovementCorrection;
import dev.px.core.movement.rotation.RotationMode;
import dev.px.core.movement.rotation.RotationService;
import dev.px.core.movement.rotation.RotationSink;
import dev.px.karatsuba.adapter.event.Events;
import dev.px.karatsuba.adapter.event.game.player.RotationClaimEvent;
import dev.px.karatsuba.adapter.game.Options;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.input.Input;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.util.PlayerInput;

import java.util.function.BooleanSupplier;

/**
 * How this game writes the player's rotation.
 *
 * <p>Core's {@link RotationService} decides <em>what</em> the rotation should be
 * &mdash; which module holds it, how far the turn steps this tick, how it eases
 * back when the last claim expires. This decides <em>how</em> it is written,
 * which is the only part of the whole arrangement that needs Minecraft.
 *
 * <h2>Two modes, two mechanisms</h2>
 * <ul>
 *   <li>{@link RotationMode#CLIENT} assigns the player's yaw and pitch. The
 *       camera turns, the movement code sees the new yaw, and the outgoing
 *       packets carry it because the game noticed the fields changed. Nothing
 *       here is doing anything clever.</li>
 *   <li>{@link RotationMode#SILENT} writes the same values, but only for the
 *       duration of the game's own packet-building call, and puts the player's
 *       real rotation back before anything draws. The server is told the new
 *       rotation; the camera never moves.</li>
 * </ul>
 *
 * <h2>Why SILENT stashes rather than editing the packet</h2>
 * The tempting implementation is to catch {@code PlayerMoveC2SPacket} on the way
 * out and overwrite its yaw. It does not work, and the reason is worth knowing:
 * the game picks <em>which</em> movement packet to send by comparing the current
 * rotation against the last one it sent. A rotation the game does not know about
 * is a rotation it sees no change in, so on most ticks it sends the
 * position-only packet &mdash; which has no yaw field to overwrite, and the
 * server keeps whatever it was last told.
 *
 * <p>Setting the fields before that decision and restoring them after lets the
 * game make its ordinary choice with the rotation we want, which is both simpler
 * and correct on every packet variant.
 *
 * <p><b>Game thread only.</b> All of this runs inside the player's tick.
 */
public final class Rotations {

    private static final Sink SINK = new Sink();

    /**
     * The rotation to send this tick, or null.
     *
     * <p>Set by the sink during {@code apply()} and cleared once the packets have
     * gone, so a stale value cannot leak into a tick where nothing claimed the
     * rotation. Plain field rather than volatile: written and read on the game
     * thread, within one call.
     */
    private static Vec2 pendingSilent;

    private static boolean stashed;
    private static float stashedYaw;
    private static float stashedPitch;

    /** Whether this tick's rotation has been resolved: once a tick, by whichever hook comes first. */
    private static boolean resolved;

    /** Whether silent rotations steer the movement too, with the keys corrected. See {@link #movementCorrection}. */
    private static BooleanSupplier correction = () -> true;

    /** Last value handed to Core, so an unchanged slider does not invalidate its cache. */
    private static double appliedSensitivity = Double.NaN;

    private Rotations() {
    }

    /** Installs the sink. Called once, from {@code MinecraftAdapter.install}. */
    public static void install(Core core) {
        core.getRotationService().setSink(SINK);
    }

    /**
     * Resolves the tick's rotation before the game moves the player, and with
     * movement correction on, puts a silent rotation in place for the move too.
     *
     * <p>Called at the head of {@code tickMovement}. A server that simulates
     * movement &mdash; Grim does, every tick &mdash; works out where the player
     * should be from the keys they reported and the yaw in their movement packet.
     * The game moves the player by the yaw it has <em>during</em> the move. So the
     * rotation has to be in place before the move, not just before the packet:
     * otherwise the player walks by one yaw and reports another, and every tick
     * spent walking while a silent rotation is held is a simulation failure.
     *
     * <ul>
     *   <li>{@link RotationMode#CLIENT}: the yaw is assigned here, before the move,
     *       so the camera, the walk and the packet all agree this tick. The keys
     *       are the player's own, relative to where they now look.</li>
     *   <li>{@link RotationMode#SILENT} with correction on: the silent yaw stands
     *       in for the camera's through the move and the packets, and
     *       {@link #correctInput} turns the held keys into the keys that walk the
     *       same way at that yaw. The camera's is put back after the packets.</li>
     *   <li>{@link RotationMode#SILENT} with correction off: swapped in only around
     *       the packets, as before &mdash; the player walks where they steer, and a
     *       simulating anticheat sees the mismatch.</li>
     * </ul>
     *
     * <p>Claims made after this point apply next tick. {@code RotationClaimEvent}
     * is posted here, so anything aiming claims in time, from where the player
     * stands before this tick's move.
     */
    public static void beforeMovementTick() {
        resolve();
        if (correction.getAsBoolean()) {
            swapInSilent();
        }
    }

    /**
     * Rewrites the keys just read so they walk where the player is steering at the
     * yaw the game will use, while a silent rotation stands in for the camera.
     *
     * <p>Called straight after the game reads the keyboard ({@code Input.tick}),
     * before it scales the keys for sneaking or using an item, decides on
     * sprinting, or sends them to the server. The keys themselves are rewritten,
     * not only the movement numbers: since 1.21.2 the client reports which keys
     * it holds ({@code PlayerInputC2SPacket}), and Grim narrows the inputs it
     * simulates to those keys, so the two must agree. Everything after this
     * &mdash; the scaling, sprinting, the input packet &mdash; then follows from
     * the rewritten keys exactly as it would from a real keyboard.
     *
     * <p>{@code MovementCorrection.Mode.STRICT}: only the eight directions a
     * keyboard has, because Grim tries only those (-1, 0 or 1 on each axis). The
     * cost is up to 22.5&deg; of drift from where the player steered.
     */
    public static void correctInput(Input input) {
        ClientPlayerEntity self = player();
        if (!stashed || self == null || input == null) {
            return;
        }
        float applied = self.getYaw();
        if (!MovementCorrection.isNeeded(stashedYaw, applied)) {
            return;
        }
        PlayerInput keys = input.playerInput;
        float forward = axis(keys.forward(), keys.backward());
        float strafe = axis(keys.left(), keys.right());
        MovementCorrection.Input fixed = MovementCorrection.correct(stashedYaw, forward, strafe, applied);

        input.playerInput = new PlayerInput(
                fixed.getForward() > 0f, fixed.getForward() < 0f,
                fixed.getStrafe() > 0f, fixed.getStrafe() < 0f,
                keys.jump(), keys.sneak(), keys.sprint());
        // What KeyboardInput.tick derives from those keys: -1, 0 or 1 each.
        input.movementForward = fixed.getForward();
        input.movementSideways = fixed.getStrafe();
    }

    /**
     * Resolves the rotation if the movement tick did not, and puts a silent one
     * in place for the packets.
     *
     * <p>The movement tick is skipped when a module cancels it (Free Cam does);
     * the packets still need a rotation.
     */
    public static void beforeMovementPackets() {
        resolve();
        swapInSilent();
    }

    /**
     * Puts the player's real rotation back once the packets have gone.
     *
     * <p>The pending value is dropped either way: a claim lasts one tick unless
     * renewed, and a rotation that outlived its claim is exactly the bug the lease
     * model exists to prevent.
     */
    public static void afterMovementPackets() {
        pendingSilent = null;
        restore();
    }

    /**
     * The end of the player's tick: anything still swapped in is put back, and
     * the next tick resolves afresh. A tick whose packets never went out would
     * otherwise leave the camera on the silent rotation.
     */
    public static void endOfTick() {
        pendingSilent = null;
        restore();
        resolved = false;
    }

    /**
     * Whether a silent rotation is applied to the movement as well as the packets,
     * with the keys corrected to match. On unless switched off: a server that
     * simulates movement flags every tick walked under a silent rotation without it.
     */
    public static void movementCorrection(BooleanSupplier on) {
        correction = on == null ? () -> true : on;
    }

    /** Resolves the tick's rotation once: claims, then Core writes it through the sink. */
    private static void resolve() {
        if (!Events.isArmed() || resolved) {
            return;
        }
        resolved = true;
        syncSensitivity();

        // Last call: anything that aims claims here.
        Events.post(new RotationClaimEvent());

        // Writes through the sink: CLIENT assigns the fields, SILENT parks the
        // value below. Does nothing at all when no module holds the rotation.
        Core.rotations().apply();
    }

    /** Stands a pending silent rotation in for the camera's, remembering the camera's. */
    private static void swapInSilent() {
        Vec2 silent = pendingSilent;
        ClientPlayerEntity self = player();
        if (silent == null || self == null || stashed) {
            return;
        }
        stashedYaw = self.getYaw();
        stashedPitch = self.getPitch();
        stashed = true;

        self.setYaw(silent.getYaw());
        self.setPitch(silent.getPitch());
    }

    private static void restore() {
        if (!stashed) {
            return;
        }
        stashed = false;
        ClientPlayerEntity self = player();
        if (self != null) {
            self.setYaw(stashedYaw);
            self.setPitch(stashedPitch);
        }
    }

    private static float axis(boolean positive, boolean negative) {
        return (positive ? 1f : 0f) - (negative ? 1f : 0f);
    }

    /**
     * Keeps Core's sensitivity snapping in step with the game's slider.
     *
     * <p>Optional in Core and off until supplied, because it cannot read the
     * game's options. Supplying it turns snapping on, which is the point: a
     * resolved rotation is then rounded onto the grid of turns the player's own
     * mouse could produce, rather than landing on angles no mouse can hit.
     *
     * <p>Only pushed when the value actually changed. The setter invalidates the
     * service's resolved rotation, and doing that every tick would defeat the
     * lazy caching that makes reading the rotation cheap.
     */
    private static void syncSensitivity() {
        double current = Options.mouseSensitivity();
        if (current == appliedSensitivity) {
            return;
        }
        appliedSensitivity = current;
        Core.rotations().setSensitivity((float) current);
    }

    private static ClientPlayerEntity player() {
        return MinecraftClient.getInstance().player;
    }

    /**
     * The seam itself: two methods, and neither decides anything.
     *
     * <p>Nested because it has no independent life &mdash; it is installed once,
     * never held by anything else, and every piece of state it touches belongs to
     * the enclosing class.
     */
    private static final class Sink implements RotationSink {

        /**
         * @return where the player is actually looking
         *
         * <p>The live fields, not the last thing sent. In CLIENT mode that is what
         * this sink just wrote; in SILENT mode it is untouched, which is exactly
         * what makes the easing on release return the camera to the player rather
         * than to a rotation they never saw.
         */
        @Override
        public Vec2 getRotation() {
            ClientPlayerEntity self = player();
            return self == null ? Vec2.ZERO : Vec2.rotation(self.getYaw(), self.getPitch());
        }

        @Override
        public void apply(Vec2 rotation, RotationMode mode) {
            if (mode == RotationMode.CLIENT) {
                pendingSilent = null;
                ClientPlayerEntity self = player();
                if (self != null) {
                    self.setYaw(rotation.getYaw());
                    self.setPitch(rotation.getPitch());
                }
                return;
            }
            pendingSilent = rotation;
        }
    }
}
