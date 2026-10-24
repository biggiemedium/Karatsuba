package dev.px.karatsuba.adapter.mixin.render;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import dev.px.karatsuba.adapter.screen.ChatChromeBridge;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ChatInputSuggestor;
import net.minecraft.client.gui.screen.ChatScreen;
import net.minecraft.client.gui.widget.TextFieldWidget;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.lwjgl.glfw.GLFW;

/**
 * The chat screen, with the client's {@code ChatChrome} on its input bar: the
 * bar's slide and chrome, and the client's prediction in its suggestion text.
 *
 * <p>Only the input bar's three draws are wrapped &mdash; its fill, the text
 * field, and the suggestions over it &mdash; and each is moved down by the slide
 * while the chat opens. The messages above are left where they are: they were on
 * screen before the chat opened, and moving them would make them jump. Nothing of
 * the game's logic is touched, so typing, history and suggestions work as they do
 * without the client.
 *
 * <p>The bar's rectangle is the one the game fills, read off the fill call rather
 * than recomputed, so the chrome is drawn exactly where the bar is.
 */
@Mixin(ChatScreen.class)
public abstract class ChatScreenMixin {

    @Shadow
    protected TextFieldWidget chatField;

    @Shadow
    ChatInputSuggestor chatInputSuggestor;

    /** How far down the bar is drawn this frame. Read once per frame, so its draws agree. */
    @Unique
    private float karatsuba$slide;

    /** The bar as the game filled it this frame, slide included. */
    @Unique
    private float karatsuba$barX;
    @Unique
    private float karatsuba$barY;
    @Unique
    private float karatsuba$barWidth;
    @Unique
    private float karatsuba$barHeight;

    /** Once per opening: a resize re-runs {@code init}, but never the constructor. */
    @Inject(method = "<init>", at = @At("TAIL"))
    private void karatsuba$opened(String originalChatText, CallbackInfo callback) {
        ChatChromeBridge.opened();
    }

    /**
     * The prediction, in the field's grey suggestion text. At TAIL, after the
     * game's suggestor has refreshed, which is what clears that text; while there
     * is no prediction the suggestor's own is left alone.
     */
    @Inject(method = "onChatFieldUpdate", at = @At("TAIL"))
    private void karatsuba$predict(String chatText, CallbackInfo callback) {
        String suggestion = ChatChromeBridge.suggestion(chatField.getText());
        if (suggestion != null) {
            chatField.setSuggestion(suggestion);
        }
    }

    /**
     * Tab takes the prediction, as it takes the game's own completion. Only with
     * the cursor at the end, where the prediction is shown, and only while the
     * game's suggestion list is closed, so Tab still cycles that list.
     */
    @Inject(method = "keyPressed", at = @At("HEAD"), cancellable = true)
    private void karatsuba$accept(int keyCode, int scanCode, int modifiers, CallbackInfoReturnable<Boolean> callback) {
        String text = chatField.getText();
        if (keyCode != GLFW.GLFW_KEY_TAB || chatInputSuggestor.isOpen() || chatField.getCursor() != text.length()) {
            return;
        }
        String predicted = ChatChromeBridge.predict(text);
        if (predicted != null) {
            // Firing the field's change listener, so the next word is predicted in turn.
            chatField.setText(predicted);
            chatField.setCursorToEnd(false);
            callback.setReturnValue(true);
        }
    }

    @Inject(method = "render", at = @At("HEAD"))
    private void karatsuba$frame(DrawContext context, int mouseX, int mouseY, float delta, CallbackInfo callback) {
        karatsuba$slide = ChatChromeBridge.slide();
    }

    @WrapOperation(method = "render", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/gui/DrawContext;fill(IIIII)V"))
    private void karatsuba$bar(DrawContext context, int x1, int y1, int x2, int y2, int color,
                               Operation<Void> original) {
        karatsuba$barX = x1;
        karatsuba$barY = y1 + karatsuba$slide;
        karatsuba$barWidth = x2 - x1;
        karatsuba$barHeight = y2 - y1;
        karatsuba$slid(context, () -> original.call(context, x1, y1, x2, y2, color));
    }

    /** The text field, then the client's chrome over the bar it sits in. */
    @WrapOperation(method = "render", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/gui/widget/TextFieldWidget;render(Lnet/minecraft/client/gui/DrawContext;IIF)V"))
    private void karatsuba$field(TextFieldWidget field, DrawContext context, int mouseX, int mouseY, float delta,
                                 Operation<Void> original) {
        karatsuba$slid(context, () -> original.call(field, context, mouseX, mouseY, delta));
        ChatChromeBridge.drawInput(context, karatsuba$barX, karatsuba$barY,
                karatsuba$barWidth, karatsuba$barHeight, chatField.getText());
    }

    @WrapOperation(method = "render", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/gui/screen/ChatInputSuggestor;render(Lnet/minecraft/client/gui/DrawContext;II)V"))
    private void karatsuba$suggestions(ChatInputSuggestor suggestor, DrawContext context, int mouseX, int mouseY,
                                       Operation<Void> original) {
        karatsuba$slid(context, () -> original.call(suggestor, context, mouseX, mouseY));
    }

    @Unique
    private void karatsuba$slid(DrawContext context, Runnable draw) {
        if (karatsuba$slide == 0f) {
            draw.run();
            return;
        }
        context.getMatrices().push();
        context.getMatrices().translate(0f, karatsuba$slide, 0f);
        try {
            draw.run();
        } finally {
            context.getMatrices().pop();
        }
    }
}
