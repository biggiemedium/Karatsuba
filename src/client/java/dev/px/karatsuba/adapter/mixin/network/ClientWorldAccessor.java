package dev.px.karatsuba.adapter.mixin.network;

import net.minecraft.client.network.PendingUpdateManager;
import net.minecraft.client.world.ClientWorld;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * The client world's book of predicted blocks, which the game keeps
 * package-private.
 *
 * <p>Every sequenced block interaction is numbered from it, and each block the
 * client predicts is held there until the server acknowledges that number. Read
 * by {@code PlacementSync} to learn which number a click went out with.
 */
@Mixin(ClientWorld.class)
public interface ClientWorldAccessor {

    @Accessor("pendingUpdateManager")
    PendingUpdateManager karatsuba$getPendingUpdateManager();
}
