package dev.px.karatsuba.adapter.mixin.render;

import dev.px.karatsuba.adapter.event.Events;
import dev.px.karatsuba.adapter.event.game.render.SkyRenderEvent;
import net.minecraft.client.render.Fog;
import net.minecraft.client.render.SkyRendering;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.util.math.MatrixStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * The overworld sky, replaceable whole.
 *
 * <p>1.21.4's sky pass ({@code WorldRenderer}'s render-sky lambda) calls these four
 * in order: {@code renderSky} (the dome), {@code renderGlowingSky} (sunrise),
 * {@code renderCelestialBodies} (sun, moon, stars), {@code renderSkyDark} (the
 * lower half). {@link SkyRenderEvent} is posted at the first; when it is
 * cancelled, that and the three after it are skipped. The flag is set afresh
 * every time the dome is drawn, so one frame's answer never leaks into the next.
 */
@Mixin(SkyRendering.class)
public abstract class SkyRenderingMixin {

    @Unique
    private boolean karatsuba$replaced;

    @Inject(method = "renderSky", at = @At("HEAD"), cancellable = true)
    private void karatsuba$renderSky(float red, float green, float blue, CallbackInfo callback) {
        karatsuba$replaced = Events.cancelled(new SkyRenderEvent());
        if (karatsuba$replaced) {
            callback.cancel();
        }
    }

    @Inject(method = "renderGlowingSky", at = @At("HEAD"), cancellable = true)
    private void karatsuba$renderGlowingSky(MatrixStack matrices, VertexConsumerProvider.Immediate vertexConsumers,
                                            float angleRadians, int color, CallbackInfo callback) {
        if (karatsuba$replaced) {
            callback.cancel();
        }
    }

    @Inject(method = "renderCelestialBodies", at = @At("HEAD"), cancellable = true)
    private void karatsuba$renderCelestialBodies(MatrixStack matrices, VertexConsumerProvider.Immediate vertexConsumers,
                                                 float rot, int phase, float alpha, float starBrightness, Fog fog,
                                                 CallbackInfo callback) {
        if (karatsuba$replaced) {
            callback.cancel();
        }
    }

    @Inject(method = "renderSkyDark", at = @At("HEAD"), cancellable = true)
    private void karatsuba$renderSkyDark(MatrixStack matrices, CallbackInfo callback) {
        if (karatsuba$replaced) {
            callback.cancel();
        }
    }
}
