package dev.px.karatsuba.adapter.screen;

import dev.px.core.Core;
import dev.px.core.render.Render;
import dev.px.karatsuba.adapter.MinecraftAdapter;
import dev.px.karatsuba.adapter.render.nanovg.NanoVGRender2D;
import net.minecraft.client.gui.DrawContext;

/**
 * Where the game's loading screen hands over to the client's {@link LoadingScreen}.
 *
 * <p>{@code SplashOverlayMixin} asks {@link #replacing()} before each of the
 * game's draws and skips it while a screen is installed, then calls
 * {@link #draw} once the game's own logic has run for the frame. Kept out of the
 * mixin so the mixin only says where, and this says what.
 */
public final class LoadingScreenBridge {

    private LoadingScreenBridge() {
    }

    /** @return whether the game's own loading screen draws are to be skipped */
    public static boolean replacing() {
        return LoadingScreen.installed() != null && MinecraftAdapter.render2D() != null;
    }

    /**
     * Draws the installed screen over what the game drew under it this frame.
     *
     * <p>A screen that throws is uninstalled, so the next frame is the game's own
     * loading screen rather than a blank one for as long as loading takes.
     */
    public static void draw(DrawContext context, float progress, float opacity, float finished) {
        LoadingScreen screen = LoadingScreen.installed();
        NanoVGRender2D backend = MinecraftAdapter.render2D();
        if (screen == null || backend == null || opacity <= 0f || backend.isInFrame()) {
            return;
        }

        // The game batches its GUI draws; flush the ones under the loading screen
        // (the title screen it fades over) so they land under it, not on top.
        context.draw();

        float width = Core.platform().getScreenWidth();
        float height = Core.platform().getScreenHeight();
        Render.begin2D(width, height, Core.platform().getScreenScale());
        try {
            screen.render(width, height, progress, opacity, finished);
        } catch (RuntimeException failure) {
            LoadingScreen.install(null);
            Core.log().error("Loading screen " + screen.getClass().getSimpleName()
                    + " threw while drawing; the game's own is back", failure);
        } finally {
            Render.end2D();
        }
    }
}
