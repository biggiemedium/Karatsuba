package dev.px.karatsuba.adapter.mixin.render;

import dev.px.karatsuba.adapter.render.BlockVisibility;
import net.minecraft.client.render.chunk.ChunkOcclusionDataBuilder;
import net.minecraft.util.math.BlockPos;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Turns off chunk occlusion culling under a {@link BlockVisibility} filter.
 *
 * <p>The game skips drawing chunk sections it decides cannot be seen through
 * solid blocks, from which blocks it marks closed while building a section. With
 * those blocks hidden, everything behind them can be seen, so nothing is marked
 * closed and no section is culled.
 */
@Mixin(ChunkOcclusionDataBuilder.class)
public abstract class ChunkOcclusionDataBuilderMixin {

    @Inject(method = "markClosed", at = @At("HEAD"), cancellable = true)
    private void karatsuba$markClosed(BlockPos pos, CallbackInfo callback) {
        if (BlockVisibility.isFiltering()) {
            callback.cancel();
        }
    }
}
