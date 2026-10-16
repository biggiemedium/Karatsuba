package dev.px.karatsuba.client.gui.apple.shell.section.modules;

import dev.px.core.Core;
import dev.px.core.module.Module;
import dev.px.core.module.category.Category;
import dev.px.gui.Widget;
import dev.px.karatsuba.client.gui.apple.shell.Section;
import dev.px.karatsuba.client.gui.apple.shell.Shell;
import dev.px.karatsuba.client.gui.apple.shell.card.Card;
import dev.px.karatsuba.client.gui.apple.shell.card.CardList;
import dev.px.karatsuba.client.gui.apple.shell.card.CardPage;
import dev.px.karatsuba.client.gui.apple.shell.frame.PageStack;
import dev.px.karatsuba.client.gui.apple.shell.rail.Glyph;
import dev.px.karatsuba.client.gui.apple.shell.setting.SwitchRow;
import dev.px.karatsuba.client.gui.apple.shell.style.Appearance;
import dev.px.karatsuba.client.gui.apple.shell.style.Palette;
import dev.px.karatsuba.client.gui.apple.shell.widget.EmptyState;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/**
 * The modules, as Tahoe's System Settings pages ({@code gui/apple/DESIGN.md} §2.6,
 * PLAN.md step 9): the category chosen in the sidebar is a page with one card of
 * its modules, each with its switch and a ›; opening one pushes its page in from
 * the right: a card with its switch and description, then its settings in cards.
 * ‹, Escape and the history pop back.
 *
 * <p>The sidebar's search is the one search: while it holds anything the list
 * page shows every module that matches, from every category, each with its
 * category beside it.
 */
public final class ModulesSection implements Section {

    public static final String ID = "modules";

    /** Between a category and a module in a {@link #place()}. */
    private static final String SEPARATOR = "/";

    private static Category lastCategory;

    private final Shell shell;
    private final PageStack pages = new PageStack();
    private final CardPage list = new CardPage();
    private final CardPage detail = new CardPage();
    private final CardList settings = new CardList(true);

    private Category category;
    /** The module whose page is open, or null on the list. */
    private Module open;
    private Module built;
    private boolean wasSplit;

    public ModulesSection(Shell shell, dev.px.karatsuba.client.gui.apple.shell.frame.WindowFrame frame) {
        this.shell = shell;
        pages.page(list);
        pages.page(detail);
        // Split (an Appearance choice): the list and the open module side by side, nothing sliding.
        pages.sideBySide(this::isSplit, Palette.SPLIT_LIST_WIDTH, Palette.GAP_SPLIT);

        // One card of every module, each row showing while its category is chosen or it matches the search.
        Card card = list.card(null);
        for (Module module : sortedModules()) {
            ModuleRow row = card.add(new ModuleRow(module,
                    () -> isSearching() && module.getCategory() != null ? module.getCategory().getName() : "",
                    this::openModule)).split(() -> isSplit() && module == open, () -> !isSplit());
            row.visibleWhen(() -> lists(module));
        }
        card.visibleWhen(() -> !nothingListed());
        list.group(null).add(new EmptyState(Glyph.SEARCH, "No modules match",
                "Nothing is called that, and no description mentions it.",
                "Clear Search", () -> shell.search(""))).visibleWhen(this::nothingListed);

        List<Category> all = categories();
        category = lastCategory != null && all.contains(lastCategory) ? lastCategory
                : all.isEmpty() ? null : all.get(0);
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
        return pages;
    }

    @Override
    public void tick() {
        // The layout changed under us (Appearance): Pages starts back on the list.
        boolean split = isSplit();
        if (split != wasSplit) {
            wasSplit = split;
            if (!split) {
                open = null;
                pages.jump(list);
            }
        }
        // Beside the list there is always a module showing: the first listed, until another is chosen.
        if (isSplit() && open == null) {
            open = firstListed();
        }
        if (built != open) {
            rebuildDetail();
        }
        settings.refresh();
    }

    @Override
    public void left() {
        lastCategory = category;
    }

    @Override
    public boolean typed(char character) {
        return !Character.isISOControl(character) && shell.typeIntoSearch(character);
    }

    @Override
    public boolean find() {
        return shell.focusSearch();
    }

    /** Escape on a module's page goes back to its category, as ‹ does. */
    @Override
    public boolean back() {
        if (open == null || isSplit()) {
            return false;
        }
        closeModule();
        return true;
    }

    @Override
    public String heading() {
        if (open != null && !isSplit()) {
            return open.getName();
        }
        return isSearching() ? "Search" : category == null ? title() : category.getName();
    }

    /** "Combat", or "Combat/Aura" with Aura's page open: what the history steps between. */
    @Override
    public String place() {
        String where = category == null ? "" : category.getName();
        return open == null ? where : where + SEPARATOR + open.getName();
    }

    @Override
    public boolean slidesPlaces() {
        return true;
    }

    @Override
    public boolean ownsSearch() {
        return true;
    }

    @Override
    public boolean submitSearch() {
        return openFirstMatch();
    }

    @Override
    public void restore(String place, int direction) {
        String[] parts = place.split(SEPARATOR, 2);
        for (Category each : categories()) {
            if (each.getName().equals(parts[0])) {
                category = each;
            }
        }
        Module target = parts.length > 1 ? find(parts[1]) : null;
        open = target;
        Widget page = target == null ? list : detail;
        if (target != null) {
            rebuildDetail();
        }
        if (direction > 0) {
            pages.push(page);
        } else if (direction < 0) {
            pages.pop(page);
        } else {
            pages.jump(page);
        }
    }

    // ------------------------------------------------------------- categories

    /** @return whether {@code candidate} is the category showing (its modules, or one of their pages); false while searching */
    public boolean isShowing(Category candidate) {
        return !isSearching() && candidate == category;
    }

    /** Shows {@code shown}'s modules, clearing any search; from a module's page, without a slide (a sidebar switch). */
    public void showCategory(Category shown) {
        category = shown;
        lastCategory = shown;
        shell.search("");
        open = isSplit() ? firstListed() : null;
        pages.jump(open == null ? list : detail);
    }

    /** Opens with {@code query} in the sidebar's search: Home's "see all" for a search. */
    public void searchFor(String query) {
        open = null;
        pages.jump(list);
        shell.search(query);
    }

    /** Enter in the search: opens the first module it lists. @return whether there was one */
    public boolean openFirstMatch() {
        for (Module module : sortedModules()) {
            if (lists(module)) {
                openModule(module);
                return true;
            }
        }
        return false;
    }

    /** Opens {@code module}'s page from its category: Home's way into a module. */
    public void reveal(Module module) {
        showCategory(module.getCategory());
        openModule(module);
    }

    // ------------------------------------------------------------- internals

    /** Pushes {@code module}'s page in from the right. */
    private void openModule(Module module) {
        if (module.getCategory() != null && !isSearching()) {
            category = module.getCategory();
        }
        open = module;
        rebuildDetail();
        pages.push(detail);
    }

    /** Pops back to the list, sliding it in from the left. */
    private void closeModule() {
        open = null;
        pages.pop(list);
    }

    /** @return whether Modules shows the list and the open module side by side (Appearance, Modules Layout) */
    private boolean isSplit() {
        Appearance appearance = Appearance.get();
        return appearance != null && appearance.modulesLayout() == Appearance.ModulesLayout.SPLIT;
    }

    /** @return the first module the list shows, or null */
    private Module firstListed() {
        for (Module module : sortedModules()) {
            if (lists(module)) {
                return module;
            }
        }
        return null;
    }

    private boolean isSearching() {
        return !shell.search().isBlank();
    }

    private boolean lists(Module module) {
        if (!isSearching()) {
            return module.getCategory() == category;
        }
        String query = shell.search().trim().toLowerCase(Locale.ROOT);
        return module.getName().toLowerCase(Locale.ROOT).contains(query)
                || module.getDescription().toLowerCase(Locale.ROOT).contains(query);
    }

    private boolean nothingListed() {
        for (Module module : Core.modules().all()) {
            if (lists(module)) {
                return false;
            }
        }
        return true;
    }

    /** Fills the module's page: only when the module changes, never per frame. */
    private void rebuildDetail() {
        if (built == open && open != null) {
            return;
        }
        built = open;
        if (open == null) {
            // Kept as it was while the page slides away; rebuilt when another opens.
            return;
        }
        detail.clearCards();
        settings.clear();
        Module module = open;
        // Its switch and what it does first, as a page in System Settings leads with its own switch.
        Card first = detail.card(null);
        first.add(new SwitchRow(module.getName(), module.getDescription(), false,
                module::isEnabled, on -> {
                    if (on != module.isEnabled()) {
                        module.toggle();
                    }
                })).detailed();
        settings.build(module, detail);
    }

    private static Module find(String name) {
        for (Module module : Core.modules().all()) {
            if (module.getName().equals(name)) {
                return module;
            }
        }
        return null;
    }

    /** @return the categories with modules in them, in their order: the rows in the sidebar */
    public static List<Category> categories() {
        List<Category> all = new ArrayList<>(Core.categories().all());
        all.sort(Comparator.comparingInt(Category::getOrder));
        // A category with nothing in it yet is not worth a row.
        all.removeIf(each -> Core.modules().inCategory(each).isEmpty());
        return all;
    }

    /** Every module, by category and then by name, so a search lists them in a steady order. */
    private static List<Module> sortedModules() {
        List<Module> all = new ArrayList<>();
        for (Category each : categories()) {
            List<Module> in = new ArrayList<>(Core.modules().inCategory(each));
            in.sort(Comparator.comparing(module -> module.getName().toLowerCase(Locale.ROOT)));
            all.addAll(in);
        }
        return all;
    }
}
