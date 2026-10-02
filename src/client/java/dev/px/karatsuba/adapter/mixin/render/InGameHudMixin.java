package dev.px.karatsuba.adapter.mixin.render;

import dev.px.karatsuba.adapter.event.Events;
import dev.px.karatsuba.adapter.event.game.render.EffectRenderEvent;
import dev.px.karatsuba.adapter.event.game.render.EffectRenderEvent.Effect;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.hud.InGameHud;
import net.minecraft.client.render.RenderTickCounter;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * The screen-space overlays drawn over the HUD.
 *
 * <p>Each is its own private method that the game only calls when the overlay is
 * due, so cancelling at the head of one skips exactly that overlay and nothing
 * drawn around it.
 */
@Mixin(InGameHud.class)
public abstract class InGameHudMixin {

    @Inject(method = "renderPortalOverlay", at = @At("HEAD"), cancellable = true)
    private void karatsuba$renderPortalOverlay(DrawContext context, float strength, CallbackInfo callback) {
        if (Events.cancelled(new EffectRenderEvent(Effect.PORTAL_OVERLAY))) {
            callback.cancel();
        }
    }

    @Inject(method = "renderNauseaOverlay", at = @At("HEAD"), cancellable = true)
    private void karatsuba$renderNauseaOverlay(DrawContext context, float strength, CallbackInfo callback) {
        if (Events.cancelled(new EffectRenderEvent(Effect.NAUSEA))) {
            callback.cancel();
        }
    }

    @Inject(method = "renderStatusEffectOverlay", at = @At("HEAD"), cancellable = true)
    private void karatsuba$renderStatusEffectOverlay(DrawContext context, RenderTickCounter tickCounter,
                                                     CallbackInfo callback) {
        if (Events.cancelled(new EffectRenderEvent(Effect.STATUS_EFFECT_ICONS))) {
            callback.cancel();
        }
    }
}
