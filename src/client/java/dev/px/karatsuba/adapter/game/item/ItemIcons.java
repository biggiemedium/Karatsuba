package dev.px.karatsuba.adapter.game.item;

import dev.px.karatsuba.adapter.render.GuiFrame;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;

/**
 * Draws an item's icon on the screen, as the inventory would.
 *
 * <p>Includes the game's own overlay: the stack count and the durability bar. Only
 * works inside a {@code Render2DEvent} handler, where the game's draw context is
 * open; anywhere else it quietly draws nothing.
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
        DrawContext context = GuiFrame.current();
        if (context == null || item.isEmpty()) {
            return;
        }
        float scale = size / NATIVE_SIZE;

        context.getMatrices().push();
        context.getMatrices().translate(x, y, 0f);
        context.getMatrices().scale(scale, scale, 1f);
        context.drawItem(item.stack(), 0, 0);
        context.drawStackOverlay(MinecraftClient.getInstance().textRenderer, item.stack(), 0, 0);
        context.getMatrices().pop();
    }
}
