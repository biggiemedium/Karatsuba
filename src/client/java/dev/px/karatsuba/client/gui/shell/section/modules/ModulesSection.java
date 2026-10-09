package dev.px.karatsuba.client.gui.shell.section.modules;

import dev.px.core.Core;
import dev.px.core.module.Module;
import dev.px.core.module.category.Category;
import dev.px.gui.Widget;
import dev.px.gui.container.Row;
import dev.px.karatsuba.client.gui.shell.Section;
import dev.px.karatsuba.client.gui.shell.Shell;
import dev.px.karatsuba.client.gui.shell.frame.ModuleColumn;
import dev.px.karatsuba.client.gui.shell.frame.Reveal;
import dev.px.karatsuba.client.gui.shell.frame.SettingsColumn;
import dev.px.karatsuba.client.gui.shell.frame.Sidebar;
import dev.px.karatsuba.client.gui.shell.frame.SmoothScroll;
import dev.px.karatsuba.client.gui.shell.frame.Spacer;
import dev.px.karatsuba.client.gui.shell.rail.Glyph;
import dev.px.karatsuba.client.gui.shell.setting.SettingList;
import dev.px.karatsuba.client.gui.shell.style.Palette;
import dev.px.karatsuba.client.gui.shell.widget.Caption;
import dev.px.karatsuba.client.gui.shell.widget.Description;
import dev.px.karatsuba.client.gui.shell.widget.InspectorHeader;
import dev.px.karatsuba.client.gui.shell.widget.ListItem;
import dev.px.karatsuba.client.gui.shell.widget.SearchField;
import dev.px.karatsuba.client.gui.shell.widget.SectionTitle;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * The modules: categories, then the modules in the one chosen, then the chosen
 * module's settings. Typing searches every category at once.
 *
 * <p>The category and module last shown are kept across openings.
 */
public final class ModulesSection implements Section, Navigator {

    public static final String ID = "modules";

    private static Category lastCategory;
    private static final Map<Category, Module> LAST_SELECTED = new HashMap<>();

    private final Shell shell;
    private final Row content;
    private final Sidebar categories;
    private final ModuleColumn moduleColumn;
    private final SettingsColumn settingsColumn;
    private final SearchField search;
    private final SettingList settings = new SettingList();

    private final Reveal listReveal = new Reveal();
    private final Reveal paneReveal = new Reveal();

    private Category category;
    private Module selected;
    private Module built;
    private boolean firstBuild = true;
    private boolean ready;

    public ModulesSection(Shell shell, dev.px.karatsuba.client.gui.shell.frame.WindowFrame frame) {
        this.shell = shell;
        this.content = new Row();

        // ---- categories, under the section's title
        this.categories = content.add(new Sidebar(frame));
        categories.gap(Palette.GAP_ROW);
        categories.add(new SectionTitle("Modules", null));
        categories.add(new Spacer(Palette.INSET - Palette.GAP_ROW));
        SmoothScroll tabs = categories.add(new SmoothScroll());
        tabs.gap(Palette.GAP_ROW);
        tabs.grow(1f);
        for (Category each : categories()) {
            tabs.add(new CategoryTab(each, this));
        }

        // ---- modules
        this.moduleColumn = content.add(new ModuleColumn(listReveal));
        moduleColumn.gap(Palette.INSET);
        this.search = moduleColumn.add(new SearchField("Search modules"));
        SmoothScroll modules = moduleColumn.add(new SmoothScroll());
        modules.gap(Palette.GAP_ROW);
        modules.grow(1f);
        for (Module module : sortedModules()) {
            ListItem row = modules.add(new ListItem(module::getName,
                    () -> isSearching() && module.getCategory() != null ? module.getCategory().getName() : "",
                    module::isEnabled, module::toggle,
                    () -> module == selected, () -> select(module)));
            row.visibleWhen(() -> lists(module));
            if (!module.getDescription().isEmpty()) {
                row.tooltip(module.getDescription());
            }
        }
        modules.add(new Caption("No modules match")).visibleWhen(this::nothingListed);

        // ---- the chosen module's settings
        this.settingsColumn = content.add(new SettingsColumn(paneReveal));
        settingsColumn.gap(Palette.GAP_ROW * 2f);
        settingsColumn.grow(1f);

        shell.veil(listReveal, () -> moduleColumn);
        shell.veil(paneReveal, () -> settingsColumn);

        List<Category> all = categories();
        showCategory(lastCategory != null && all.contains(lastCategory) ? lastCategory
                : all.isEmpty() ? null : all.get(0));
        ready = true;
    }

    // --------------------------------------------------------------- Section

    @Override
    public String id() {
        return ID;
    }

    @Override
    public String title() {
        return "Modules";
    }

    @Override
    public Glyph glyph() {
        return Glyph.MODULES;
    }

    @Override
    public Widget content() {
        return content;
    }

    @Override
    public void tick() {
        // A narrow window takes room from the two fixed columns first, so the
        // settings keep enough width for a name and its control.
        float squeeze = Math.max(0f, Math.min(1f, (Palette.FRAME_WIDTH - shell.windowWidth()) / 280f));
        categories.width(Palette.SIDEBAR_WIDTH - 24f * squeeze);
        moduleColumn.width(Palette.LIST_WIDTH - 36f * squeeze);
        if (built != selected || firstBuild) {
            rebuildSettings();
        }
        settings.refresh();
    }

    @Override
    public void left() {
        lastCategory = category;
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
    public boolean find() {
        search.grab();
        search.selectAll();
        return true;
    }

    // ------------------------------------------------------------- Navigator

    @Override
    public boolean isShowing(Category candidate) {
        return !isSearching() && candidate == category;
    }

    @Override
    public int enabledIn(Category candidate) {
        int count = 0;
        for (Module module : Core.modules().inCategory(candidate)) {
            if (module.isEnabled()) {
                count++;
            }
        }
        return count;
    }

    @Override
    public void showCategory(Category shown) {
        boolean changed = shown != category || isSearching();
        category = shown;
        lastCategory = shown;
        search.setText("");
        if (changed && ready) {
            listReveal.start();
        }
        if (shown == null) {
            select(null);
            return;
        }
        Module last = LAST_SELECTED.get(shown);
        if (last == null) {
            List<Module> in = sorted(Core.modules().inCategory(shown));
            last = in.isEmpty() ? null : in.get(0);
        }
        select(last);
    }

    /** Opens with {@code query} in the search box: Home's "see all" for a search. */
    public void searchFor(String query) {
        search.setText(query);
    }

    /** Shows {@code module} in its category, selected: Home's way into a module. */
    public void reveal(Module module) {
        showCategory(module.getCategory());
        select(module);
    }

    // ------------------------------------------------------------- internals

    private boolean isSearching() {
        // Null while the category tabs are built, which happens before the search box exists.
        return search != null && !search.getText().isBlank();
    }

    private boolean lists(Module module) {
        if (!isSearching()) {
            return module.getCategory() == category;
        }
        String query = search.getText().trim().toLowerCase(Locale.ROOT);
        return module.getName().toLowerCase(Locale.ROOT).contains(query)
                || module.getDescription().toLowerCase(Locale.ROOT).contains(query);
    }

    private void select(Module module) {
        if (module != selected && ready) {
            paneReveal.start();
        }
        selected = module;
        if (module != null && module.getCategory() != null) {
            LAST_SELECTED.put(module.getCategory(), module);
            if (!isSearching()) {
                category = module.getCategory();
            }
        }
    }

    private boolean nothingListed() {
        for (Module module : Core.modules().all()) {
            if (lists(module)) {
                return false;
            }
        }
        return true;
    }

    /** Fills the right column for the chosen module; only when the choice changes, never per frame. */
    private void rebuildSettings() {
        firstBuild = false;
        built = selected;
        settingsColumn.clear();
        if (selected == null) {
            settingsColumn.add(new Caption("Select a module to see its settings"));
            return;
        }
        Module module = selected;
        settingsColumn.add(new InspectorHeader(module.getName(), "Enabled", "Disabled",
                module::isEnabled, module::toggle));
        if (!module.getDescription().isEmpty()) {
            settingsColumn.add(new Description(module.getDescription()));
        }
        settingsColumn.add(new Spacer(Palette.INSET - Palette.GAP_ROW * 2f));
        SmoothScroll list = settingsColumn.add(new SmoothScroll());
        list.gap(Palette.GAP_ROW);
        list.grow(1f);
        settings.clear();
        settings.build(module, list);
    }

    private static List<Category> categories() {
        List<Category> all = new ArrayList<>(Core.categories().all());
        all.sort(Comparator.comparingInt(Category::getOrder));
        // A category with nothing in it yet is not worth a tab.
        all.removeIf(each -> Core.modules().inCategory(each).isEmpty());
        return all;
    }

    /** Every module, by category and then by name, so a search lists them in a steady order. */
    private static List<Module> sortedModules() {
        List<Module> all = new ArrayList<>();
        for (Category each : categories()) {
            all.addAll(sorted(Core.modules().inCategory(each)));
        }
        return all;
    }

    private static List<Module> sorted(List<Module> modules) {
        List<Module> copy = new ArrayList<>(modules);
        copy.sort(Comparator.comparing(module -> module.getName().toLowerCase(Locale.ROOT)));
        return copy;
    }
}
