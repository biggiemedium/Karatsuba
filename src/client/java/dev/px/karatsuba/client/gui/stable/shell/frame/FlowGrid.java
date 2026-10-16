package dev.px.karatsuba.client.gui.stable.shell.frame;

import dev.px.core.layout.Bounds;
import dev.px.core.layout.Size;
import dev.px.gui.Container;
import dev.px.gui.Widget;

import java.util.ArrayList;
import java.util.List;

/**
 * Cells in as many equal columns as fit, at least {@code minCell} wide each, so
 * the theme picker shows more themes a row as the window is made wider.
 */
public final class FlowGrid extends Container {

    private final float minCell;
    private final float gap;

    public FlowGrid(float minCell, float gap) {
        this.minCell = minCell;
        this.gap = gap;
    }

    private int columns(float width) {
        return Math.max(1, (int) ((width + gap) / (minCell + gap)));
    }

    private float cellWidth(float width) {
        int columns = columns(width);
        return (width - gap * (columns - 1)) / columns;
    }

    @Override
    protected float childrenHeight(List<Widget> visible, float width) {
        float height = 0f;
        for (float row : rowHeights(visible, width)) {
            height += row;
        }
        int rows = rowHeights(visible, width).size();
        return height + Math.max(0, rows - 1) * gap;
    }

    @Override
    protected Size childrenNatural(List<Widget> visible) {
        return Size.of(minCell, childrenHeight(visible, minCell));
    }

    @Override
    protected void arrange(List<Widget> visible, Bounds slot) {
        int columns = columns(slot.getWidth());
        float cell = cellWidth(slot.getWidth());
        List<Float> rows = rowHeights(visible, slot.getWidth());
        float y = slot.getY();
        for (int i = 0; i < visible.size(); i++) {
            int row = i / columns;
            int column = i % columns;
            if (column == 0 && row > 0) {
                y += rows.get(row - 1) + gap;
            }
            place(visible.get(i), Bounds.of(slot.getX() + column * (cell + gap), y, cell, rows.get(row)));
        }
    }

    private List<Float> rowHeights(List<Widget> visible, float width) {
        int columns = columns(width);
        float cell = cellWidth(width);
        List<Float> rows = new ArrayList<>();
        for (int i = 0; i < visible.size(); i++) {
            float height = heightOf(visible.get(i), cell);
            if (i % columns == 0) {
                rows.add(height);
            } else {
                rows.set(rows.size() - 1, Math.max(rows.get(rows.size() - 1), height));
            }
        }
        return rows;
    }
}
