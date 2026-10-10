package dev.px.karatsuba.adapter.game.player;

import dev.px.core.Core;
import dev.px.core.control.ControlService;
import dev.px.core.control.MovementSink;
import dev.px.core.movement.MovementCorrection;
import dev.px.core.movement.simulation.MovementInput;
import dev.px.karatsuba.adapter.event.Events;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.input.Input;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.util.PlayerInput;

/**
 * How this game takes the movement keys Core's {@link ControlService} hands out.
 *
 * <p>The movement half of what {@link Rotations} is for the head. Core decides
 * <em>whose</em> keys are held this tick &mdash; a flow walking a route, a module
 * with a higher priority &mdash; and this decides how they reach the game, which
 * is the only part of the arrangement that needs Minecraft.
 *
 * <h2>Written where the keyboard is read</h2>
 * The keys are written straight after {@code Input.tick()}, at the same point the
 * Move Fix rewrites them, and in the same way: the {@code PlayerInput} keys and
 * the -1/0/1 movement numbers derived from them. Everything after &mdash; the
 * scaling for sneaking or using an item, the jump, sprinting, the input packet
 * &mdash; then follows from them exactly as from a real keyboard. Since 1.21.2
 * the client reports which keys it holds, and Grim narrows the inputs it
 * simulates to those, so the reported keys and the move have to agree. Writing
 * the keys rather than a velocity is what makes them agree.
 *
 * <h2>Corrected to the yaw the game moves by</h2>
 * A claim's keys are meant for the yaw in the same claim. The game moves by the
 * yaw the player has during the move, which is that one when the claim also won
 * the rotation, and the camera's or another module's when it did not. So the keys
 * go through {@code MovementCorrection} in its STRICT mode: the eight directions
 * a keyboard has, because those are the only inputs Grim tries.
 *
 * <h2>Sprinting</h2>
 * Set on the player directly, as the Sprint module does, rather than by pressing
 * the sprint key: the key is a toggle for players who set it so. Vanilla's own
 * checks run later in the same movement tick and stop a sprint the player cannot
 * have &mdash; no hunger, walking backwards, blinded &mdash; so nothing here
 * repeats them.
 *
 * <p>When no claim wins, nothing is written and the player's own keys stand.
 *
 * <p><b>Game thread only.</b>
 */
public final class MovementKeys {

    private static final Sink SINK = new Sink();

    /** The input being written this call, so the sink knows where. Null outside {@link #apply}. */
    private static Input writing;

    private MovementKeys() {
    }

    /** Installs the sink. Called once, from {@code MinecraftAdapter.install}. */
    public static void install(Core core) {
        core.getControlService().setMovementSink(SINK);
    }

    /**
     * Writes the winning movement claim, if any, over the keys the game just read.
     *
     * <p>Called by the player mixin straight after {@code Input.tick()}, after the
     * Move Fix and before a one-tick stand-still, which overrides both.
     */
    public static void apply(Input input) {
        if (!Events.isArmed() || input == null) {
            return;
        }
        writing = input;
        try {
            Core.controls().applyMovement();
        } finally {
            writing = null;
        }
    }

    private static ClientPlayerEntity player() {
        return MinecraftClient.getInstance().player;
    }

    /** The seam itself: one method, deciding nothing. */
    private static final class Sink implements MovementSink {

        @Override
        public void apply(MovementInput keys) {
            Input input = writing;
            ClientPlayerEntity self = player();
            if (input == null || self == null) {
                return;
            }
            MovementCorrection.Input fixed = MovementCorrection.correct(
                    keys.getYaw(), keys.getForward(), keys.getStrafe(), self.getYaw());
            float forward = Math.signum(fixed.getForward());
            float strafe = Math.signum(fixed.getStrafe());

            input.playerInput = new PlayerInput(
                    forward > 0f, forward < 0f,
                    strafe > 0f, strafe < 0f,
                    keys.isJump(), keys.isSneak(), keys.isSprint());
            // What KeyboardInput.tick derives from those keys.
            input.movementForward = forward;
            input.movementSideways = strafe;

            if (self.isSprinting() != (keys.isSprint() && forward > 0f)) {
                self.setSprinting(keys.isSprint() && forward > 0f);
            }
        }
    }
}
