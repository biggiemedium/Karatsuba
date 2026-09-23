package dev.px.karatsuba.adapter.game.player;

import dev.px.core.Core;
import dev.px.core.math.Vec2;
import dev.px.core.movement.rotation.RotationMode;
import dev.px.core.movement.rotation.RotationService;
import dev.px.core.movement.rotation.RotationSink;
import dev.px.karatsuba.adapter.event.Events;
import dev.px.karatsuba.adapter.game.Options;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;

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

    /** Last value handed to Core, so an unchanged slider does not invalidate its cache. */
    private static double appliedSensitivity = Double.NaN;

    private Rotations() {
    }

    /** Installs the sink. Called once, from {@code MinecraftAdapter.install}. */
    public static void install(Core core) {
        core.getRotationService().setSink(SINK);
    }

    /**
     * Resolves the tick's rotation and, for a silent one, swaps it in.
     *
     * <p>Called at the head of the game's own {@code sendMovementPackets}. That
     * is the latest point still inside the tick, which is the property worth
     * having: every module subscription point &mdash; {@code TickEvent},
     * {@code PlayerUpdateEvent}, {@code PlayerMoveEvent}, either stage &mdash;
     * has already run, so no module is ever silently a tick late in claiming the
     * rotation.
     *
     * <p><b>The cost of that placement, stated plainly:</b> in
     * {@link RotationMode#CLIENT} the yaw is assigned after {@code tickMovement}
     * has already turned the held keys into velocity, so <em>this</em> tick's
     * motion used the previous yaw and the body follows the head one tick later.
     * The camera and the outgoing packet are both correct immediately; only the
     * walking direction lags.
     *
     * <p>That is the right trade, because the alternative &mdash; applying before
     * movement &mdash; makes any module that claims from {@code PlayerUpdateEvent}
     * a tick late instead, which is far harder to notice. A module that needs the
     * body to keep its intended heading despite the turn should use
     * {@code MovementCorrection} rather than move this call.
     */
    public static void beforeMovementPackets() {
        if (!Events.isArmed()) {
            return;
        }
        syncSensitivity();

        // Writes through the sink: CLIENT assigns the fields, SILENT parks the
        // value below. Does nothing at all when no module holds the rotation.
        Core.rotations().apply();

        Vec2 silent = pendingSilent;
        ClientPlayerEntity self = player();
        if (silent == null || self == null) {
            return;
        }
        stashedYaw = self.getYaw();
        stashedPitch = self.getPitch();
        stashed = true;

        self.setYaw(silent.getYaw());
        self.setPitch(silent.getPitch());
    }

    /**
     * Puts the player's real rotation back.
     *
     * <p>At the tail of the same call, so the swap is invisible to everything
     * except the packet builder between the two. The pending value is dropped
     * either way: a claim lasts one tick unless renewed, and a rotation that
     * outlived its claim is exactly the bug the lease model exists to prevent.
     */
    public static void afterMovementPackets() {
        pendingSilent = null;
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
