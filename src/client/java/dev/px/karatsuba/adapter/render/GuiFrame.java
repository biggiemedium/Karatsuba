package dev.px.karatsuba.adapter.render;

import net.minecraft.client.gui.DrawContext;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * The game's own 2D draw context, for the length of one HUD frame.
 *
 * <p>Almost everything the client draws in 2D goes through NanoVG and never needs
 * this. The exception is anything only the game can draw &mdash; an item's icon is
 * a 3D model rendered flat, with enchantment glint &mdash; and that needs the
 * context the game is drawing its own HUD with. The event bridge opens it around
 * {@code Render2DEvent} and closes it after, so outside that window there is none.
 *
 * <p><b>The game must never draw inside an open NanoVG frame.</b> NanoVG's GL
 * state guard would undo the game's changes behind its state cache's back, and
 * the game's next draw would then use a binding that is not there. So a game draw
 * either steps out of the frame for a moment
 * ({@code NanoVGRender2D.suspend()}/{@code resume()}), or is {@linkplain #defer
 * deferred} until the frame has been painted, which also puts it on top.
 */
public final class GuiFrame {

    private static DrawContext current;

    private static final List<Consumer<DrawContext>> deferred = new ArrayList<>();

    private GuiFrame() {
    }

    /** Called by the event bridge as a 2D frame starts. */
    public static void open(DrawContext context) {
        current = context;
        deferred.clear();
    }

    /**
     * Called by the event bridge once the NanoVG frame has been painted: draws
     * what was deferred, then closes.
     */
    public static void close() {
        DrawContext context = current;
        try {
            if (context != null) {
                for (Consumer<DrawContext> draw : deferred) {
                    draw.accept(context);
                }
            }
        } finally {
            deferred.clear();
            current = null;
        }
    }

    /** @return the context for this frame, or null outside one */
    public static DrawContext current() {
        return current;
    }

    /**
     * Draws with the game once this frame's NanoVG content has been painted, on
     * top of it. Dropped outside a frame.
     */
    public static void defer(Consumer<DrawContext> draw) {
        if (current != null && draw != null) {
            deferred.add(draw);
        }
    }
}
