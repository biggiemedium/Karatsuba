package dev.px.karatsuba.client.gui.apple.shell.section.settings;

import dev.px.core.Core;
import dev.px.core.input.Bind;
import dev.px.core.module.Module;
import dev.px.core.setting.Setting;
import dev.px.core.setting.impl.BindSetting;
import dev.px.gui.Widget;
import dev.px.gui.container.Column;
import dev.px.karatsuba.client.config.AutoSave;
import dev.px.karatsuba.client.gui.apple.shell.Section;
import dev.px.karatsuba.client.gui.apple.shell.Shell;
import dev.px.karatsuba.client.gui.apple.shell.card.Card;
import dev.px.karatsuba.client.gui.apple.shell.card.CardList;
import dev.px.karatsuba.client.gui.apple.shell.card.CardPage;
import dev.px.karatsuba.client.gui.apple.shell.card.PushButton;
import dev.px.karatsuba.client.gui.apple.shell.frame.PageStack;
import dev.px.karatsuba.client.gui.apple.shell.frame.WindowFrame;
import dev.px.karatsuba.client.gui.apple.shell.rail.Glyph;
import dev.px.karatsuba.client.gui.apple.shell.setting.BindRow;
import dev.px.karatsuba.client.gui.apple.shell.setting.ControlRow;
import dev.px.karatsuba.client.gui.apple.shell.setting.InfoRow;
import dev.px.karatsuba.client.gui.apple.shell.setting.LinkRow;
import dev.px.karatsuba.client.gui.apple.shell.widget.Caption;
import dev.px.karatsuba.client.gui.apple.shell.widget.EmptyState;
import dev.px.karatsuba.client.gui.apple.shell.widget.PageIntro;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * The client's own settings, as Tahoe's System Settings pages
 * ({@code gui/apple/DESIGN.md} §2.4, §2.6; PLAN.md step 11): a page with one card
 * of the settings pages, each with how many of its settings are changed and a ›,
 * and an About card; opening one pushes it in from the right: a sentence saying what
 * it governs, its settings in grouped cards (a keybind that clashes with another
 * says so under it), and a Reset button under the last card that asks twice.
 *
 * <p>The sidebar's search is the one search: while it holds anything a results page
 * shows every matching setting, in a card per page. Changes apply as they are made,
 * with nothing to save.
 */
public final class SettingsSection implements Section {

    public static final String ID = "settings";

    /** How long "Reset" waits for its confirming second press. */
    private static final long CONFIRM_MILLIS = 3000L;

    private final Shell shell;
    private final List<SettingsPage> pages;
    private final PageStack stack = new PageStack();
    private final CardPage list = new CardPage();
    private final CardPage detail = new CardPage();
    private final CardPage results = new CardPage();
    private final CardList settings = new CardList(true);
    private final CardList found = new CardList(true);

    /** The page open, or null on the list. */
    private SettingsPage open;
    private SettingsPage built;
    private String builtQuery = "";
    private long resetArmedAt;

    public SettingsSection(Shell shell, WindowFrame frame) {
        this.shell = shell;
        this.pages = SettingsPages.create();
        settings.decorate(this::warnClash);
        found.decorate(this::warnClash);

        stack.page(list);
        stack.page(detail);
        stack.page(results);

        Card card = list.card(null);
        for (SettingsPage each : pages) {
            card.add(new LinkRow(each.title(), () -> changed(each), () -> openPage(each)));
        }
        // About: what this is and what it runs on.
        Card about = list.card("About");
        about.add(new InfoRow("Version", () -> Core.get().getClientName() + " " + Core.get().getClientVersion()));
        about.add(new InfoRow("Game", () -> Core.platform().getGameVersion()));
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
        return stack;
    }

    @Override
    public void tick() {
        String query = query();
        if (!query.isEmpty()) {
            if (!query.equals(builtQuery)) {
                showResults(query);
            }
            if (stack.showing() != results) {
                stack.jump(results);
            }
        } else if (stack.showing() == results) {
            builtQuery = "";
            stack.jump(open == null ? list : detail);
        }
        if (built != open && open != null) {
            buildPage(open);
        }
        settings.refresh();
        found.refresh();
    }

    @Override
    public boolean find() {
        return shell.focusSearch();
    }

    @Override
    public boolean typed(char character) {
        return !Character.isISOControl(character) && shell.typeIntoSearch(character);
    }

    /** Escape on a page goes back to the list, as ‹ does. */
    @Override
    public boolean back() {
        if (open == null || searching()) {
            return false;
        }
        open = null;
        stack.pop(list);
        return true;
    }

    @Override
    public String heading() {
        if (searching()) {
            return "Search";
        }
        return open == null ? title() : open.title();
    }

    /** The page open, by id, for the history; empty on the list. */
    @Override
    public String place() {
        return open == null ? "" : open.id();
    }

    @Override
    public boolean slidesPlaces() {
        return true;
    }

    @Override
    public void restore(String place, int direction) {
        open = find(place);
        Widget page = open == null ? list : detail;
        if (open != null) {
            buildPage(open);
        }
        if (direction > 0) {
            stack.push(page);
        } else if (direction < 0) {
            stack.pop(page);
        } else {
            stack.jump(page);
        }
    }

    @Override
    public boolean ownsSearch() {
        return true;
    }

    /** Enter in the search: opens the page of the first setting found. */
    @Override
    public boolean submitSearch() {
        String query = query();
        for (SettingsPage each : pages) {
            if (!matchesIn(each, query).isEmpty()) {
                shell.search("");
                openPage(each);
                return true;
            }
        }
        return false;
    }

    /** Opens with {@code query} in the sidebar's search: Home's way to a setting. */
    public void search(String query) {
        shell.search(query);
    }

    // ------------------------------------------------------------- internals

    /** Pushes {@code page} in from the right. */
    private void openPage(SettingsPage page) {
        open = page;
        resetArmedAt = 0L;
        buildPage(page);
        stack.push(detail);
    }

    private String query() {
        return shell.search().trim().toLowerCase(Locale.ROOT);
    }

    private boolean searching() {
        return !query().isEmpty();
    }

    /** @return the page's count of changed settings, for its row, or empty when there are none */
    private static String changed(SettingsPage page) {
        int count = page.modified();
        return count == 0 ? "" : count + " changed";
    }

    /** Fills a page: its sentence, its settings in cards, any note, and Reset under them. Only when it changes. */
    private void buildPage(SettingsPage shown) {
        built = shown;
        detail.clearCards();
        settings.clear();
        detail.group(null).add(new PageIntro(shown.description()));
        if (shown.layout() != null) {
            // A page that lays itself out still sits in a solid card.
            Card card = detail.card(null);
            for (Widget row : shown.layout().apply(settings::row)) {
                if (row instanceof ControlRow control) {
                    control.detailed();
                }
                card.add(row);
            }
        } else {
            settings.build(shown.settings().get(), null, detail);
        }
        Column foot = detail.group(null);
        if (shown.note() != null) {
            foot.add(new Caption(shown.note()));
        }
        PushButton reset = new PushButton(() -> resetLabel(shown), () -> resetPage(shown));
        reset.enabledWhen(() -> shown.modified() > 0);
        detail.actions(foot, reset);
    }

    /** Fills the results page: each page with a match, a card of its matches under its title. */
    private void showResults(String query) {
        builtQuery = query;
        results.clearCards();
        found.clear();
        boolean any = false;
        for (SettingsPage each : pages) {
            List<Setting<?>> matches = matchesIn(each, query);
            if (matches.isEmpty()) {
                continue;
            }
            any = true;
            Card card = results.card(each.title());
            for (Setting<?> setting : matches) {
                card.add(found.row(setting));
            }
        }
        if (!any) {
            results.group(null).add(new EmptyState(Glyph.SEARCH, "No settings match",
                    "Nothing is called that, and no description mentions it.",
                    "Clear Search", () -> shell.search("")));
        }
    }

    /** @return the settings on {@code page} that match {@code query}: all of them if its title does */
    private static List<Setting<?>> matchesIn(SettingsPage page, String query) {
        List<Setting<?>> matches = new ArrayList<>();
        boolean pageMatches = page.title().toLowerCase(Locale.ROOT).contains(query);
        for (Setting<?> setting : page.flat()) {
            if (pageMatches || setting.getName().toLowerCase(Locale.ROOT).contains(query)
                    || setting.getDescription().toLowerCase(Locale.ROOT).contains(query)) {
                matches.add(setting);
            }
        }
        return matches;
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
            return "Nothing to Reset";
        }
        String what = count == 1 ? "1 Setting" : count + " Settings";
        return armed() ? "Click Again to Reset " + what : "Reset " + what + "…";
    }

    private boolean armed() {
        return resetArmedAt != 0L && System.currentTimeMillis() - resetArmedAt < CONFIRM_MILLIS;
    }

    /** Puts a keybind's clash under its row. */
    private void warnClash(ControlRow row) {
        if (row instanceof BindRow bind) {
            row.note(() -> clash(bind.setting()));
        }
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
