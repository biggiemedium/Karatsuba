package dev.px.karatsuba.adapter.mixin.game;

import dev.px.karatsuba.adapter.event.ScreenBridge;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.Screen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * The screen swap, for the same reason as the input mixins: Fabric API exposes no
 * hook that can see it whole or refuse it.
 *
 * <p>HEAD and cancellable, so Core is told while the change can still be stopped
 * and while {@code currentScreen} is still the outgoing one.
 */
@Mixin(MinecraftClient.class)
public abstract class MinecraftClientMixin {

    @Shadow
    public Screen currentScreen;

    @Inject(method = "setScreen", at = @At("HEAD"), cancellable = true)
    private void karatsuba$setScreen(Screen screen, CallbackInfo callback) {
        if (ScreenBridge.onScreenChange(currentScreen, screen)) {
            callback.cancel();
        }
    }
}
