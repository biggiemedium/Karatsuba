package dev.px.karatsuba.adapter.mixin.render;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import dev.px.karatsuba.adapter.screen.LoadingScreenBridge;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.SplashOverlay;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.util.Identifier;
import net.minecraft.util.Util;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.function.Function;

/**
 * The game's loading screen, with its drawing handed to the client's
 * {@code LoadingScreen} and its logic left alone.
 *
 * <p>{@code render} both draws and decides: it eases the progress, notices the
 * reload finishing, reports its errors, re-initialises the screen underneath and
 * removes itself. Cancelling it would mean copying all of that, so only its draws
 * are wrapped &mdash; the red fills and clear, the two halves of the logo and the
 * bar &mdash; and the client's screen is drawn at the end from the same fields
 * the game's fades are computed from. The screen faded over (the title screen)
 * is still drawn by the game.
 */
@Mixin(SplashOverlay.class)
public abstract class SplashOverlayMixin {

    @Shadow
    @Final
    private boolean reloading;

    @Shadow
    private float progress;

    @Shadow
    private long reloadCompleteTime;

    @Shadow
    private long reloadStartTime;

    @WrapOperation(method = "render", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/gui/DrawContext;fill(Lnet/minecraft/client/render/RenderLayer;IIIII)V"))
    private void karatsuba$fill(DrawContext context, RenderLayer layer, int x1, int y1, int x2, int y2, int color,
                                Operation<Void> original) {
        if (!LoadingScreenBridge.replacing()) {
            original.call(context, layer, x1, y1, x2, y2, color);
        }
    }

    @WrapOperation(method = "render", at = @At(value = "INVOKE",
            target = "Lcom/mojang/blaze3d/platform/GlStateManager;_clearColor(FFFF)V", remap = false))
    private void karatsuba$clearColor(float red, float green, float blue, float alpha, Operation<Void> original) {
        if (!LoadingScreenBridge.replacing()) {
            original.call(red, green, blue, alpha);
        }
    }

    @WrapOperation(method = "render", at = @At(value = "INVOKE",
            target = "Lcom/mojang/blaze3d/platform/GlStateManager;_clear(I)V", remap = false))
    private void karatsuba$clear(int mask, Operation<Void> original) {
        if (!LoadingScreenBridge.replacing()) {
            original.call(mask);
        }
    }

    @WrapOperation(method = "render", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/gui/DrawContext;drawTexture(Ljava/util/function/Function;Lnet/minecraft/util/Identifier;IIFFIIIIIII)V"))
    private void karatsuba$logo(DrawContext context, Function<Identifier, RenderLayer> layers, Identifier texture,
                                int x, int y, float u, float v, int width, int height,
                                int regionWidth, int regionHeight, int textureWidth, int textureHeight, int color,
                                Operation<Void> original) {
        if (!LoadingScreenBridge.replacing()) {
            original.call(context, layers, texture, x, y, u, v, width, height,
                    regionWidth, regionHeight, textureWidth, textureHeight, color);
        }
    }

    @WrapOperation(method = "render", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/gui/screen/SplashOverlay;renderProgressBar(Lnet/minecraft/client/gui/DrawContext;IIIIF)V"))
    private void karatsuba$bar(SplashOverlay overlay, DrawContext context, int minX, int minY, int maxX, int maxY,
                               float opacity, Operation<Void> original) {
        if (!LoadingScreenBridge.replacing()) {
            original.call(overlay, context, minX, minY, maxX, maxY, opacity);
        }
    }

    /**
     * The client's screen, with the game's fades: after the game has drawn the
     * screen underneath and run its logic for the frame, including removing itself
     * two seconds after loading finished.
     */
    @Inject(method = "render", at = @At("TAIL"))
    private void karatsuba$draw(DrawContext context, int mouseX, int mouseY, float delta, CallbackInfo callback) {
        if (!LoadingScreenBridge.replacing()) {
            return;
        }
        long now = Util.getMeasuringTimeMs();
        // The same two clocks the game fades by: a second after finishing before
        // the fade out starts, a second to fade out, and half a second to fade a
        // reload in over the screen it started from.
        float sinceFinished = reloadCompleteTime > -1L ? (now - reloadCompleteTime) / 1000f : -1f;
        float sinceStarted = reloadStartTime > -1L ? (now - reloadStartTime) / 500f : -1f;

        float opacity;
        if (sinceFinished >= 1f) {
            opacity = 1f - clamp(sinceFinished - 1f);
        } else if (reloading) {
            opacity = clamp(sinceStarted);
        } else {
            opacity = 1f;
        }
        float finished = sinceFinished < 0f ? 0f : clamp(sinceFinished);
        LoadingScreenBridge.draw(context, clamp(progress), opacity, finished);
    }

    private static float clamp(float value) {
        return Math.max(0f, Math.min(1f, value));
    }
}
