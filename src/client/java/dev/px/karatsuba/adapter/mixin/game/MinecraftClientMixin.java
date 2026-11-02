package dev.px.karatsuba.adapter.mixin.game;

import dev.px.karatsuba.adapter.event.ScreenBridge;
import dev.px.karatsuba.adapter.input.InputBridge;
import dev.px.karatsuba.adapter.render.shader.Silhouettes;
import dev.px.karatsuba.adapter.screen.DeathScreens;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.Framebuffer;
import net.minecraft.client.gui.screen.Screen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * The screen swap and window focus, for the same reason as the input mixins:
 * Fabric API exposes no hook that can see either whole or refuse it.
 *
 * <p>The screen swap is HEAD and cancellable, so Core is told while the change can
 * still be stopped and while {@code currentScreen} is still the outgoing one.
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

    /**
     * A death screen asked for by name: the server's death message calls
     * {@code setScreen(new DeathScreen(...))}. See {@code DeathScreens}.
     *
     * <p>Changed here, before the method reads it, and not at the
     * {@code currentScreen} write: by that instruction the screen is already on
     * the stack, so a change there reaches {@code init} but not the field, and
     * the game shows a screen it never initialised (Fabric's screen API crashes).
     */
    @ModifyVariable(method = "setScreen", argsOnly = true, at = @At("HEAD"))
    private Screen karatsuba$askedForDeathScreen(Screen screen) {
        return DeathScreens.onShow(screen);
    }

    /**
     * The death screen the game builds itself: every tick the player is dead with
     * no screen up, {@code setScreen(null)} swaps one in, which HEAD never sees.
     * Changed as it is stored, so everything after reads the replacement. The
     * other store, the title screen with no world, passes through untouched.
     */
    @ModifyVariable(method = "setScreen", argsOnly = true, at = @At("STORE"))
    private Screen karatsuba$builtDeathScreen(Screen screen) {
        return DeathScreens.onShow(screen);
    }

    /**
     * While {@code Silhouettes} captures, the main framebuffer is its mask: every
     * render layer binds whatever this returns before it draws, so this one answer
     * moves the game's own entity and item draws into the mask.
     */
    @Inject(method = "getFramebuffer", at = @At("HEAD"), cancellable = true)
    private void karatsuba$getFramebuffer(CallbackInfoReturnable<Framebuffer> callback) {
        Framebuffer mask = Silhouettes.redirect();
        if (mask != null) {
            callback.setReturnValue(mask);
        }
    }

    /**
     * A window that loses focus never hears the keys let go while it was away, so
     * every hold bind ends here rather than staying on until the key is pressed
     * again. Core 2.3 asks the adapter for exactly this.
     */
    @Inject(method = "onWindowFocusChanged", at = @At("HEAD"))
    private void karatsuba$focusChanged(boolean focused, CallbackInfo callback) {
        if (!focused) {
            InputBridge.onFocusLost();
        }
    }
}
