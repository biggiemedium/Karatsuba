package dev.px.karatsuba.adapter.mixin;

import dev.px.karatsuba.adapter.input.InputBridge;
import net.minecraft.client.Keyboard;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * The one place a physical key becomes a Core event.
 *
 * <p>Fabric API has no raw keyboard event, and the alternative &mdash; polling
 * {@code InputUtil} once a tick &mdash; would miss taps shorter than 50ms and give
 * every bind a lurching feel. Injecting at the GLFW callback catches the press
 * itself.
 *
 * <p>The injection is at HEAD so Core is told before the game acts, which is what
 * lets a cancelled event stop the game acting at all.
 */
@Mixin(Keyboard.class)
public final class KeyboardMixin {

    @Inject(method = "onKey", at = @At("HEAD"), cancellable = true)
    private void karatsuba$onKey(long window, int key, int scanCode, int action, int modifiers,
                                 CallbackInfo callback) {
        if (InputBridge.onKey(key, action, modifiers)) {
            callback.cancel();
        }
    }

    @Inject(method = "onChar", at = @At("HEAD"), cancellable = true)
    private void karatsuba$onChar(long window, int codePoint, int modifiers, CallbackInfo callback) {
        if (InputBridge.onChar(codePoint)) {
            callback.cancel();
        }
    }
}
