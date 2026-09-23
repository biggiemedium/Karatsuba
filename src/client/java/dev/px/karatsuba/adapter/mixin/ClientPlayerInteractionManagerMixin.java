package dev.px.karatsuba.adapter.mixin;

import dev.px.core.math.Vec3;
import dev.px.karatsuba.adapter.event.Events;
import dev.px.karatsuba.adapter.event.game.AttackEvent;
import net.minecraft.client.network.ClientPlayerInteractionManager;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * The swing, before it reaches the server.
 *
 * <p>{@code attackEntity} is where the client commits: it sends the interaction
 * packet and plays the swing. Cancelling at HEAD means neither happens, so the
 * attack did not occur rather than occurring invisibly.
 *
 * <p>The target is described into Core types here rather than handed over, which
 * is what lets a module decide whether to allow the hit without ever holding an
 * {@code Entity}.
 */
@Mixin(ClientPlayerInteractionManager.class)
public abstract class ClientPlayerInteractionManagerMixin {

    @Inject(method = "attackEntity", at = @At("HEAD"), cancellable = true)
    private void karatsuba$attackEntity(PlayerEntity attacker, Entity target, CallbackInfo callback) {
        AttackEvent event = new AttackEvent(
                target.getId(),
                target.getName().getString(),
                Vec3.of(target.getX(), target.getY(), target.getZ()),
                target instanceof PlayerEntity);

        if (Events.cancelled(event)) {
            callback.cancel();
        }
    }
}
