package dev.px.karatsuba.adapter.mixin.render;

import dev.px.karatsuba.adapter.event.Events;
import dev.px.karatsuba.adapter.event.game.render.EffectRenderEvent;
import dev.px.karatsuba.adapter.event.game.render.EffectRenderEvent.Effect;
import dev.px.karatsuba.adapter.event.game.render.GammaEvent;
import net.minecraft.client.render.LightmapTextureManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.Slice;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * The lightmap: how bright every light level is drawn, rebuilt once a frame.
 */
@Mixin(LightmapTextureManager.class)
public abstract class LightmapTextureManagerMixin {

    /**
     * The player's gamma, as the lightmap reads it.
     *
     * <p>Sliced to the first unboxing after {@code getGamma()}, which is the
     * gamma read and nothing else &mdash; {@code update} unboxes other options
     * too. Replacing the value here rather than the option is what lets a module
     * brighten the world without writing anything it would owe back.
     */
    @Redirect(method = "update",
            at = @At(value = "INVOKE", target = "Ljava/lang/Double;floatValue()F", ordinal = 0),
            slice = @Slice(from = @At(value = "INVOKE",
                    target = "Lnet/minecraft/client/option/GameOptions;getGamma()Lnet/minecraft/client/option/SimpleOption;")))
    private float karatsuba$gamma(Double option) {
        float gamma = option.floatValue();
        return Events.isArmed() ? Events.post(new GammaEvent(gamma)).getGamma() : gamma;
    }

    /**
     * How strongly the Darkness effect is pulsing, 0 when it is not.
     *
     * <p>The single input to both halves of the lightmap's darkness: the pulse
     * itself and the gamma it eats into. Zero here is the "no effect" answer.
     */
    @Inject(method = "getDarknessFactor", at = @At("HEAD"), cancellable = true)
    private void karatsuba$getDarknessFactor(float tickDelta, CallbackInfoReturnable<Float> callback) {
        if (Events.cancelled(new EffectRenderEvent(Effect.DARKNESS))) {
            callback.setReturnValue(0f);
        }
    }
}
