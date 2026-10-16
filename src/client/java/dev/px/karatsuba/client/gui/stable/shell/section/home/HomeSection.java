package dev.px.karatsuba.client.gui.stable.shell.section.home;

import dev.px.core.Core;
import dev.px.core.hud.HudElement;
import dev.px.core.hud.HudLayout;
import dev.px.core.module.Module;
import dev.px.core.module.category.Category;
import dev.px.core.setting.Setting;
import dev.px.core.setting.impl.BindSetting;
import dev.px.gui.Widget;
import dev.px.gui.container.Column;
import dev.px.karatsuba.adapter.game.player.PlayerList;
import dev.px.karatsuba.client.gui.stable.shell.Section;
import dev.px.karatsuba.client.gui.stable.shell.Shell;
import dev.px.karatsuba.client.gui.stable.shell.frame.FlowGrid;
import dev.px.karatsuba.client.gui.stable.shell.frame.SmoothScroll;
import dev.px.karatsuba.client.gui.stable.shell.frame.Spacer;
import dev.px.karatsuba.client.gui.stable.shell.rail.Glyph;
import dev.px.karatsuba.client.gui.stable.shell.section.friends.FriendsSection;
import dev.px.karatsuba.client.gui.stable.shell.section.hud.HudSection;
import dev.px.karatsuba.client.gui.stable.shell.section.layout.LayoutSection;
import dev.px.karatsuba.client.gui.stable.shell.section.modules.ModulesSection;
import dev.px.karatsuba.client.gui.stable.shell.section.settings.SettingsPage;
import dev.px.karatsuba.client.gui.stable.shell.section.settings.SettingsPages;
import dev.px.karatsuba.client.gui.stable.shell.section.settings.SettingsSection;
import dev.px.karatsuba.client.gui.stable.shell.section.themes.ThemesSection;
import dev.px.karatsuba.client.gui.stable.shell.style.Palette;
import dev.px.karatsuba.client.gui.stable.shell.widget.Caption;
import dev.px.karatsuba.client.gui.stable.shell.widget.ListHeading;
import dev.px.karatsuba.client.gui.stable.shell.widget.ListItem;
import dev.px.karatsuba.client.gui.stable.shell.widget.SearchField;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Home: how things stand, and the quickest way to whatever is wanted.
 *
 * <p>A dashboard you glance at, not a second copy of every section. At the top, a
 * greeting and the state of the session (server, ping, tick rate, anticheat);
 * then one search box over every module and setting; then a card for each
 * section, saying in one number and a line how it stands and opening it when
 * pressed; and last, the modules switched lately, to switch again without going
 * looking for them.
 *
 * <p>Typing anywhere on Home goes into the search; while it holds text the
 * results replace the cards. The GUI opens on Home the first time each session
 * and when you join another server, and otherwise where you left it.
 */
public final class HomeSection implements Section {

    public static final String ID = "home";

    /** Most results of each kind the search lists: enough to find, few enough to scan. */
    private static final int RESULTS = 6;

    /** Narrowest a card gets before the grid drops a column. */
    private static final float CARD_WIDTH = 150f;

    private final Shell shell;
    private final HomeView content;
    private final SearchField search;
    private final SmoothScroll body;

    private String built;

    public HomeSection(Shell shell) {
        this.shell = shell;
        this.content = new HomeView();
        content.gap(Palette.GAP_ROW);
        content.add(new HomeHeader());
        content.add(new Spacer(Palette.INSET));
        this.search = content.add(new SearchField("Search modules and settings"));
        content.add(new Spacer(Palette.GAP_SECTION - Palette.GAP_ROW));
        this.body = content.add(new SmoothScroll());
        body.gap(Palette.GAP_ROW);
        body.grow(1f);
    }

    // --------------------------------------------------------------- Section

    @Override
    public String id() {
        return ID;
    }

    @Override
    public String title() {
        return "Home";
    }

    @Override
    public Glyph glyph() {
        return Glyph.HOME;
    }

    @Override
    public Widget content() {
        return content;
    }

    @Override
    public void tick() {
        String query = search.getText().trim().toLowerCase(Locale.ROOT);
        // The cards read their numbers live, so they are built once; the recent
        // list and the results are rebuilt only when what they show changes.
        String now = query.isEmpty() ? "home|" + RecentModules.all() : "search|" + query;
        if (!now.equals(built)) {
            built = now;
            body.clear();
            if (query.isEmpty()) {
                overview(body);
            } else {
                results(body, query);
            }
        }
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
        if (!search.getText().isEmpty()) {
            search.setText("");
            return true;
        }
        return false;
    }

    // -------------------------------------------------------------- overview

    private void overview(Column into) {
        FlowGrid cards = into.add(new FlowGrid(CARD_WIDTH, Palette.INSET));
        cards.add(new HomeCard(Glyph.MODULES, "Modules", HomeSection::modulesMetric, HomeSection::modulesDetail,
                () -> share(enabledModules(), Core.modules().size()), () -> shell.show(ModulesSection.ID), null, null));
        cards.add(new HomeCard(Glyph.HUD, "HUD", HomeSection::hudMetric, HomeSection::hudDetail,
                () -> share(shownElements(), Core.hud().getElements().size()), () -> shell.show(HudSection.ID),
                "Edit layout", () -> shell.show(LayoutSection.ID)));
        cards.add(new HomeCard(Glyph.PEOPLE, "Friends", HomeSection::friendsMetric, HomeSection::friendsDetail,
                null, () -> shell.show(FriendsSection.ID), null, null));
        cards.add(new HomeCard(Glyph.THEMES, "Themes", () -> Core.themes().getActive().getName(),
                () -> Core.themes().getThemes().size() + " to choose from",
                null, () -> shell.show(ThemesSection.ID), null, null).swatch());
        cards.add(new HomeCard(Glyph.SETTINGS, "Settings", HomeSection::settingsMetric, HomeSection::settingsDetail,
                null, () -> shell.show(SettingsSection.ID), null, null));

        into.add(new Spacer(Palette.GAP_SECTION));
        into.add(new ListHeading("Jump back in", null));
        List<Module> recent = RecentModules.all();
        if (recent.isEmpty()) {
            into.add(new Caption("Modules you switch on or off show here, ready to switch again."));
            return;
        }
        for (Module module : recent) {
            into.add(listed(module));
        }
    }

    /** A module as a row: its switch flips it, pressing the row opens it among the modules. */
    private ListItem listed(Module module) {
        ListItem row = new ListItem(module::getName,
                () -> module.getCategory() == null ? "" : module.getCategory().getName(),
                module::isEnabled, module::toggle, () -> false, () -> openModule(module));
        if (!module.getDescription().isEmpty()) {
            row.tooltip(module.getDescription());
        }
        return row;
    }

    private void openModule(Module module) {
        shell.show(ModulesSection.ID);
        if (shell.section(ModulesSection.ID) instanceof ModulesSection modules) {
            modules.reveal(module);
        }
    }

    // --------------------------------------------------------------- results

    private void results(Column into, String query) {
        List<Module> modules = new ArrayList<>();
        for (Module module : Core.modules().all()) {
            if (module.getName().toLowerCase(Locale.ROOT).contains(query)
                    || module.getDescription().toLowerCase(Locale.ROOT).contains(query)) {
                modules.add(module);
            }
        }
        modules.sort((a, b) -> a.getName().compareToIgnoreCase(b.getName()));

        List<ResultRow> settings = new ArrayList<>();
        for (SettingsPage page : SettingsPages.create()) {
            for (Setting<?> setting : page.flat()) {
                if (setting.getName().toLowerCase(Locale.ROOT).contains(query)
                        || setting.getDescription().toLowerCase(Locale.ROOT).contains(query)) {
                    String name = setting.getName();
                    settings.add(new ResultRow(name, "Settings  ·  " + page.title(), () -> openSetting(name)));
                }
            }
        }

        if (!modules.isEmpty()) {
            into.add(new ListHeading("Modules", () -> String.valueOf(modules.size())));
            for (Module module : modules.subList(0, Math.min(RESULTS, modules.size()))) {
                into.add(listed(module));
            }
            if (modules.size() > RESULTS) {
                String text = search.getText().trim();
                into.add(new ResultRow("See all " + modules.size() + " in Modules", "", () -> {
                    shell.show(ModulesSection.ID);
                    if (shell.section(ModulesSection.ID) instanceof ModulesSection section) {
                        section.searchFor(text);
                    }
                }));
            }
        }
        if (!settings.isEmpty()) {
            if (!modules.isEmpty()) {
                into.add(new Spacer(Palette.GAP_SECTION));
            }
            into.add(new ListHeading("Settings", () -> String.valueOf(settings.size())));
            for (ResultRow row : settings.subList(0, Math.min(RESULTS, settings.size()))) {
                into.add(row);
            }
        }
        if (modules.isEmpty() && settings.isEmpty()) {
            into.add(new Caption("No modules or settings match \"" + search.getText().trim() + "\""));
        }
    }

    private void openSetting(String name) {
        shell.show(SettingsSection.ID);
        if (shell.section(SettingsSection.ID) instanceof SettingsSection settings) {
            settings.search(name);
        }
    }

    // ------------------------------------------------------------- the cards

    private static String modulesMetric() {
        int on = enabledModules();
        return on == 0 ? "None on" : on + " of " + Core.modules().size() + " on";
    }

    /** The categories with most switched on: "Combat 3  ·  Render 2". */
    private static String modulesDetail() {
        List<String> parts = new ArrayList<>();
        List<Category> categories = new ArrayList<>(Core.categories().all());
        categories.sort((a, b) -> Integer.compare(enabledIn(b), enabledIn(a)));
        for (Category category : categories) {
            int count = enabledIn(category);
            if (count > 0 && parts.size() < 3) {
                parts.add(category.getName() + " " + count);
            }
        }
        return parts.isEmpty() ? "Switch one on in Modules" : String.join("  ·  ", parts);
    }

    private static String hudMetric() {
        return shownElements() + " of " + Core.hud().getElements().size() + " shown";
    }

    private static String hudDetail() {
        List<String> hidden = new ArrayList<>();
        for (HudElement element : Core.hud().getElements()) {
            HudLayout layout = Core.hud().layoutOf(element);
            if (layout != null && layout.isHidden()) {
                hidden.add(element.getDisplayName());
            }
        }
        if (hidden.isEmpty()) {
            return "Every element is shown";
        }
        return "Hidden: " + names(hidden);
    }

    private static String friendsMetric() {
        int total = Core.social().getFriends().size();
        if (total == 0) {
            return "No friends yet";
        }
        int online = onlineFriends().size();
        return online > 0 ? online + " online" : total + (total == 1 ? " friend" : " friends");
    }

    private static String friendsDetail() {
        if (Core.social().getFriends().isEmpty()) {
            return "Add the players combat should leave alone";
        }
        List<String> online = onlineFriends();
        if (!online.isEmpty()) {
            return names(online);
        }
        return Core.platform().isInGame() ? "Nobody online here" : "Join a server to see who is on";
    }

    private static String settingsMetric() {
        int changed = 0;
        for (SettingsPage page : SettingsPages.create()) {
            changed += page.modified();
        }
        return changed == 0 ? "All at defaults" : changed + " changed";
    }

    /** The key that opens the GUI: the one thing worth remembering from Settings. */
    private static String settingsDetail() {
        for (Setting<?> setting : Core.input().getActionSettings()) {
            if (setting instanceof BindSetting bind && setting.getName().equals("Click GUI")) {
                return bind.isBound() ? bind.get().getDisplay() + " opens this GUI" : "No key opens this GUI";
            }
        }
        return "Keybinds, chat and notifications";
    }

    // --------------------------------------------------------------- figures

    private static int enabledModules() {
        return Core.modules().enabled().size();
    }

    private static int enabledIn(Category category) {
        int count = 0;
        for (Module module : Core.modules().inCategory(category)) {
            if (module.isEnabled()) {
                count++;
            }
        }
        return count;
    }

    private static int shownElements() {
        int count = 0;
        for (HudElement element : Core.hud().getElements()) {
            HudLayout layout = Core.hud().layoutOf(element);
            if (layout != null && !layout.isHidden()) {
                count++;
            }
        }
        return count;
    }

    private static List<String> onlineFriends() {
        List<String> names = new ArrayList<>();
        for (String name : PlayerList.names()) {
            if (Core.social().isFriend(name)) {
                names.add(Core.social().displayNameFor(name));
            }
        }
        return names;
    }

    private static float share(int part, int whole) {
        return whole <= 0 ? 0f : (float) part / whole;
    }

    /** "Jeb, Notch and 3 more": a few names, never a wall of them. */
    private static String names(List<String> names) {
        if (names.size() <= 2) {
            return String.join(" and ", names);
        }
        return names.get(0) + ", " + names.get(1) + " and " + (names.size() - 2) + " more";
    }

    /** Home's own column, so the look can give it its padding. */
    public static final class HomeView extends Column {
    }
}
