package dev.px.karatsuba.adapter.mixin.render;

import dev.px.karatsuba.adapter.render.BlockVisibility;
import net.minecraft.block.BlockState;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.block.BlockRenderManager;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.fluid.FluidState;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.random.Random;
import net.minecraft.world.BlockRenderView;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Leaves filtered-out blocks and fluids out of the chunk meshes; see
 * {@link BlockVisibility}. Both calls are the ones {@code SectionBuilder} makes
 * per block while building a chunk, on a worker thread.
 */
@Mixin(BlockRenderManager.class)
public abstract class BlockRenderManagerMixin {

    @Inject(method = "renderBlock", at = @At("HEAD"), cancellable = true)
    private void karatsuba$renderBlock(BlockState state, BlockPos pos, BlockRenderView world, MatrixStack matrices,
                                       VertexConsumer vertices, boolean cull, Random random,
                                       CallbackInfo callback) {
        if (BlockVisibility.isHidden(state)) {
            callback.cancel();
        }
    }

    @Inject(method = "renderFluid", at = @At("HEAD"), cancellable = true)
    private void karatsuba$renderFluid(BlockPos pos, BlockRenderView world, VertexConsumer vertices,
                                       BlockState blockState, FluidState fluidState, CallbackInfo callback) {
        // The fluid's block state is the water or lava block, so it is filtered by
        // that block's id like any other.
        if (BlockVisibility.isHidden(blockState)) {
            callback.cancel();
        }
    }
}
