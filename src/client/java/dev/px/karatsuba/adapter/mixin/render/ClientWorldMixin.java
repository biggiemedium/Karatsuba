package dev.px.karatsuba.adapter.mixin.render;

import dev.px.core.math.Vec3;
import dev.px.karatsuba.adapter.event.Events;
import dev.px.karatsuba.adapter.event.game.render.ParticleSpawnEvent;
import dev.px.karatsuba.adapter.event.game.world.BlockBreakingEvent;
import dev.px.karatsuba.adapter.world.Voxels;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.component.type.FireworkExplosionComponent;
import net.minecraft.util.math.BlockPos;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;

/**
 * Things the client world is told: a firework going off, and blocks being broken.
 *
 * <p>The firework burst is not a typed particle: the world builds an emitter object
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

    /**
     * Block-breaking progress, the player's own and everyone else's: the server's
     * breaking-progress packets and the client's own mining both land here.
     */
    @Inject(method = "setBlockBreakingInfo", at = @At("HEAD"))
    private void karatsuba$setBlockBreakingInfo(int entityId, BlockPos pos, int progress, CallbackInfo callback) {
        Events.post(new BlockBreakingEvent(entityId, Voxels.toVec3i(pos), progress));
    }
}
