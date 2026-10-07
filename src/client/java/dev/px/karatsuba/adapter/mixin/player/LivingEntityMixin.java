package dev.px.karatsuba.adapter.mixin.player;

import dev.px.core.math.Vec3;
import dev.px.karatsuba.adapter.event.Events;
import dev.px.karatsuba.adapter.event.game.player.PlayerJumpEvent;
import dev.px.karatsuba.adapter.game.entity.PositionStamped;
import dev.px.karatsuba.adapter.movement.MotionBridge;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * The jump, for the local player only.
 *
 * <p>{@code jump()} lives on {@code LivingEntity}, so this injection runs for
 * every mob in the world. The identity check is what keeps that from turning into
 * an event storm, and it is deliberately the first thing in the method: a
 * reference comparison against a field is about as cheap as an injection gets,
 * and everything after it only happens a few times a second.
 *
 * <p>Posted from the jump rather than from the key, so it fires for every jump
 * the game performs &mdash; including the ones a module caused by holding the
 * control down.
 */
@Mixin(LivingEntity.class)
public abstract class LivingEntityMixin implements PositionStamped {

    /** Positions the server has sent this entity. See {@link PositionStamped}. */
    @Unique
    private long karatsuba$positionStamp;

    @Override
    public long karatsuba$positionStamp() {
        return karatsuba$positionStamp;
    }

    /**
     * Every position the server sends a living entity &mdash; absolute, relative
     * or a sync &mdash; arrives here before the game starts easing toward it.
     */
    @Inject(method = "updateTrackedPositionAndAngles", at = @At("HEAD"))
    private void karatsuba$positionSent(double x, double y, double z, float yaw, float pitch,
                                        int interpolationSteps, CallbackInfo callback) {
        karatsuba$positionStamp++;
    }

    @Inject(method = "jump", at = @At("HEAD"), cancellable = true)
    private void karatsuba$jump(CallbackInfo callback) {
        LivingEntity self = (LivingEntity) (Object) this;
        if (self != MinecraftClient.getInstance().player) {
            return;
        }
        PlayerJumpEvent event = new PlayerJumpEvent(
                Vec3.of(self.getX(), self.getY(), self.getZ()),
                self.isSprinting());

        if (Events.cancelled(event)) {
            callback.cancel();
        }
    }

    /**
     * The jump went ahead.
     *
     * <p>Not the same as the key being down: the game jumps only from the ground
     * and only once its cooldown has run out, and a handler above may have
     * refused it. The simulation is scored against what happened.
     */
    @Inject(method = "jump", at = @At("TAIL"))
    private void karatsuba$jumped(CallbackInfo callback) {
        if ((Object) this == MinecraftClient.getInstance().player) {
            MotionBridge.jumped();
        }
    }
}
