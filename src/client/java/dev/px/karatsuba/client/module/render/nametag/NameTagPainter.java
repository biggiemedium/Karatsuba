package dev.px.karatsuba.client.module.render.nametag;

import dev.px.core.render.Color;
import dev.px.core.render.Render;
import dev.px.karatsuba.adapter.game.item.GameItem;
import dev.px.karatsuba.adapter.game.item.ItemIcons;

import java.util.List;

/**
 * Lays out and draws one name tag on the screen.
 *
 * <p>Knows nothing about players, settings or the world: it is handed a point
 * on the screen, the text to show and the items to show, and draws them. That
 * split is what lets {@code NameTagsModule} read as a list of decisions and this
 * read as a list of measurements, and either change without the other.
 *
 * <p>Everything is laid out in local units around the anchor &mdash; the bottom
 * centre of the tag &mdash; and scaled once. From the bottom up:
 *
 * <pre>
 *      Pro4  Sha5          enchantments, stacked
 *       97%   84%          durability
 *     [icon][icon][icon]   held and worn items
 *    ┌──────────────────┐
 *    │ Name [S] 45ms 20 │  the info line
 *    └──────────────────┘
 *              ▲ anchor
 * </pre>
 *
 * <p>Item icons are drawn by the game, not by NanoVG, and the two are flushed
 * separately. Nothing here overlaps an icon for that reason: the text above an
 * icon sits clear of it, and the icon's own count and durability bar come from
 * the game's overlay, drawn with it.
 */
public final class NameTagPainter {

    private static final float PADDING = 2f;
    private static final float ICON_SIZE = ItemIcons.NATIVE_SIZE;
    private static final float ICON_GAP = 2f;

    /** Durability and enchantments are drawn at this fraction of the main text size. */
    private static final float SMALL_TEXT = 0.5f;

    private static final Color BACKGROUND = Color.of(0, 0, 0, 120);

    /**
     * Item icons are drawn by the game, which has no notion of NanoVG's global
     * alpha, so they cannot fade with the text. Instead they are shown only while
     * the tag is at least this opaque: they appear halfway through popping in and
     * leave halfway through fading out, which reads as part of the same motion.
     */
    private static final float ICON_MIN_ALPHA = 0.5f;

    /** One run of the info line in a single colour, so a name and a health value can differ. */
    public record Segment(String text, Color color) {
    }

    /** One line of small text over an item. */
    public record Note(String text, Color color) {
    }

    /** An item with the notes to stack over it, nearest the icon first. */
    public record ItemColumn(GameItem item, List<Note> notes) {
    }

    /**
     * @param anchorX screen X of the tag's bottom centre, in GUI pixels
     * @param anchorY screen Y of the tag's bottom centre, in GUI pixels
     * @param scale   1 draws at HUD text size
     * @param alpha   0 invisible to 1 opaque, applied to the whole tag
     * @param info    the info line, left to right; may be empty
     * @param items   the item row, left to right; may be empty
     */
    public void paint(float anchorX, float anchorY, float scale, float alpha,
                      List<Segment> info, List<ItemColumn> items) {
        if (alpha <= 0f || scale <= 0f) {
            return;
        }
        float textHeight = Render.textHeight();
        float infoTop = info.isEmpty() ? 0f : -(textHeight + PADDING * 2f);

        // Outermost: NanoVG restores transforms along with alpha when it pops, so
        // every transform below has to open and close inside it.
        Render.pushAlpha(alpha);
        try {
            if (!info.isEmpty()) {
                Render.pushTransform();
                try {
                    Render.translate(anchorX, anchorY);
                    Render.scale(scale, scale);
                    paintInfo(info, infoTop, textHeight);
                } finally {
                    Render.popTransform();
                }
            }
            if (!items.isEmpty()) {
                paintItems(anchorX, anchorY, scale, items, infoTop - PADDING - ICON_SIZE, textHeight,
                        alpha >= ICON_MIN_ALPHA);
            }
        } finally {
            Render.popAlpha();
        }
    }

    private static void paintInfo(List<Segment> info, float top, float textHeight) {
        float width = 0f;
        for (Segment segment : info) {
            width += Render.textWidth(segment.text());
        }
        float x = -width / 2f;

        Render.rect(x - PADDING, top, width + PADDING * 2f, textHeight + PADDING * 2f, BACKGROUND);
        for (Segment segment : info) {
            Render.text(segment.text(), x, top + PADDING, segment.color());
            x += Render.textWidth(segment.text());
        }
    }

    /**
     * The item row and the notes over each item.
     *
     * <p>Icons go to the game in screen pixels, so their positions are scaled by
     * hand here rather than through the NanoVG transform the text uses.
     */
    private static void paintItems(float anchorX, float anchorY, float scale,
                                   List<ItemColumn> items, float iconTop, float textHeight,
                                   boolean icons) {
        float rowWidth = items.size() * ICON_SIZE + (items.size() - 1) * ICON_GAP;
        float left = -rowWidth / 2f;
        float noteHeight = textHeight * SMALL_TEXT;

        for (int i = 0; i < items.size(); i++) {
            ItemColumn column = items.get(i);
            float iconLeft = left + i * (ICON_SIZE + ICON_GAP);

            if (icons) {
                ItemIcons.draw(column.item(),
                        anchorX + iconLeft * scale, anchorY + iconTop * scale, ICON_SIZE * scale);
            }

            float noteBottom = iconTop - 1f;
            for (Note note : column.notes()) {
                noteBottom -= noteHeight;
                paintNote(note, anchorX, anchorY, scale, iconLeft + ICON_SIZE / 2f, noteBottom);
            }
        }
    }

    /** One line of small text, centred on {@code centerX}, with its top at {@code top}; local units. */
    private static void paintNote(Note note, float anchorX, float anchorY, float scale, float centerX, float top) {
        Render.pushTransform();
        try {
            Render.translate(anchorX + centerX * scale, anchorY + top * scale);
            Render.scale(scale * SMALL_TEXT, scale * SMALL_TEXT);
            Render.text(note.text(), -Render.textWidth(note.text()) / 2f, 0f, note.color());
        } finally {
            Render.popTransform();
        }
    }
}
