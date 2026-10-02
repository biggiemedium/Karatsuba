package dev.px.karatsuba.adapter.mixin.render;

import dev.px.karatsuba.adapter.event.Events;
import dev.px.karatsuba.adapter.event.game.render.EffectRenderEvent;
import dev.px.karatsuba.adapter.event.game.render.EffectRenderEvent.Effect;
import net.minecraft.client.gui.hud.InGameOverlayRenderer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.util.math.MatrixStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * The overlays drawn in front of the camera rather than over the HUD: here, the
 * flames while burning.
 */
@Mixin(InGameOverlayRenderer.class)
public abstract class InGameOverlayRendererMixin {

    @Inject(method = "renderFireOverlay", at = @At("HEAD"), cancellable = true)
    private static void karatsuba$renderFireOverlay(MatrixStack matrices, VertexConsumerProvider vertexConsumers,
                                                    CallbackInfo callback) {
        if (Events.cancelled(new EffectRenderEvent(Effect.FIRE_OVERLAY))) {
            callback.cancel();
        }
    }
}
