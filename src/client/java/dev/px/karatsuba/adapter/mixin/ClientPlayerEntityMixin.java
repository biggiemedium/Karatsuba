package dev.px.karatsuba.adapter.mixin;

import dev.px.core.event.Stage;
import dev.px.core.math.Vec2;
import dev.px.core.math.Vec3;
import dev.px.karatsuba.adapter.event.Events;
import dev.px.karatsuba.adapter.event.game.PlayerMoveEvent;
import dev.px.karatsuba.adapter.event.game.PlayerUpdateEvent;
import dev.px.karatsuba.adapter.game.player.Rotations;
import net.minecraft.client.network.ClientPlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
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
        if (postUpdate(Stage.PRE)) {
            callback.cancel();
        }
    }

    @Inject(method = "tick", at = @At("TAIL"))
    private void karatsuba$postTick(CallbackInfo callback) {
        postUpdate(Stage.POST);
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
        if (postMove(Stage.PRE)) {
            callback.cancel();
        }
    }

    @Inject(method = "tickMovement", at = @At("TAIL"))
    private void karatsuba$postMove(CallbackInfo callback) {
        postMove(Stage.POST);
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
     * Resolves the tick's rotation just before the game sends it.
     *
     * <p>The last moment that is still inside the tick, so every module has filed
     * its claim, and the first moment that matters, because what leaves here is
     * what the server believes. {@code Rotations} owns what happens; this only
     * says when.
     */
    @Inject(method = "sendMovementPackets", at = @At("HEAD"))
    private void karatsuba$beforeMovementPackets(CallbackInfo callback) {
        Rotations.beforeMovementPackets();
    }

    @Inject(method = "sendMovementPackets", at = @At("RETURN"))
    private void karatsuba$afterMovementPackets(CallbackInfo callback) {
        Rotations.afterMovementPackets();
    }

    private ClientPlayerEntity self() {
        return (ClientPlayerEntity) (Object) this;
    }
}
