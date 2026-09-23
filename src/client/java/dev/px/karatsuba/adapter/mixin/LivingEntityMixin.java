package dev.px.karatsuba.adapter.mixin;

import dev.px.core.math.Vec3;
import dev.px.karatsuba.adapter.event.Events;
import dev.px.karatsuba.adapter.event.game.PlayerJumpEvent;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
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
public abstract class LivingEntityMixin {

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
}
