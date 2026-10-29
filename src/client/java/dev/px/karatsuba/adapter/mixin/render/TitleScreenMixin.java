package dev.px.karatsuba.adapter.mixin.render;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import dev.px.karatsuba.adapter.screen.SignedInSkin;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.Element;
import net.minecraft.client.gui.PlayerSkinDrawer;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.TitleScreen;
import net.minecraft.client.gui.widget.PressableTextWidget;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.ColorHelper;
import net.minecraft.util.math.MathHelper;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.ArrayList;

/**
 * The title screen, cleaned up: no copyright line, no version line, its panorama
 * blurred like every other menu's, and who you are signed in as in the bottom
 * left.
 *
 * <ul>
 *   <li><b>Copyright:</b> the "Copyright Mojang AB. Do not distribute!" link in the
 *       bottom right is a widget {@code init} adds; it is taken back out at the end
 *       of {@code init}, so it is neither drawn nor clickable.</li>
 *   <li><b>Version:</b> "Minecraft 1.21.4/fabric (Modded)" in the bottom left is
 *       the one plain string {@code render} draws; that draw is skipped.</li>
 *   <li><b>Blur:</b> {@code Screen.renderBackground} blurs the panorama behind
 *       every other menu ({@code applyBlur}, by the game's Menu Background Blur
 *       option), but the title screen draws its panorama itself and never does.
 *       It is blurred here the same way, after the cube and before the vignette
 *       and the buttons, so a blur of 0 leaves it as the game draws it.</li>
 *   <li><b>Signed in as:</b> the account's face ({@link SignedInSkin}, with its
 *       hat layer) and "Logged in as: Name" to its right, where the version line
 *       was. It follows an account switch, and fades in with the buttons.</li>
 * </ul>
 */
@Mixin(TitleScreen.class)
public abstract class TitleScreenMixin extends Screen {

    @Shadow
    @Final
    private static Text COPYRIGHT;

    /** The face's size and its gap from the screen's corner and from the text, in GUI pixels. */
    @Unique
    private static final int FACE = 16;
    @Unique
    private static final int MARGIN = 4;

    /** The buttons' alpha this frame, as the screen's fade in set it. */
    @Unique
    private float karatsuba$alpha = 1f;

    protected TitleScreenMixin(Text title) {
        super(title);
    }

    @Inject(method = "init", at = @At("TAIL"))
    private void karatsuba$noCopyright(CallbackInfo ci) {
        for (Element child : new ArrayList<>(children())) {
            if (child instanceof PressableTextWidget link && link.getMessage() == COPYRIGHT) {
                remove(link);
            }
        }
    }

    @WrapOperation(method = "render", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/gui/DrawContext;drawTextWithShadow(Lnet/minecraft/client/font/TextRenderer;Ljava/lang/String;III)I"))
    private int karatsuba$noVersion(DrawContext context, TextRenderer renderer, String text, int x, int y, int color,
                                   Operation<Integer> original) {
        return 0;
    }

    @Inject(method = "renderPanoramaBackground", at = @At("TAIL"))
    private void karatsuba$blur(DrawContext context, float delta, CallbackInfo ci) {
        applyBlur();
    }

    @Inject(method = "setWidgetAlpha", at = @At("HEAD"))
    private void karatsuba$fade(float alpha, CallbackInfo ci) {
        karatsuba$alpha = alpha;
    }

    @Inject(method = "render", at = @At("TAIL"))
    private void karatsuba$signedIn(DrawContext context, int mouseX, int mouseY, float delta, CallbackInfo ci) {
        int alpha = MathHelper.ceil(karatsuba$alpha * 255f);
        // The game's own cut-off: text drawn with an alpha this low comes out opaque.
        if ((alpha << 24 & 0xFC000000) == 0) {
            return;
        }
        int x = MARGIN;
        int y = height - MARGIN - FACE;
        PlayerSkinDrawer.draw(context, SignedInSkin.current(), x, y, FACE, ColorHelper.withAlpha(alpha, 0xFFFFFF));

        Text line = Text.literal("Logged in as: ").formatted(Formatting.GRAY)
                .append(Text.literal(client.getSession().getUsername()).formatted(Formatting.WHITE));
        int textY = y + (FACE - textRenderer.fontHeight) / 2 + 1;
        context.drawTextWithShadow(textRenderer, line, x + FACE + MARGIN, textY, ColorHelper.withAlpha(alpha, 0xFFFFFF));
    }
}
