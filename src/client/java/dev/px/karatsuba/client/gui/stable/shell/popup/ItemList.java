package dev.px.karatsuba.client.gui.stable.shell.popup;

import dev.px.core.input.MouseButton;
import dev.px.core.layout.Bounds;
import dev.px.gui.Widget;
import dev.px.karatsuba.adapter.game.item.ItemCatalog;
import dev.px.karatsuba.client.setting.ItemSearch;
import dev.px.karatsuba.client.setting.ItemSetting;

import java.util.List;

/**
 * Every item matching the search, one row each, with its own scrolling.
 *
 * <p>One widget rather than a widget per item: the game has well over a thousand,
 * and only the eight or so in view are drawn. The order is worked out when the
 * search changes, never while you click, so ticking an item never moves the row
 * under the cursor.
 */
public final class ItemList extends Widget {

    public static final float ROW = 16f;
    public static final int VISIBLE_ROWS = 8;

    public final ItemSetting setting;

    private String query = "";
    private boolean selectedOnly;
    private List<ItemCatalog.Entry> shown = List.of();
    private List<ItemCatalog.Entry> builtFrom;

    /** How far down the list is scrolled, in pixels, and where the wheel wants it. */
    public float offset;
    private float target;

    public ItemList(ItemSetting setting) {
        this.setting = setting;
    }

    // ------------------------------------------------------------ filtering

    public void query(String text) {
        query = text;
        refilter();
    }

    public boolean isSelectedOnly() {
        return selectedOnly;
    }

    public void toggleSelectedOnly() {
        selectedOnly = !selectedOnly;
        refilter();
    }

    /** Ticks the best match: Enter in the search box. */
    public void tickFirst() {
        List<ItemCatalog.Entry> entries = shown();
        if (!entries.isEmpty()) {
            setting.toggle(entries.get(0).getId());
        }
    }

    public List<ItemCatalog.Entry> shown() {
        if (builtFrom != ItemSearch.catalogue(setting)) {
            refilter();
        }
        return shown;
    }

    private void refilter() {
        builtFrom = ItemSearch.catalogue(setting);
        shown = ItemSearch.filter(setting, builtFrom, query, selectedOnly);
        offset = target = 0f;
    }

    // ------------------------------------------------------------- scrolling

    public float maxOffset() {
        return Math.max(0f, shown().size() * ROW - VISIBLE_ROWS * ROW);
    }

    /** Eases {@link #offset} toward where the wheel left it; once a frame, from the renderer. */
    public void ease() {
        target = Math.max(0f, Math.min(target, maxOffset()));
        offset += (target - offset) * 0.35f;
        if (Math.abs(target - offset) < 0.5f) {
            offset = target;
        }
    }

    @Override
    protected boolean mouseScrolled(float x, float y, float amount) {
        target -= amount * ROW * 3f;
        return true;
    }

    // ----------------------------------------------------------------- input

    @Override
    protected boolean mousePressed(float x, float y, MouseButton button) {
        Bounds rows = part("rows");
        if (button != MouseButton.LEFT || rows == null || !rows.contains(x, y)) {
            return true;
        }
        int index = (int) ((y - rows.getY() + offset) / ROW);
        List<ItemCatalog.Entry> entries = shown();
        if (index >= 0 && index < entries.size()) {
            setting.toggle(entries.get(index).getId());
        }
        return true;
    }
}
