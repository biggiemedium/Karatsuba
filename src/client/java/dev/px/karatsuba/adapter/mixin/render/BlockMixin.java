package dev.px.karatsuba.adapter.mixin.render;

import dev.px.karatsuba.adapter.render.BlockVisibility;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.util.math.Direction;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Face culling under a {@link BlockVisibility} filter: a shown block draws every
 * face a hidden neighbour would have covered, so an ore in stone is a whole cube
 * rather than nothing at all.
 */
@Mixin(Block.class)
public abstract class BlockMixin {

    @Inject(method = "shouldDrawSide", at = @At("HEAD"), cancellable = true)
    private static void karatsuba$shouldDrawSide(BlockState state, BlockState neighbour, Direction side,
                                                 CallbackInfoReturnable<Boolean> callback) {
        if (!BlockVisibility.isFiltering()) {
            return;
        }
        if (BlockVisibility.isHidden(state)) {
            callback.setReturnValue(false);
        } else if (BlockVisibility.isHidden(neighbour)) {
            callback.setReturnValue(true);
        }
    }
}
