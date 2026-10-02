package dev.px.karatsuba.adapter.mixin.render;

import dev.px.core.math.Vec3;
import dev.px.karatsuba.adapter.event.Events;
import dev.px.karatsuba.adapter.event.game.render.ParticleSpawnEvent;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.component.type.FireworkExplosionComponent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;

/**
 * A firework going off.
 *
 * <p>The burst is not a typed particle: the world builds an emitter object
 * directly and hands it to the particle manager, so it never passes through
 * {@code ParticleManagerMixin}. Stopping it here stops the emitter being made,
 * and with it every spark, flash and crackle it would have spawned.
 */
@Mixin(ClientWorld.class)
public abstract class ClientWorldMixin {

    @Inject(method = "addFireworkParticle", at = @At("HEAD"), cancellable = true)
    private void karatsuba$addFireworkParticle(double x, double y, double z,
                                               double velocityX, double velocityY, double velocityZ,
                                               List<FireworkExplosionComponent> explosions,
                                               CallbackInfo callback) {
        if (Events.cancelled(new ParticleSpawnEvent(ParticleSpawnEvent.Kind.FIREWORK, Vec3.of(x, y, z)))) {
            callback.cancel();
        }
    }
}
