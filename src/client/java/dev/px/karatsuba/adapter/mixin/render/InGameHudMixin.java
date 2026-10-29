package dev.px.karatsuba.adapter.mixin.render;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import dev.px.karatsuba.adapter.event.Events;
import dev.px.karatsuba.adapter.event.game.render.EffectRenderEvent;
import dev.px.karatsuba.adapter.event.game.render.EffectRenderEvent.Effect;
import dev.px.karatsuba.adapter.game.item.ShownSlot;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.hud.InGameHud;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.item.ItemStack;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * The screen-space overlays drawn over the HUD, and the hotbar drawn on the
 * {@link ShownSlot}.
 *
 * <p>Each overlay is its own private method that the game only calls when the overlay is
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

    /** The hotbar's highlight on the {@link ShownSlot}: the only read of the selected slot there. */
    @ModifyExpressionValue(method = "renderHotbar",
            at = @At(value = "FIELD", target = "Lnet/minecraft/entity/player/PlayerInventory;selectedSlot:I",
                    opcode = Opcodes.GETFIELD))
    private int karatsuba$shownSlot(int selected) {
        return ShownSlot.selected(selected);
    }

    /** The item-name popup follows the {@link ShownSlot}, so a hidden slot change does not name the food. */
    @ModifyExpressionValue(method = "tick()V",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/entity/player/PlayerInventory;getMainHandStack()Lnet/minecraft/item/ItemStack;"))
    private ItemStack karatsuba$shownStack(ItemStack held) {
        return ShownSlot.mainHand(held);
    }
}
