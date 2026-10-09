package dev.px.karatsuba.client.gui.shell.section.settings;

import dev.px.core.Core;
import dev.px.core.input.Bind;
import dev.px.core.module.Module;
import dev.px.core.setting.Setting;
import dev.px.core.setting.impl.BindSetting;
import dev.px.gui.Widget;
import dev.px.gui.container.Row;
import dev.px.karatsuba.client.config.AutoSave;
import dev.px.karatsuba.client.gui.shell.Section;
import dev.px.karatsuba.client.gui.shell.Shell;
import dev.px.karatsuba.client.gui.shell.frame.Reveal;
import dev.px.karatsuba.client.gui.shell.frame.SettingsColumn;
import dev.px.karatsuba.client.gui.shell.frame.Sidebar;
import dev.px.karatsuba.client.gui.shell.frame.SmoothScroll;
import dev.px.karatsuba.client.gui.shell.frame.Spacer;
import dev.px.karatsuba.client.gui.shell.frame.WindowFrame;
import dev.px.karatsuba.client.gui.shell.rail.Glyph;
import dev.px.karatsuba.client.gui.shell.setting.BindRow;
import dev.px.karatsuba.client.gui.shell.setting.ControlRow;
import dev.px.karatsuba.client.gui.shell.setting.SettingList;
import dev.px.karatsuba.client.gui.shell.style.Palette;
import dev.px.karatsuba.client.gui.shell.widget.Caption;
import dev.px.karatsuba.client.gui.shell.widget.PageIntro;
import dev.px.karatsuba.client.gui.shell.widget.NavItem;
import dev.px.karatsuba.client.gui.shell.widget.PillButton;
import dev.px.karatsuba.client.gui.shell.widget.SearchField;
import dev.px.karatsuba.client.gui.shell.widget.SectionHeader;
import dev.px.karatsuba.client.gui.shell.widget.SectionTitle;
import dev.px.karatsuba.client.gui.shell.widget.TextButton;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * The client's own settings, as opposed to a module's: how it acts, which keys
 * open what, how it talks to you.
 *
 * <p>A list of pages on the left, under a search box that looks through every
 * page at once; the chosen page on the right, its settings in one column under
 * its title and a sentence saying what it governs. Every setting shows its
 * description under its name, so the page can be read rather than hovered. A
 * setting changed from its default is marked with a bar in the accent and gets a
 * reset button; each page in the list counts its changed settings, and its foot
 * has a button to reset them all, which asks twice.
 *
 * <p>Changes apply as they are made, with nothing to save. The page last open is
 * the one shown next time.
 */
public final class SettingsSection implements Section {

    public static final String ID = "settings";

    /** How long "Reset" waits for its confirming second press. */
    private static final long CONFIRM_MILLIS = 3000L;

    private static String lastPage;

    private final Shell shell;
    private final Row content;
    private final Sidebar nav;
    private final SettingsColumn pane;
    private final SearchField search;
    private final List<SettingsPage> pages;
    private final SettingList settings = new SettingList(true);
    private final Reveal paneReveal = new Reveal();

    private SettingsPage page;
    private SettingsPage built;
    private String builtQuery;
    private long resetArmedAt;

    public SettingsSection(Shell shell, WindowFrame frame) {
        this.shell = shell;
        this.content = new Row();
        this.pages = SettingsPages.create();
        settings.decorate(row -> {
            if (row instanceof BindRow bind) {
                row.note(() -> clash(bind.setting()));
            }
        });

        // ---- the pages, under the section's title and the search
        this.nav = content.add(new Sidebar(frame));
        nav.gap(Palette.GAP_ROW);
        nav.add(new SectionTitle("Settings", null));
        nav.add(new Spacer(Palette.INSET - Palette.GAP_ROW));
        this.search = nav.add(new SearchField("Search settings"));
        nav.add(new Spacer(Palette.INSET - Palette.GAP_ROW));
        SmoothScroll list = nav.add(new SmoothScroll());
        list.gap(Palette.GAP_ROW);
        list.grow(1f);
        for (SettingsPage each : pages) {
            list.add(new NavItem(each.title(), () -> changed(each),
                    () -> each == page && !searching(), () -> open(each)));
        }
        // About, at the foot: what this is and what it runs on.
        nav.add(new Caption(Core.get().getClientName() + " " + Core.get().getClientVersion()
                + "\n" + Core.platform().getGameVersion()));

        // ---- the chosen page
        this.pane = content.add(new SettingsColumn(paneReveal));
        pane.gap(Palette.GAP_ROW * 2f);
        pane.grow(1f);
        shell.veil(paneReveal, () -> pane);

        SettingsPage start = find(lastPage);
        page = start != null ? start : pages.isEmpty() ? null : pages.get(0);
    }

    // --------------------------------------------------------------- Section

    @Override
    public String id() {
        return ID;
    }

    @Override
    public String title() {
        return "Settings";
    }

    @Override
    public Glyph glyph() {
        return Glyph.SETTINGS;
    }

    @Override
    public Group group() {
        return Group.BOTTOM;
    }

    @Override
    public Widget content() {
        return content;
    }

    @Override
    public void tick() {
        float squeeze = Math.max(0f, Math.min(1f, (Palette.FRAME_WIDTH - shell.windowWidth()) / 280f));
        nav.width(Palette.LIST_WIDTH - 24f * squeeze);
        String query = query();
        if (built != page || !query.equals(builtQuery)) {
            rebuild(query);
        }
        settings.refresh();
    }

    @Override
    public boolean find() {
        search.grab();
        search.selectAll();
        return true;
    }

    @Override
    public boolean typed(char character) {
        if (Character.isISOControl(character) || search.isFocused()) {
            return false;
        }
        search.grab();
        search.insert(String.valueOf(character));
        return true;
    }

    @Override
    public boolean back() {
        // Escape clears a search before it leaves.
        if (searching()) {
            search.setText("");
            return true;
        }
        return false;
    }

    /** Opens with {@code query} in the search box: Home's way to a setting. */
    public void search(String query) {
        search.setText(query);
    }

    // ------------------------------------------------------------- internals

    private void open(SettingsPage next) {
        search.setText("");
        if (next != page) {
            paneReveal.start();
        }
        page = next;
        lastPage = next.id();
        resetArmedAt = 0L;
    }

    private String query() {
        return search.getText().trim().toLowerCase(Locale.ROOT);
    }

    private boolean searching() {
        return !query().isEmpty();
    }

    /** @return the page's count of changed settings, for the list, or empty when there are none */
    private static String changed(SettingsPage page) {
        int count = page.modified();
        return count == 0 ? "" : String.valueOf(count);
    }

    /** Fills the right-hand pane: the chosen page, or what the search found. Only when either changes. */
    private void rebuild(String query) {
        built = page;
        builtQuery = query;
        pane.clear();
        settings.clear();
        if (query.isEmpty()) {
            showPage();
        } else {
            showResults(query);
        }
    }

    private void showPage() {
        if (page == null) {
            pane.add(new Caption("No settings"));
            return;
        }
        SettingsPage shown = page;
        pane.add(new SectionTitle(shown.title(), null));
        pane.add(new PageIntro(shown.description()));
        pane.add(new Spacer(Palette.INSET - Palette.GAP_ROW * 2f));
        SmoothScroll rows = pane.add(new SmoothScroll());
        rows.gap(Palette.GAP_ROW);
        rows.grow(1f);
        if (shown.layout() != null) {
            for (Widget row : shown.layout().apply(settings::row)) {
                if (row instanceof ControlRow control) {
                    control.detailed();
                }
                rows.add(row);
            }
        } else {
            settings.build(shown.settings().get(), null, rows, null);
        }
        if (shown.note() != null) {
            rows.add(new Caption(shown.note()));
        }
        rows.add(new Spacer(Palette.GAP_SECTION));
        // In a row, so the button is as wide as its words rather than the page: a rare action, kept small.
        Row foot = rows.add(new Row());
        foot.add(new PillButton(Glyph.RESET, "Reset", false, PillButton.Style.PLAIN, null,
                () -> shown.modified() > 0, () -> resetPage(shown))
                .labelledBy(() -> resetLabel(shown)));
    }

    private void showResults(String query) {
        pane.add(new SectionTitle("Search", () -> "\"" + search.getText().trim() + "\""));
        pane.add(new Spacer(Palette.INSET - Palette.GAP_ROW * 2f));
        SmoothScroll rows = pane.add(new SmoothScroll());
        rows.gap(Palette.GAP_ROW);
        rows.grow(1f);
        boolean any = false;
        for (SettingsPage each : pages) {
            List<Setting<?>> matches = new ArrayList<>();
            boolean pageMatches = each.title().toLowerCase(Locale.ROOT).contains(query);
            for (Setting<?> setting : each.flat()) {
                if (pageMatches || matches(setting, query)) {
                    matches.add(setting);
                }
            }
            if (matches.isEmpty()) {
                continue;
            }
            if (any) {
                rows.add(new Spacer(Palette.GAP_SECTION));
            }
            any = true;
            rows.add(new SectionHeader(each.title(), null));
            for (Setting<?> setting : matches) {
                rows.add(settings.row(setting));
            }
        }
        if (!any) {
            rows.add(new Caption("No settings match \"" + search.getText().trim() + "\""));
            rows.add(new TextButton("Clear search", () -> false, () -> search.setText("")));
        }
    }

    private static boolean matches(Setting<?> setting, String query) {
        return setting.getName().toLowerCase(Locale.ROOT).contains(query)
                || setting.getDescription().toLowerCase(Locale.ROOT).contains(query);
    }

    /** Resets the page's changed settings, on a second press within three seconds. */
    private void resetPage(SettingsPage shown) {
        if (armed()) {
            resetArmedAt = 0L;
            shown.resetAll();
            AutoSave.markDirty();
        } else {
            resetArmedAt = System.currentTimeMillis();
        }
    }

    private String resetLabel(SettingsPage shown) {
        int count = shown.modified();
        if (count == 0) {
            return "Nothing to reset";
        }
        String what = count == 1 ? "1 setting" : count + " settings";
        return armed() ? "Click again to reset " + what : "Reset " + what + " to default";
    }

    private boolean armed() {
        return resetArmedAt != 0L && System.currentTimeMillis() - resetArmedAt < CONFIRM_MILLIS;
    }

    /**
     * @return a warning when another action or a module is on the same key, naming
     *         them; empty when the key is free or unbound
     */
    private static String clash(BindSetting bind) {
        if (!bind.isBound()) {
            return "";
        }
        String key = bind.get().getDisplay();
        List<String> others = new ArrayList<>();
        for (Setting<?> other : Core.input().getActionSettings()) {
            if (other != bind && other instanceof BindSetting action && same(action, key)) {
                others.add(action.getName());
            }
        }
        for (Module module : Core.modules().all()) {
            BindSetting toggle = module.getToggleBind();
            if (toggle != null && same(toggle, key)) {
                others.add(module.getName());
            }
        }
        return others.isEmpty() ? "" : "Also the key for " + String.join(", ", others);
    }

    private static boolean same(BindSetting candidate, String key) {
        Bind value = candidate.get();
        return candidate.isBound() && value.getDisplay().equals(key);
    }

    private SettingsPage find(String id) {
        for (SettingsPage each : pages) {
            if (each.id().equals(id)) {
                return each;
            }
        }
        return null;
    }
}
