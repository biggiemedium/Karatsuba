package dev.px.karatsuba.adapter.mixin.render;

import dev.px.karatsuba.adapter.event.Events;
import dev.px.karatsuba.adapter.event.game.render.EffectRenderEvent;
import dev.px.karatsuba.adapter.event.game.render.EffectRenderEvent.Effect;
import net.minecraft.client.render.BackgroundRenderer;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.Fog;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.effect.StatusEffects;
import org.joml.Vector4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Fog: distance fog, the fluid fogs, and the fog status effects pull in.
 */
@Mixin(BackgroundRenderer.class)
public abstract class BackgroundRendererMixin {

    /**
     * Every fog the game sets up, sky and terrain alike.
     *
     * <p>{@link Fog#DUMMY} is the game's own "no fog" value &mdash; it starts at
     * infinity &mdash; so returning it skips the whole calculation rather than
     * computing a fog and then pushing it out of sight.
     */
    @Inject(method = "applyFog", at = @At("HEAD"), cancellable = true)
    private static void karatsuba$applyFog(Camera camera, BackgroundRenderer.FogType fogType, Vector4f color,
                                           float viewDistance, boolean thickenFog, float tickDelta,
                                           CallbackInfoReturnable<Fog> callback) {
        if (Events.cancelled(new EffectRenderEvent(Effect.FOG))) {
            callback.setReturnValue(Fog.DUMMY);
        }
    }

    /**
     * The status effect whose fog applies this frame, if any.
     *
     * <p>Blindness is checked before Darkness, so with both active the game picks
     * Blindness and this leaves it alone: only a Darkness-only frame has its fog
     * modifier taken away. The return type is a package-private interface, which
     * is why the callback is typed as {@code Object} &mdash; null is the only
     * value written.
     */
    @Inject(method = "getFogModifier", at = @At("HEAD"), cancellable = true)
    private static void karatsuba$getFogModifier(Entity entity, float tickDelta,
                                                 CallbackInfoReturnable<Object> callback) {
        if (entity instanceof LivingEntity living
                && living.hasStatusEffect(StatusEffects.DARKNESS)
                && !living.hasStatusEffect(StatusEffects.BLINDNESS)
                && Events.cancelled(new EffectRenderEvent(Effect.DARKNESS))) {
            callback.setReturnValue(null);
        }
    }
}
