package dev.px.karatsuba.adapter.render;

import net.minecraft.client.gui.DrawContext;

/**
 * The game's own 2D draw context, for the length of one HUD frame.
 *
 * <p>Almost everything the client draws in 2D goes through NanoVG and never needs
 * this. The exception is anything only the game can draw &mdash; an item's icon is
 * a 3D model rendered flat, with enchantment glint &mdash; and that needs the
 * context the game is drawing its own HUD with. The event bridge opens it around
 * {@code Render2DEvent} and closes it after, so outside that window there is none.
 *
 * <p>The game's draws are batched separately from NanoVG's and the two are not
 * interleaved: whichever flushes later is on top. So something drawn here should
 * not overlap NanoVG content it needs to sit above or below.
 */
public final class GuiFrame {

    private static DrawContext current;

    private GuiFrame() {
    }

    /** Called by the event bridge as a 2D frame starts. */
    public static void open(DrawContext context) {
        current = context;
    }

    /** Called by the event bridge as a 2D frame ends. */
    public static void close() {
        current = null;
    }

    /** @return the context for this frame, or null outside one */
    public static DrawContext current() {
        return current;
    }
}
