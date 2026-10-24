package dev.px.karatsuba.adapter.mixin.render;

import dev.px.karatsuba.adapter.event.GameEventBridge;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.hud.ChatHud;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Where the chat is drawn, so the client's HUD can go under it.
 *
 * <p>One method serves both chats: {@code InGameHud} calls it while playing and
 * {@code ChatScreen} while typing (1.21.4). At HEAD, before the game's own check
 * for chat being hidden, so the HUD is drawn either way. What is drawn there is
 * {@link GameEventBridge#beforeChat}'s to decide.
 */
@Mixin(ChatHud.class)
public abstract class ChatHudMixin {

    @Inject(method = "render", at = @At("HEAD"))
    private void karatsuba$beforeChat(DrawContext context, int currentTick, int mouseX, int mouseY,
                                      boolean focused, CallbackInfo callback) {
        GameEventBridge.beforeChat(context);
    }
}
