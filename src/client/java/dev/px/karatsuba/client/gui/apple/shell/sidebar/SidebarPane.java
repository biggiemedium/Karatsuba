package dev.px.karatsuba.client.gui.apple.shell.sidebar;

import dev.px.core.input.MouseButton;
import dev.px.core.layout.Bounds;
import dev.px.core.layout.Size;
import dev.px.core.module.category.Category;
import dev.px.gui.Container;
import dev.px.gui.Widget;
import dev.px.gui.container.Column;
import dev.px.karatsuba.client.gui.apple.shell.Section;
import dev.px.karatsuba.client.gui.apple.shell.Shell;
import dev.px.karatsuba.client.gui.apple.shell.frame.GlassSurface;
import dev.px.karatsuba.client.gui.apple.shell.frame.SmoothScroll;
import dev.px.karatsuba.client.gui.apple.shell.frame.WindowFrame;
import dev.px.karatsuba.client.gui.apple.shell.rail.Glyph;
import dev.px.karatsuba.client.gui.apple.shell.section.friends.FriendsSection;
import dev.px.karatsuba.client.gui.apple.shell.section.hud.HudSection;
import dev.px.karatsuba.client.gui.apple.shell.section.modules.ModulesSection;
import dev.px.karatsuba.client.gui.apple.shell.section.settings.SettingsSection;
import dev.px.karatsuba.client.gui.apple.shell.section.appearance.AppearanceSection;
import dev.px.karatsuba.client.gui.apple.shell.style.Animations;
import dev.px.karatsuba.client.gui.apple.shell.style.Easing;
import dev.px.karatsuba.client.gui.apple.shell.style.Motion;
import dev.px.karatsuba.client.gui.apple.shell.style.Palette;

import java.util.ArrayList;
import java.util.List;

/**
 * The sidebar: a glass pane floating inside the window's left edge
 * ({@code gui/apple/DESIGN.md} §2.2). A search field at the top, then the module
 * categories, then HUD and Friends, then Appearance and Settings, each group set
 * apart by room rather than a line.
 *
 * <p>Holds its size, as the window does: as tall as the window less its inset
 * above and below, with the entries scrolling if they do not fit. Below
 * {@link Palette#SIDEBAR_COLLAPSE_WIDTH} it narrows to the icons alone and the
 * search goes. Dragging the pane anywhere that is not an entry moves the window.
 */
public final class SidebarPane extends Container implements GlassSurface {

    private final Shell shell;
    private final WindowFrame frame;
    private final SidebarSearch search;
    private final List<SidebarEntry> entries = new ArrayList<>();
    private final Motion press = new Motion(Animations.PRESS, Animations.PRESS_OUT, Easing.STANDARD, 0f);

    private float grabX;
    private float grabY;
    private boolean held;

    public SidebarPane(Shell shell, WindowFrame frame) {
        this.shell = shell;
        this.frame = frame;
        Column body = add(new Column().gap(Palette.SIDEBAR_GAP_GROUP));

        this.search = body.add(new SidebarSearch());
        search.visibleWhen(() -> !isCollapsed());
        search.onSubmit(text -> {
            // On a section that owns the search (Modules, Settings), Enter opens the first thing it found.
            if (shell.active().ownsSearch()) {
                shell.active().submitSearch();
                return;
            }
            for (SidebarEntry entry : entries) {
                if (entry.isVisible()) {
                    entry.go();
                    return;
                }
            }
        });

        SmoothScroll scroll = body.add(new SidebarScroll(this));
        scroll.grow(1f);
        Column list = scroll.add(new Column().gap(1f));

        // The things you work on: the module categories.
        ModulesSection modules = (ModulesSection) shell.section(ModulesSection.ID);
        List<SidebarEntry> categories = new ArrayList<>();
        if (modules != null) {
            for (Category category : ModulesSection.categories()) {
                categories.add(entry(list, category.getName(), category.getName(),
                        Glyph.forIcon(category.getIcon()),
                        () -> shell.active() == modules && modules.isShowing(category),
                        () -> {
                            shell.show(ModulesSection.ID);
                            modules.showCategory(category);
                        }));
            }
        }

        // What is on screen, and who you play with.
        List<SidebarEntry> around = new ArrayList<>();
        list.add(new SidebarGap(around));
        section(list, around, shell, HudSection.ID, "hud", "HUD", Glyph.HUD);
        section(list, around, shell, FriendsSection.ID, "friends", "Friends", Glyph.PEOPLE);

        // The GUI itself: the Apple UI's own look, and the client's settings.
        List<SidebarEntry> client = new ArrayList<>();
        list.add(new SidebarGap(client));
        section(list, client, shell, AppearanceSection.ID, "appearance", "Appearance", Glyph.THEMES);
        section(list, client, shell, SettingsSection.ID, "settings", "Settings", Glyph.SETTINGS);
    }

    private SidebarEntry entry(Column list, String id, String label, Glyph glyph,
                               java.util.function.BooleanSupplier selected, Runnable open) {
        SidebarEntry entry = list.add(new SidebarEntry(this, id, label, glyph, selected, open));
        entries.add(entry);
        return entry;
    }

    private void section(Column list, List<SidebarEntry> group, Shell shell, String sectionId,
                         String id, String label, Glyph glyph) {
        Section section = shell.section(sectionId);
        if (section != null) {
            group.add(entry(list, id, label, glyph, () -> shell.active() == section, () -> shell.show(sectionId)));
        }
    }

    // ----------------------------------------------------------------- state

    /** @return whether the window is too narrow for names: icons alone */
    public boolean isCollapsed() {
        return frame.getWidth() < Palette.SIDEBAR_COLLAPSE_WIDTH;
    }

    /** @return the pane's width, with names or with icons alone */
    public float paneWidth() {
        return isCollapsed() ? Palette.SIDEBAR_COLLAPSED_WIDTH : Palette.SIDEBAR_PANE_WIDTH;
    }

    /** @return how tall it is: the window less the inset above and below */
    public float height() {
        return Math.max(0f, frame.getHeight() - Palette.INSET_WINDOW * 2f);
    }

    /**
     * @return what the search holds, trimmed, for filtering the entries; empty while
     *         collapsed, and on Modules, where the search finds modules rather than
     *         places (PLAN.md step 8c), so every entry shows
     */
    String query() {
        return isCollapsed() || shell.active().ownsSearch() ? "" : search.getText().trim();
    }

    /** @return the search field: the one on screen, for every section to share */
    public SidebarSearch search() {
        return search;
    }

    // ---------------------------------------------------------- GlassSurface

    @Override
    public Bounds glassBounds() {
        return part(PART);
    }

    @Override
    public float glassRadius() {
        return Palette.R_SIDEBAR;
    }

    @Override
    public float glassPress() {
        return press.toward(held);
    }

    // ---------------------------------------------------------------- layout

    // The renderer pads the slot by INSET_SIDEBAR at the sides and bottom and
    // SIDEBAR_PAD_TOP at the top; the slot is what is left, so the whole pane is
    // exactly paneWidth() by height() and stays inside the window.

    @Override
    protected float childrenHeight(List<Widget> visible, float available) {
        return Math.max(0f, height() - Palette.SIDEBAR_PAD_TOP - Palette.INSET_SIDEBAR);
    }

    @Override
    protected Size childrenNatural(List<Widget> visible) {
        return Size.of(paneWidth() - Palette.INSET_SIDEBAR * 2f,
                Math.max(0f, height() - Palette.SIDEBAR_PAD_TOP - Palette.INSET_SIDEBAR));
    }

    @Override
    protected void arrange(List<Widget> visible, Bounds slot) {
        for (Widget child : visible) {
            place(child, slot);
        }
    }

    // ----------------------------------------------------------------- input

    @Override
    protected boolean mousePressed(float x, float y, MouseButton button) {
        if (button != MouseButton.LEFT) {
            return false;
        }
        grabX = x - frame.getX();
        grabY = y - frame.getY();
        held = true;
        capture();
        return true;
    }

    @Override
    protected void mouseDragged(float x, float y) {
        frame.moveTo(x - grabX, y - grabY);
    }

    @Override
    protected void mouseReleased(float x, float y, MouseButton button) {
        held = false;
        frame.save();
    }
}
