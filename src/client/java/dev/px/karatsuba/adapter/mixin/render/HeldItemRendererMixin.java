package dev.px.karatsuba.adapter.mixin.render;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import dev.px.karatsuba.adapter.game.item.ShownSlot;
import net.minecraft.item.ItemStack;
import net.minecraft.client.render.item.HeldItemRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * The first-person main hand drawn with the {@link ShownSlot}'s item.
 *
 * <p>{@code updateHeldItems} reads the main-hand stack once a tick into the field
 * every first-person draw uses, and judges the swap animation from it, so a hidden
 * slot change neither shows the item nor dips the hand.
 */
@Mixin(HeldItemRenderer.class)
public abstract class HeldItemRendererMixin {

    @ModifyExpressionValue(method = "updateHeldItems",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/client/network/ClientPlayerEntity;getMainHandStack()Lnet/minecraft/item/ItemStack;"))
    private ItemStack karatsuba$shownStack(ItemStack held) {
        return ShownSlot.mainHand(held);
    }
}
