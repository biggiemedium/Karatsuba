package dev.px.karatsuba.client.gui.apple.shell.card;

import dev.px.core.layout.Bounds;
import dev.px.gui.Widget;
import dev.px.gui.container.Column;
import dev.px.karatsuba.client.gui.apple.shell.setting.ControlRow;

import java.util.ArrayList;
import java.util.List;

/**
 * A rounded card of rows in an inset grouped list ({@code gui/apple/DESIGN.md}
 * §2.4): rows edge to edge, a hairline between each pair starting at the label,
 * none after the last.
 *
 * <p>The card draws what spans its rows, so they need not know where they sit:
 * its background, the hairlines, and the hover highlight, whose corners follow
 * the card's on the first and last rows (§3.4). A row added here is told it is
 * {@linkplain CardRow#markCarded() carded} and draws no background of its own.
 */
public class Card extends Column {

    @Override
    public <W extends Widget> W add(W child) {
        if (child instanceof CardRow row) {
            row.markCarded();
        }
        return super.add(child);
    }

    /** @return the rows showing, top to bottom, as the last update laid them out */
    public List<Widget> rows() {
        List<Widget> shown = new ArrayList<>();
        for (Widget child : getChildren()) {
            Bounds bounds = child.getBounds();
            if (child.isVisible() && bounds != null && bounds.getHeight() > 0f) {
                shown.add(child);
            }
        }
        return shown;
    }

    /** @return how strongly {@code row} is highlighted, 0 to 1: a setting row's eased hover, or plain hover */
    public static float highlight(Widget row) {
        if (row instanceof ControlRow control) {
            return control.hover();
        }
        return row.isHovered() ? 1f : 0f;
    }
}
