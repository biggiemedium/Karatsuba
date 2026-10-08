package dev.px.karatsuba.adapter.mixin.render;

import dev.px.karatsuba.adapter.render.BlockVisibility;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.block.entity.BlockEntityRenderDispatcher;
import net.minecraft.client.util.math.MatrixStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Leaves filtered-out block entities undrawn: chests, signs, banners and the rest
 * are drawn every frame rather than built into a chunk, so the chunk mixins never
 * see them. See {@link BlockVisibility}.
 */
@Mixin(BlockEntityRenderDispatcher.class)
public abstract class BlockEntityRenderDispatcherMixin {

    @Inject(method = "render", at = @At("HEAD"), cancellable = true)
    private <E extends BlockEntity> void karatsuba$render(E blockEntity, float tickDelta, MatrixStack matrices,
                                                         VertexConsumerProvider vertices, CallbackInfo callback) {
        if (BlockVisibility.isHidden(blockEntity.getCachedState())) {
            callback.cancel();
        }
    }
}
