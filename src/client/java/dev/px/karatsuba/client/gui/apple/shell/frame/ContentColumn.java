package dev.px.karatsuba.client.gui.apple.shell.frame;

import dev.px.core.layout.Bounds;
import dev.px.core.layout.Size;
import dev.px.gui.Container;
import dev.px.gui.Widget;
import dev.px.karatsuba.client.gui.apple.shell.style.Palette;

import java.util.List;

/**
 * The content column ({@code gui/apple/DESIGN.md} §2.4): its child inset from the
 * pane by {@link Palette#INSET_CONTENT} each side, no wider than
 * {@link Palette#CONTENT_MAX_WIDTH}, and centred in whatever room is beyond that,
 * search fields and all. The header lines up with the same left edge
 * ({@link #span}).
 */
public final class ContentColumn extends Container {

    /**
     * @param room the width available beside the sidebar
     * @return the column's offset into that room, and its width
     */
    public static float[] span(float room) {
        float width = Math.max(0f, Math.min(room - Palette.INSET_CONTENT * 2f, Palette.CONTENT_MAX_WIDTH));
        return new float[]{(room - width) / 2f, width};
    }

    @Override
    protected float childrenHeight(List<Widget> visible, float available) {
        float height = 0f;
        for (Widget child : visible) {
            height = Math.max(height, heightOf(child, span(available)[1]));
        }
        return height;
    }

    @Override
    protected Size childrenNatural(List<Widget> visible) {
        float width = 0f;
        float height = 0f;
        for (Widget child : visible) {
            Size natural = naturalSizeOf(child);
            width = Math.max(width, natural.getWidth());
            height = Math.max(height, natural.getHeight());
        }
        return Size.of(width, height);
    }

    @Override
    protected void arrange(List<Widget> visible, Bounds slot) {
        float[] span = span(slot.getWidth());
        Bounds column = Bounds.of(slot.getX() + span[0], slot.getY(), span[1], slot.getHeight());
        for (Widget child : visible) {
            place(child, column);
        }
    }
}
