package dev.px.karatsuba.adapter.mixin.player;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import dev.px.core.event.Stage;
import dev.px.core.math.Vec2;
import dev.px.core.math.Vec3;
import dev.px.karatsuba.adapter.event.Events;
import dev.px.karatsuba.adapter.event.game.player.PlayerMoveEvent;
import dev.px.karatsuba.adapter.event.game.player.PlayerUpdateEvent;
import dev.px.karatsuba.adapter.event.game.player.SlowdownEvent;
import dev.px.karatsuba.adapter.game.player.Inputs;
import dev.px.karatsuba.adapter.game.player.MovementKeys;
import dev.px.karatsuba.adapter.game.player.Rotations;
import dev.px.karatsuba.adapter.movement.BlockPushOut;
import dev.px.karatsuba.adapter.movement.MotionBridge;
import net.minecraft.client.network.ClientPlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyConstant;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * The player's own tick, and the movement inside it.
 *
 * <p>A mixin because Fabric API has no hook here. That is the rule the whole
 * {@code mixin} package follows: if the API exposes it, the bridge in
 * {@code event/} uses the API, and only what the API cannot see gets a mixin.
 *
 * <p>Two events, and the difference between them is the difference between the
 * two methods they wrap:
 *
 * <ul>
 *   <li>{@code tick} is the player's whole update, and
 *       {@link PlayerUpdateEvent} brackets it. This is where a module acts when
 *       it wants consistent player state.</li>
 *   <li>{@code tickMovement} is the part of that update which turns held keys
 *       into velocity, and {@link PlayerMoveEvent} brackets it. This is where a
 *       module changes how the player moves.</li>
 * </ul>
 *
 * <p>Each pairs a cancellable HEAD with a TAIL, which is what makes a staged
 * event honest: cancelling PRE returns from the method, so TAIL never runs and
 * POST is never posted. A handler that saw a PRE it cancelled and then a POST
 * anyway would be seeing a stage that did not happen.
 */
@Mixin(ClientPlayerEntity.class)
public abstract class ClientPlayerEntityMixin {

    // ----------------------------------------------------------------- tick

    @Inject(method = "tick", at = @At("HEAD"), cancellable = true)
    private void karatsuba$preTick(CallbackInfo callback) {
        // Every tick resolves its rotation afresh, even one whose end was skipped
        // because a handler cancelled the tick before it ran.
        Rotations.endOfTick();
        if (postUpdate(Stage.PRE)) {
            callback.cancel();
        }
    }

    @Inject(method = "tick", at = @At("TAIL"))
    private void karatsuba$postTick(CallbackInfo callback) {
        postUpdate(Stage.POST);
        Rotations.endOfTick();
    }

    /**
     * @return whether the event was cancelled
     *
     * <p>State is read here rather than in the event's constructor so the snapshot
     * is taken at the injection point, which is the moment the stage names.
     */
    private boolean postUpdate(Stage stage) {
        ClientPlayerEntity self = self();

        PlayerUpdateEvent event = new PlayerUpdateEvent(
                stage,
                Vec3.of(self.getX(), self.getY(), self.getZ()),
                Vec2.rotation(self.getYaw(), self.getPitch()),
                self.isOnGround());

        return Events.cancelled(event);
    }

    // ------------------------------------------------------------- movement

    @Inject(method = "tickMovement", at = @At("HEAD"), cancellable = true)
    private void karatsuba$preMove(CallbackInfo callback) {
        // First: the move must happen at the yaw the packet will carry.
        Rotations.beforeMovementTick();
        MotionBridge.beforeMovementTick();
        if (postMove(Stage.PRE)) {
            callback.cancel();
        }
    }

    /**
     * The keys, the moment the game has read them: corrected for a silent
     * rotation before anything uses them. See {@code Rotations.correctInput}.
     */
    @Inject(method = "tickMovement",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/input/Input;tick()V", shift = At.Shift.AFTER))
    private void karatsuba$afterInput(CallbackInfo callback) {
        Rotations.correctInput(self().input);
        // After the Move Fix: a movement claim replaces the player's keys outright,
        // corrected to the yaw the game moves by.
        MovementKeys.apply(self().input);
        // After both: standing still overrides whatever they wrote.
        Inputs.applyHoldStill(self().input);
        Inputs.applyJumpLetGo(self().input);
    }

    /** The item-use slowdown for this tick, decided where the game asks whether an item is in use. */
    @Unique
    private float karatsuba$useSlowdown = SlowdownEvent.VANILLA;

    /**
     * The game's one {@code isUsingItem()} check in {@code tickMovement}, which
     * guards the slowdown: posts {@link SlowdownEvent} once while an item is in
     * use, and keeps its answer for the two multiplications that follow.
     */
    @ModifyExpressionValue(method = "tickMovement",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/network/ClientPlayerEntity;isUsingItem()Z"))
    private boolean karatsuba$onUseSlowdown(boolean using) {
        karatsuba$useSlowdown = using
                ? Events.post(new SlowdownEvent(self().input.getMovementInput().lengthSquared() > 0f)).applied()
                : SlowdownEvent.VANILLA;
        return using;
    }

    /**
     * Both inputs' {@code *= 0.2f}. Read from the 1.21.4 bytecode: they are the
     * only {@code 0.2f} constants in {@code tickMovement}.
     */
    @ModifyConstant(method = "tickMovement", constant = @Constant(floatValue = SlowdownEvent.VANILLA))
    private float karatsuba$useSlowdownMultiplier(float vanilla) {
        return karatsuba$useSlowdown;
    }

    @Inject(method = "tickMovement", at = @At("TAIL"))
    private void karatsuba$postMove(CallbackInfo callback) {
        postMove(Stage.POST);
        // After the event, so a velocity a handler wrote back is part of what
        // Core is told this tick's move produced.
        MotionBridge.afterMovementTick(self());
    }

    /**
     * @return whether the event was cancelled
     *
     * <p>The velocity is written back only when a handler replaced it, so the
     * common case &mdash; nobody subscribed, or nobody wanted this tick &mdash;
     * costs a post and no assignment. Writing it back unconditionally would
     * round-trip the vector through Core's types every tick for nothing.
     */
    private boolean postMove(Stage stage) {
        ClientPlayerEntity self = self();
        net.minecraft.util.math.Vec3d motion = self.getVelocity();

        PlayerMoveEvent event = new PlayerMoveEvent(
                stage,
                Vec3.of(self.getX(), self.getY(), self.getZ()),
                Vec3.of(motion.x, motion.y, motion.z));

        boolean cancelled = Events.cancelled(event);

        if (event.isModified()) {
            Vec3 replacement = event.getVelocity();
            self.setVelocity(replacement.getX(), replacement.getY(), replacement.getZ());
        }
        return cancelled;
    }

    // ------------------------------------------------------------- rotations

    /**
     * A silent rotation goes in for the packets, if the movement tick did not
     * already put it in place (correction off, or the move was skipped).
     * {@code Rotations} owns what happens; this only says when.
     */
    @Inject(method = "sendMovementPackets", at = @At("HEAD"))
    private void karatsuba$beforeMovementPackets(CallbackInfo callback) {
        Rotations.beforeMovementPackets();
        // After the rotation is resolved, so Core is told the rotation that is
        // about to be sent rather than the one the camera shows.
        MotionBridge.beforeMovementPackets(self());
    }

    @Inject(method = "sendMovementPackets", at = @At("RETURN"))
    private void karatsuba$afterMovementPackets(CallbackInfo callback) {
        // Before a silent rotation is put back, for the same reason.
        MotionBridge.afterMovementPackets(self());
        Rotations.afterMovementPackets();
    }

    /** The push out of blocks, skipped while {@link BlockPushOut} says so; see there for why Grim allows it. */
    @Inject(method = "pushOutOfBlocks", at = @At("HEAD"), cancellable = true)
    private void karatsuba$pushOutOfBlocks(double x, double z, CallbackInfo callback) {
        if (BlockPushOut.skipped()) {
            callback.cancel();
        }
    }

    private ClientPlayerEntity self() {
        return (ClientPlayerEntity) (Object) this;
    }
}
