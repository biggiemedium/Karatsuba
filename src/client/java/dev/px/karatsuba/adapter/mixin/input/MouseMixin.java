package dev.px.karatsuba.adapter.mixin.input;

import dev.px.karatsuba.adapter.input.InputBridge;
import net.minecraft.client.Mouse;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Mouse buttons and the wheel, for the same reason as {@code KeyboardMixin}:
 * Fabric API exposes no raw mouse event.
 *
 * <p>There is no cursor-move hook here and there does not need to be. Core follows
 * {@code Platform.getMouseX()} every frame while a drag is in flight, so a HUD
 * element being dragged tracks the cursor without any move event to bridge.
 */
@Mixin(Mouse.class)
public final class MouseMixin {

    @Inject(method = "onMouseButton", at = @At("HEAD"), cancellable = true)
    private void karatsuba$onMouseButton(long window, int button, int action, int modifiers,
                                         CallbackInfo callback) {
        if (InputBridge.onMouseButton(button, action, modifiers)) {
            callback.cancel();
        }
    }

    @Inject(method = "onMouseScroll", at = @At("HEAD"), cancellable = true)
    private void karatsuba$onMouseScroll(long window, double horizontal, double vertical,
                                         CallbackInfo callback) {
        if (InputBridge.onScroll(vertical)) {
            callback.cancel();
        }
    }
}
