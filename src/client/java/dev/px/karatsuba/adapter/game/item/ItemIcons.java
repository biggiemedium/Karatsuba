package dev.px.karatsuba.adapter.game.item;

import dev.px.core.render.Render;
import dev.px.karatsuba.adapter.render.GuiFrame;
import dev.px.karatsuba.adapter.render.nanovg.NanoVGRender2D;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;

import java.util.function.Consumer;

/**
 * Draws an item's icon on the screen, as the inventory would.
 *
 * <p>{@link #draw} includes the game's own overlay: the stack count and the
 * durability bar. Only works inside a {@code Render2DEvent} handler, where the
 * game's draw context is open; anywhere else it quietly draws nothing.
 *
 * <p>In this package rather than {@code adapter.render} because it needs the
 * stack inside a {@link GameItem}, which is kept package-private so a module can
 * never reach it.
 */
public final class ItemIcons {

    /** Icons are 16 pixels square at scale 1, as in the inventory. */
    public static final float NATIVE_SIZE = 16f;

    private ItemIcons() {
    }

    /**
     * @param x    left edge, in GUI pixels
     * @param y    top edge, in GUI pixels
     * @param size width and height, in GUI pixels; {@link #NATIVE_SIZE} is inventory size
     */
    public static void draw(GameItem item, float x, float y, float size) {
        if (GuiFrame.current() == null || item.isEmpty()) {
            return;
        }
        // Drawn once the frame's NanoVG content is painted, so on top of it: the
        // game must not draw inside a NanoVG frame (see GuiFrame).
        GuiFrame.defer(context -> {
            float scale = size / NATIVE_SIZE;
            context.getMatrices().push();
            context.getMatrices().translate(x, y, 0f);
            context.getMatrices().scale(scale, scale, 1f);
            context.drawItem(item.stack(), 0, 0);
            context.drawStackOverlay(MinecraftClient.getInstance().textRenderer, item.stack(), 0, 0);
            context.getMatrices().pop();
        });
    }

    /**
     * Draws catalogue icons on top of everything drawn so far this frame, cut off
     * at a rectangle.
     *
     * <p>For a list inside our own GUI. The game draws an item the moment it is
     * asked, while NanoVG paints its whole frame at the end, so an icon drawn into
     * a window would land under the window's own background. This paints what
     * NanoVG has so far first, so the icons sit on the list and anything drawn
     * afterwards (a tooltip, a window dragged on top) still covers them.
     *
     * <p>NanoVG's clip does not reach the game's draws, so the game's own scissor
     * cuts the icons off instead. Call it with no Core clip, transform or alpha
     * pushed: the frame cannot be stepped out of with one open, and then nothing is
     * drawn, because a game draw inside a NanoVG frame corrupts the game's GL state.
     *
     * @param body draws the icons through the {@link Layer} it is handed
     */
    public static void drawLayer(float clipX, float clipY, float clipWidth, float clipHeight,
                                 Consumer<Layer> body) {
        DrawContext context = GuiFrame.current();
        if (context == null || clipWidth <= 0f || clipHeight <= 0f) {
            return;
        }
        NanoVGRender2D nanovg = Render.backend2D() instanceof NanoVGRender2D
                ? (NanoVGRender2D) Render.backend2D() : null;
        if (nanovg != null && !nanovg.suspend()) {
            // State is pushed, so the frame cannot be stepped out of; drawing now
            // would corrupt the game's GL state. Better no icons than garbage.
            return;
        }
        try {
            context.enableScissor(Math.round(clipX), Math.round(clipY),
                    Math.round(clipX + clipWidth), Math.round(clipY + clipHeight));
            try {
                body.accept((entry, x, y, size) -> {
                    float scale = size / NATIVE_SIZE;
                    context.getMatrices().push();
                    context.getMatrices().translate(x, y, 0f);
                    context.getMatrices().scale(scale, scale, 1f);
                    context.drawItem(entry.stack(), 0, 0);
                    context.getMatrices().pop();
                });
            } finally {
                context.disableScissor();
                // Flush the game's batch while still out of the frame.
                context.draw();
            }
        } finally {
            if (nanovg != null) {
                nanovg.resume();
            }
        }
    }

    /** Where {@link #drawLayer} icons are drawn, for as long as its body runs. */
    @FunctionalInterface
    public interface Layer {

        /**
         * @param x    left edge, in GUI pixels
         * @param y    top edge, in GUI pixels
         * @param size width and height, in GUI pixels
         */
        void draw(ItemCatalog.Entry entry, float x, float y, float size);
    }
}
