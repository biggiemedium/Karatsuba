package dev.px.karatsuba.adapter.mixin;

import dev.px.core.Core;
import dev.px.core.event.Stage;
import dev.px.core.math.Vec2;
import dev.px.core.math.Vec3;
import dev.px.karatsuba.adapter.event.game.PlayerUpdateEvent;
import net.minecraft.client.network.ClientPlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Posts {@link PlayerUpdateEvent} around the player's own tick.
 *
 * <p>A mixin because Fabric API has no hook here. That is the rule the whole
 * {@code mixin} package follows: if the API exposes it, the bridge in
 * {@code event/} uses the API, and only what the API cannot see gets a mixin.
 *
 * <p>Two injections, one event, and the pairing is what makes the staged event
 * honest. HEAD is cancellable, so a PRE handler that cancels skips the game's own
 * update &mdash; and because the method returned early, TAIL never runs and POST
 * is never posted. A handler that saw a PRE it cancelled and then a POST anyway
 * would be seeing a stage that did not happen.
 */
@Mixin(ClientPlayerEntity.class)
public abstract class ClientPlayerEntityMixin {

    @Inject(method = "tick", at = @At("HEAD"), cancellable = true)
    private void karatsuba$preTick(CallbackInfo callback) {
        if (post(Stage.PRE)) {
            callback.cancel();
        }
    }

    @Inject(method = "tick", at = @At("TAIL"))
    private void karatsuba$postTick(CallbackInfo callback) {
        post(Stage.POST);
    }

    /**
     * @return whether the event was cancelled
     *
     * <p>State is read here rather than in the event's constructor so the snapshot
     * is taken at the injection point, which is the moment the stage names.
     */
    private boolean post(Stage stage) {
        ClientPlayerEntity self = (ClientPlayerEntity) (Object) this;

        PlayerUpdateEvent event = new PlayerUpdateEvent(
                stage,
                Vec3.of(self.getX(), self.getY(), self.getZ()),
                Vec2.rotation(self.getYaw(), self.getPitch()),
                self.isOnGround());

        return Core.bus().post(event).isCancelled();
    }
}
