package dev.px.karatsuba.adapter.mixin.render;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import dev.px.karatsuba.adapter.screen.MenuPanorama;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.CubeMapRenderer;
import net.minecraft.client.gui.RotatingCubeMapRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * The menu panorama, with the client's faces in place of the game's.
 *
 * <p>The one place every menu out of a world draws its background through: the
 * title screen with its fade in, and {@code Screen.renderPanoramaBackground} for
 * the rest, which then blurs and darkens it. Only the cube is swapped; the turn,
 * its speed option and the vignette over it are the game's.
 */
@Mixin(RotatingCubeMapRenderer.class)
public abstract class RotatingCubeMapRendererMixin {

    @WrapOperation(method = "render", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/gui/CubeMapRenderer;draw(Lnet/minecraft/client/MinecraftClient;FFF)V"))
    private void karatsuba$cube(CubeMapRenderer cube, MinecraftClient client, float pitch, float yaw, float alpha,
                                Operation<Void> original) {
        CubeMapRenderer replacement = MenuPanorama.renderer();
        original.call(replacement != null ? replacement : cube, client, pitch, yaw, alpha);
    }
}
