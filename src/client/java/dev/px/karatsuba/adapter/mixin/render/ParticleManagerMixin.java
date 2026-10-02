package dev.px.karatsuba.adapter.mixin.render;

import dev.px.core.math.Vec3;
import dev.px.karatsuba.adapter.event.Events;
import dev.px.karatsuba.adapter.event.game.render.ParticleSpawnEvent;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleManager;
import net.minecraft.particle.ParticleEffect;
import net.minecraft.particle.ParticleType;
import net.minecraft.particle.ParticleTypes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Particles spawned by type, which is how nearly every particle in the game is
 * made: the world, packets and other particles all end up here.
 *
 * <p>Classified before anything is posted, and most particles are none of the
 * {@link ParticleSpawnEvent.Kind kinds}, so the common case is two reference
 * comparisons and no event. A firework's burst is the exception that does not
 * come through here; see {@code ClientWorldMixin}.
 */
@Mixin(ParticleManager.class)
public abstract class ParticleManagerMixin {

    @Inject(method = "addParticle(Lnet/minecraft/particle/ParticleEffect;DDDDDD)Lnet/minecraft/client/particle/Particle;",
            at = @At("HEAD"), cancellable = true)
    private void karatsuba$addParticle(ParticleEffect parameters, double x, double y, double z,
                                       double velocityX, double velocityY, double velocityZ,
                                       CallbackInfoReturnable<Particle> callback) {
        ParticleSpawnEvent.Kind kind = kindOf(parameters.getType());
        // Null is what the vanilla method returns for a type with no factory, so
        // every caller already copes with it.
        if (kind != null && Events.cancelled(new ParticleSpawnEvent(kind, Vec3.of(x, y, z)))) {
            callback.setReturnValue(null);
        }
    }

    /** @return the kind this particle type reports as, or null for one nobody asks about */
    private static ParticleSpawnEvent.Kind kindOf(ParticleType<?> type) {
        if (type == ParticleTypes.EXPLOSION || type == ParticleTypes.EXPLOSION_EMITTER) {
            return ParticleSpawnEvent.Kind.EXPLOSION;
        }
        if (type == ParticleTypes.FIREWORK) {
            return ParticleSpawnEvent.Kind.FIREWORK;
        }
        return null;
    }
}
