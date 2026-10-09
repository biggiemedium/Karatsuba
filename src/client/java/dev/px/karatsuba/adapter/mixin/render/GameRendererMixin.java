package dev.px.karatsuba.adapter.mixin.render;

import dev.px.karatsuba.adapter.event.Events;
import dev.px.karatsuba.adapter.event.game.render.EffectRenderEvent;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import dev.px.karatsuba.adapter.event.game.render.EffectRenderEvent.Effect;
import dev.px.karatsuba.adapter.render.shader.Silhouettes;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.render.GameRenderer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.item.HeldItemRenderer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.MathHelper;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * The two camera effects applied while the world's view matrix is built: the
 * roll when hurt and the nausea warp.
 */
@Mixin(GameRenderer.class)
public abstract class GameRendererMixin {

    @Inject(method = "tiltViewWhenHurt", at = @At("HEAD"), cancellable = true)
    private void karatsuba$tiltViewWhenHurt(MatrixStack matrices, float tickDelta, CallbackInfo callback) {
        if (Events.cancelled(new EffectRenderEvent(Effect.HURT_TILT))) {
            callback.cancel();
        }
    }

    /**
     * The held items: drawn as the game draws them, then {@code HandRenderEvent},
     * with a way for {@code Silhouettes} to draw them again exactly the same into
     * its mask (same matrices, same lightmap, which is still enabled here).
     */
    @WrapOperation(method = "renderHand", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/render/item/HeldItemRenderer;renderItem(FLnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/VertexConsumerProvider$Immediate;Lnet/minecraft/client/network/ClientPlayerEntity;I)V"))
    private void karatsuba$renderHands(HeldItemRenderer renderer, float tickDelta, MatrixStack matrices,
                                       VertexConsumerProvider.Immediate vertexConsumers, ClientPlayerEntity player,
                                       int light, Operation<Void> original) {
        original.call(renderer, tickDelta, matrices, vertexConsumers, player, light);
        Silhouettes.afterHands(() -> original.call(renderer, tickDelta, matrices, vertexConsumers, player, light));
    }

    /**
     * The nausea intensity the world warp is scaled by.
     *
     * <p>{@code renderWorld} lerps the player's nausea between ticks exactly once,
     * and the warp is skipped entirely when the result is zero &mdash; so answering
     * zero here is the vanilla "not nauseous" path rather than a warp of size
     * nothing. Only asked when there is a warp to suppress, so an ordinary frame
     * posts nothing.
     */
    @Redirect(method = "renderWorld",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/util/math/MathHelper;lerp(FFF)F"))
    private float karatsuba$nauseaIntensity(float delta, float start, float end) {
        float intensity = MathHelper.lerp(delta, start, end);
        if (intensity > 0f && Events.cancelled(new EffectRenderEvent(Effect.NAUSEA))) {
            return 0f;
        }
        return intensity;
    }
}
