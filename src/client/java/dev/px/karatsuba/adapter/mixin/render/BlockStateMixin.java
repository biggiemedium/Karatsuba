package dev.px.karatsuba.adapter.mixin.render;

import dev.px.karatsuba.adapter.render.BlockVisibility;
import net.minecraft.block.AbstractBlock;
import net.minecraft.block.BlockRenderType;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.BlockView;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * How a block state draws under a {@link BlockVisibility} filter.
 */
@Mixin(AbstractBlock.AbstractBlockState.class)
public abstract class BlockStateMixin {

    /**
     * A hidden block has no model to draw.
     *
     * <p>The filter's main hook, ahead of {@code BlockRenderManagerMixin}: chunk
     * building asks a block's render type before meshing it, and only a
     * {@code MODEL} block is handed to a renderer at all. Fabric API's Indigo
     * renderer takes over that hand-off for models it calls non-vanilla (grass,
     * among others), going round {@code BlockRenderManager.renderBlock}; answering
     * {@code INVISIBLE} here stops the block before either renderer sees it.
     */
    @Inject(method = "getRenderType", at = @At("HEAD"), cancellable = true)
    private void karatsuba$getRenderType(CallbackInfoReturnable<BlockRenderType> callback) {
        if (BlockVisibility.isHidden((net.minecraft.block.BlockState) (Object) this)) {
            callback.setReturnValue(BlockRenderType.INVISIBLE);
        }
    }

    /**
     * No ambient occlusion with full bright on: the darkening smooth lighting adds
     * where blocks meet would still shade a shown ore by the stone around it that
     * is no longer drawn.
     */
    @Inject(method = "getAmbientOcclusionLightLevel", at = @At("HEAD"), cancellable = true)
    private void karatsuba$getAmbientOcclusionLightLevel(BlockView world, BlockPos pos,
                                                         CallbackInfoReturnable<Float> callback) {
        if (BlockVisibility.isFullBright()) {
            callback.setReturnValue(1f);
        }
    }
}
