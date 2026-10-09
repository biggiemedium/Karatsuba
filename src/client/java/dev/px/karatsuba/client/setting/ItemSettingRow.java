package dev.px.karatsuba.client.setting;

import dev.px.core.Core;
import dev.px.gui.legacy.Component;
import dev.px.gui.legacy.GuiStyle;
import dev.px.gui.legacy.Screen;
import dev.px.gui.legacy.SettingComponent;
import dev.px.core.input.Key;
import dev.px.core.input.Modifier;
import dev.px.core.input.MouseButton;
import dev.px.core.layout.Align;
import dev.px.core.layout.Bounds;
import dev.px.core.layout.Content;
import dev.px.core.render.Color;
import dev.px.core.render.Render;
import dev.px.karatsuba.adapter.game.item.ItemCatalog;
import dev.px.karatsuba.adapter.game.item.ItemIcons;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * The picker for an {@link ItemSetting}: a search box over every item, each with
 * its icon and name, ticked on or off with a click.
 *
 * <p>Opens like a multi-choice row. Opening it puts the cursor in the search box,
 * so a player can type straight away; Enter ticks the top match, Escape clears the
 * search and then leaves the box.
 *
 * <h2>One list component, not a thousand rows</h2>
 * The game has well over a thousand items. A component per item would mean laying
 * out and hit-testing all of them every frame, so the list is one component that
 * draws only the rows in view and turns a click into a row index.
 *
 * <h2>The order holds still while you click</h2>
 * With no search, ticked items come first so a player sees their list at a glance.
 * That order is worked out when the picker opens and when the search changes, not
 * on every tick, so ticking an item never moves the row under the cursor.
 * "Selected only" works the same way: an item unticked there stays until the
 * search changes, so a slip of the mouse can be undone in place.
 */
public final class ItemSettingRow extends SettingComponent<ItemSetting> {

    private static final int VISIBLE_ROWS = 8;
    private static final float BOX = 5f;
    private static final int MAX_QUERY = 48;

    /** Rows moved by one notch of the wheel. */
    private static final float SCROLL_ROWS = 3f;

    /** How quickly the list catches up with the wheel; higher is snappier. */
    private static final float SCROLL_EASE = 18f;

    private final SearchField search;
    private final ItemList list;

    private boolean open;
    private String query = "";
    private boolean selectedOnly;

    /** What the list shows, in order. Rebuilt by {@link #refilter()} only. */
    private List<ItemCatalog.Entry> shown = new ArrayList<>();

    /** The catalogue {@link #shown} was built from; a new one (language change) rebuilds it. */
    private List<ItemCatalog.Entry> builtFrom;

    public ItemSettingRow(ItemSetting setting) {
        super(setting);
        this.search = add(new SearchField());
        this.list = add(new ItemList());
        add(new Footer());
    }

    @Override
    protected boolean showsChildren() {
        return open;
    }

    @Override
    protected float childIndent() {
        return GuiStyle.indent();
    }

    @Override
    protected void content(Content c) {
        header(c, open, row -> {
            value(row, getSetting().displayValue());
            caret(row, open);
        });
    }

    @Override
    protected boolean onClick(float x, float y, MouseButton button) {
        if (button != MouseButton.LEFT) {
            return false;
        }
        open = !open;
        if (open) {
            refilter();
            list.jumpToTop();
            search.take();
        }
        return true;
    }

    // ------------------------------------------------------------ filtering

    private void setQuery(String text) {
        if (text.equals(query)) {
            return;
        }
        query = text;
        refilter();
        list.jumpToTop();
    }

    private void setSelectedOnly(boolean only) {
        selectedOnly = only;
        refilter();
        list.jumpToTop();
    }

    /** Shown items, rebuilt first if the catalogue changed underneath them. */
    private List<ItemCatalog.Entry> shown() {
        if (builtFrom != catalogue()) {
            refilter();
        }
        return shown;
    }

    /** Items for an item picker; blocks, by block id, for a block picker. */
    private List<ItemCatalog.Entry> catalogue() {
        return getSetting().getScope() == ItemSetting.Scope.BLOCKS ? ItemCatalog.blocks() : ItemCatalog.all();
    }

    private void refilter() {
        List<ItemCatalog.Entry> catalogue = catalogue();
        builtFrom = catalogue;

        ItemSetting setting = getSetting();
        String[] terms = query.trim().toLowerCase(Locale.ROOT).split("\\s+");
        boolean searching = !query.trim().isEmpty();

        List<Ranked> matches = new ArrayList<>();
        for (int i = 0; i < catalogue.size(); i++) {
            ItemCatalog.Entry entry = catalogue.get(i);
            boolean selected = setting.contains(entry.getId());
            if (selectedOnly && !selected) {
                continue;
            }
            int rank = searching ? rank(entry, terms) : (selected ? 0 : 1);
            if (rank >= 0) {
                matches.add(new Ranked(entry, rank, i));
            }
        }
        matches.sort(Comparator.comparingInt((Ranked r) -> r.rank).thenComparingInt(r -> r.order));

        List<ItemCatalog.Entry> result = new ArrayList<>(matches.size());
        for (Ranked ranked : matches) {
            result.add(ranked.entry);
        }
        shown = result;
    }

    /**
     * @return how well an item matches every term, lower is better, or -1 for no match
     *
     * <p>Each term has to appear in the name or the id. Then: the name starting with
     * the first term beats a word in it starting with it, which beats the term
     * turning up anywhere. So "oak" lists Oak Planks, then Dark Oak Planks, then
     * anything with "oak" mid-word.
     */
    private static int rank(ItemCatalog.Entry entry, String[] terms) {
        String name = entry.getName().toLowerCase(Locale.ROOT);
        String id = entry.getId();
        for (String term : terms) {
            if (!name.contains(term) && !id.contains(term)) {
                return -1;
            }
        }
        String first = terms[0];
        if (name.startsWith(first)) {
            return 0;
        }
        if (name.contains(" " + first)) {
            return 1;
        }
        return 2;
    }

    private static final class Ranked {

        final ItemCatalog.Entry entry;
        final int rank;
        final int order;

        Ranked(ItemCatalog.Entry entry, int rank, int order) {
            this.entry = entry;
            this.rank = rank;
            this.order = order;
        }
    }

    // ---------------------------------------------------------------- parts

    /** Whether the cursor is over this component and nothing drawn above it. */
    private static boolean hovered(Component component) {
        Screen screen = component.getScreen();
        return screen != null
                && screen.componentAt(Core.platform().getMouseX(), Core.platform().getMouseY()) == component;
    }

    private static float textTop(float rowTop, float rowHeight) {
        return rowTop + (rowHeight - Render.textHeight()) / 2f;
    }

    /** The search box. */
    private final class SearchField extends Component {

        private static final long BLINK_MILLIS = 1060L;

        /** Takes keyboard focus, so typing goes here. */
        void take() {
            focus();
        }

        @Override
        protected void content(Content c) {
            c.align(Align.STRETCH);
            c.custom("field", 0f, GuiStyle.rowHeight(), this::paint);
        }

        private void paint(float x, float y, float w, float h) {
            boolean editing = isFocused();
            float radius = Math.min(GuiStyle.radius(), h / 2f);
            Color fill = editing ? GuiStyle.accent().withAlpha(50) : GuiStyle.background();
            Render.roundRect(x, y, w, h, radius, fill);
            // Outlined, so it reads as a box to type in rather than one more row.
            Render.roundRectOutline(x, y, w, h, radius, 1f, editing ? GuiStyle.accent() : GuiStyle.outline());

            float pad = GuiStyle.padding();
            float glass = h * 0.5f;
            paintMagnifier(x + pad, y + (h - glass) / 2f, glass, editing ? GuiStyle.text() : GuiStyle.textMuted());
            float textX = x + pad * 2f + glass;
            float room = x + w - pad - textX;
            String shownText;
            Color color;
            if (query.isEmpty()) {
                shownText = getSetting().getScope() == ItemSetting.Scope.BLOCKS
                        ? "Type to search blocks" : "Type to search items";
                color = GuiStyle.textMuted();
            } else {
                // Keep the end in view: that is where the typing is happening.
                shownText = query;
                while (shownText.length() > 1 && Render.textWidth(shownText + "_") > room) {
                    shownText = shownText.substring(1);
                }
                color = GuiStyle.text();
            }
            float textY = textTop(y, h);
            String text = shownText;
            Render.clipped(textX, y, room, h, () -> Render.text(text, textX, textY, color));

            if (editing && System.currentTimeMillis() % BLINK_MILLIS < BLINK_MILLIS / 2L) {
                float cursorX = textX + (query.isEmpty() ? 0f : Render.textWidth(text));
                Render.text("_", Math.min(cursorX, x + w - pad - Render.textWidth("_")), textY, GuiStyle.text());
            }
        }

        /** A magnifying glass in a {@code size} square: a ring and a handle. */
        private void paintMagnifier(float x, float y, float size, Color color) {
            float ring = size * 0.32f;
            float centerX = x + ring + 0.5f;
            float centerY = y + ring + 0.5f;
            Render.circleOutline(centerX, centerY, ring, 1f, color);
            float reach = ring * 0.72f;
            Render.line(centerX + reach, centerY + reach, x + size, y + size, 1.2f, color);
        }

        @Override
        protected boolean onClick(float x, float y, MouseButton button) {
            if (button == MouseButton.RIGHT) {
                setQuery("");
            }
            focus();
            return true;
        }

        @Override
        protected boolean onChar(char character) {
            if (character < ' ' || character == 127 || query.length() >= MAX_QUERY) {
                return false;
            }
            setQuery(query + character);
            return true;
        }

        @Override
        protected boolean onKey(Key key, Set<Modifier> modifiers) {
            switch (key) {
                case BACKSPACE:
                    if (modifiers.contains(Modifier.CTRL)) {
                        setQuery("");
                    } else if (!query.isEmpty()) {
                        setQuery(query.substring(0, query.length() - 1));
                    }
                    return true;
                case ESCAPE:
                    if (!query.isEmpty()) {
                        setQuery("");
                    } else {
                        Screen screen = getScreen();
                        if (screen != null) {
                            screen.clearFocus();
                        }
                    }
                    return true;
                case ENTER:
                case NUMPAD_ENTER:
                    List<ItemCatalog.Entry> entries = shown();
                    if (!entries.isEmpty()) {
                        getSetting().toggle(entries.get(0).getId());
                    }
                    return true;
                default:
                    // Swallowed while typing, so "r" in the search cannot also fire
                    // whatever "r" is bound to.
                    return true;
            }
        }
    }

    /** The items themselves: one component drawing only the rows in view. */
    private final class ItemList extends Component {

        /** First row in view, fractional while easing toward {@link #target}. */
        private float offset;
        private float target;
        private long lastFrameNanos;

        void jumpToTop() {
            offset = 0f;
            target = 0f;
        }

        private float rowHeight() {
            return GuiStyle.rowHeight();
        }

        private float maxOffset() {
            return Math.max(0, shown().size() - VISIBLE_ROWS);
        }

        @Override
        protected void content(Content c) {
            int rows = Math.max(1, Math.min(VISIBLE_ROWS, shown().size()));
            c.align(Align.STRETCH);
            c.custom("list", 0f, rows * rowHeight(), this::paint);
        }

        private void paint(float x, float y, float w, float h) {
            ease();
            List<ItemCatalog.Entry> entries = shown();
            ItemSetting setting = getSetting();
            float rowH = rowHeight();
            float pad = GuiStyle.padding();
            float icon = rowH - 3f;
            float boxX = x + pad;
            float iconX = boxX + BOX + pad;
            float textX = iconX + icon + pad;
            boolean scrolls = maxOffset() > 0f;
            float textRoom = x + w - textX - (scrolls ? pad + 2f : pad);

            Render.roundRect(x, y, w, h, Math.min(GuiStyle.radius(), rowH / 2f), GuiStyle.surface());

            if (entries.isEmpty()) {
                String message = selectedOnly && query.isEmpty() ? "Nothing selected" : "No matches";
                Render.text(message, x + pad, textTop(y, rowH), GuiStyle.textMuted());
                return;
            }

            int hoveredRow = hoveredIndex(x, y, w, h);
            int first = (int) Math.floor(offset);
            int last = Math.min(entries.size() - 1, (int) Math.ceil(offset + VISIBLE_ROWS));

            Render.pushClip(x, y, w, h);
            try {
                for (int i = first; i <= last; i++) {
                    ItemCatalog.Entry entry = entries.get(i);
                    float rowY = y + (i - offset) * rowH;
                    boolean on = setting.contains(entry.getId());
                    if (i == hoveredRow) {
                        Render.rect(x, rowY, w, rowH, GuiStyle.accent().withAlpha(40));
                    }
                    Render.roundRect(boxX, rowY + (rowH - BOX) / 2f, BOX, BOX, 1f,
                            on ? GuiStyle.accent() : GuiStyle.outline());
                    float textY = textTop(rowY, rowH);
                    Color color = on ? GuiStyle.text() : GuiStyle.textMuted();
                    Render.clipped(textX, rowY, textRoom, rowH,
                            () -> Render.text(entry.getName(), textX, textY, color));
                }
                if (scrolls) {
                    float trackH = h - 2f;
                    float thumbH = Math.max(6f, trackH * VISIBLE_ROWS / entries.size());
                    float thumbY = y + 1f + (trackH - thumbH) * (offset / maxOffset());
                    Render.roundRect(x + w - 3f, thumbY, 2f, thumbH, 1f, GuiStyle.outline());
                }
            } finally {
                Render.popClip();
            }

            // Last, with nothing pushed: the icons go over everything above.
            ItemIcons.drawLayer(x, y, w, h, layer -> {
                for (int i = first; i <= last; i++) {
                    layer.draw(entries.get(i), iconX, y + (i - offset) * rowH + 1.5f, icon);
                }
            });
        }

        private void ease() {
            long now = System.nanoTime();
            float seconds = lastFrameNanos == 0L ? 0f : Math.min(0.1f, (now - lastFrameNanos) / 1.0e9f);
            lastFrameNanos = now;

            target = Math.max(0f, Math.min(target, maxOffset()));
            offset += (target - offset) * (1f - (float) Math.exp(-SCROLL_EASE * seconds));
            if (Math.abs(target - offset) < 0.01f) {
                offset = target;
            }
        }

        /** @return the row index under the cursor, or -1 */
        private int hoveredIndex(float x, float y, float w, float h) {
            if (!hovered(this)) {
                return -1;
            }
            return indexAt(Core.platform().getMouseY(), y);
        }

        private int indexAt(float pointerY, float top) {
            int index = (int) Math.floor((pointerY - top) / rowHeight() + offset);
            return index >= 0 && index < shown().size() ? index : -1;
        }

        @Override
        protected boolean onClick(float x, float y, MouseButton button) {
            Bounds at = part("list");
            if (button != MouseButton.LEFT || at == null) {
                return true;
            }
            int index = indexAt(y, at.getY());
            if (index >= 0) {
                getSetting().toggle(shown().get(index).getId());
            }
            // Back to the search, so tick, type, tick needs no click in between.
            search.take();
            return true;
        }

        @Override
        protected boolean onScroll(float amount, float x, float y) {
            if (maxOffset() <= 0f) {
                return false;
            }
            target = Math.max(0f, Math.min(maxOffset(), target - amount * SCROLL_ROWS));
            return true;
        }

        /** The hovered item's id, which tells apart two items with the same name. */
        @Override
        public String getTooltip() {
            Bounds at = part("list");
            if (at == null) {
                return "";
            }
            int index = indexAt(Core.platform().getMouseY(), at.getY());
            return index < 0 ? "" : shown().get(index).getId();
        }
    }

    /**
     * "Selected only" on the left, "Clear" on the right.
     *
     * <p>Clear asks twice: a whitelist can take a while to build, and one stray
     * click should not undo it.
     */
    private final class Footer extends Component {

        private static final String ONLY = "Selected only";
        private static final String CLEAR = "Clear";
        private static final String CONFIRM = "Sure?";
        private static final long CONFIRM_MILLIS = 3_000L;

        /** When Clear was first clicked; 0 when it is not waiting for a second click. */
        private long armedAt;

        private boolean armed() {
            return armedAt != 0L && System.currentTimeMillis() - armedAt < CONFIRM_MILLIS;
        }

        @Override
        protected void content(Content c) {
            float rowH = GuiStyle.rowHeight();
            float pad = GuiStyle.padding();
            c.height(rowH).align(Align.STRETCH);
            c.row(r -> {
                r.grow().padding(pad, 0f).align(Align.CENTER);
                r.custom("only", BOX + pad + Render.textWidth(ONLY), rowH, (x, y, w, h) -> {
                    Render.roundRect(x, y + (h - BOX) / 2f, BOX, BOX, 1f,
                            selectedOnly ? GuiStyle.accent() : GuiStyle.outline());
                    Render.text(ONLY, x + BOX + pad, textTop(y, h),
                            selectedOnly ? GuiStyle.text() : GuiStyle.textMuted());
                });
                r.fill();
                if (!getSetting().isEmpty()) {
                    boolean confirming = armed();
                    String label = confirming ? CONFIRM : CLEAR;
                    r.custom("clear", Render.textWidth(label), rowH, (x, y, w, h) ->
                            Render.text(label, x, textTop(y, h),
                                    confirming ? GuiStyle.accent() : GuiStyle.textMuted()));
                }
            });
        }

        @Override
        protected boolean onClick(float x, float y, MouseButton button) {
            if (button != MouseButton.LEFT) {
                return true;
            }
            if (hitsPart("only", x, y)) {
                setSelectedOnly(!selectedOnly);
            } else if (hitsPart("clear", x, y)) {
                if (armed()) {
                    getSetting().clear();
                    armedAt = 0L;
                } else {
                    armedAt = System.currentTimeMillis();
                }
            }
            search.take();
            return true;
        }
    }
}
