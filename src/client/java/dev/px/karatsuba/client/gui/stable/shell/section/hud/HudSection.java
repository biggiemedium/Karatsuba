package dev.px.karatsuba.client.gui.stable.shell.section.hud;

import dev.px.core.Core;
import dev.px.core.hud.HudElement;
import dev.px.core.hud.HudLayout;
import dev.px.core.setting.Setting;
import dev.px.core.setting.SettingHolder;
import dev.px.gui.Widget;
import dev.px.gui.container.Row;
import dev.px.karatsuba.client.config.AutoSave;
import dev.px.karatsuba.client.hud.style.HudStyle;
import dev.px.karatsuba.client.hud.style.StyledElement;
import dev.px.karatsuba.client.gui.stable.shell.Section;
import dev.px.karatsuba.client.gui.stable.shell.Shell;
import dev.px.karatsuba.client.gui.stable.shell.frame.ModuleColumn;
import dev.px.karatsuba.client.gui.stable.shell.frame.Reveal;
import dev.px.karatsuba.client.gui.stable.shell.frame.SettingsColumn;
import dev.px.karatsuba.client.gui.stable.shell.frame.SmoothScroll;
import dev.px.karatsuba.client.gui.stable.shell.frame.Spacer;
import dev.px.karatsuba.client.gui.stable.shell.rail.Glyph;
import dev.px.karatsuba.client.gui.stable.shell.section.layout.LayoutSection;
import dev.px.karatsuba.client.gui.stable.shell.setting.ActionRow;
import dev.px.karatsuba.client.gui.stable.shell.setting.AnchorRow;
import dev.px.karatsuba.client.gui.stable.shell.setting.SettingList;
import dev.px.karatsuba.client.gui.stable.shell.setting.SwitchRow;
import dev.px.karatsuba.client.gui.stable.shell.setting.ValueRow;
import dev.px.karatsuba.client.gui.stable.shell.style.Palette;
import dev.px.karatsuba.client.gui.stable.shell.widget.Caption;
import dev.px.karatsuba.client.gui.stable.shell.widget.InspectorHeader;
import dev.px.karatsuba.client.gui.stable.shell.widget.ListItem;
import dev.px.karatsuba.client.gui.stable.shell.widget.NavItem;
import dev.px.karatsuba.client.gui.stable.shell.widget.PillButton;
import dev.px.karatsuba.client.gui.stable.shell.widget.SectionHeader;
import dev.px.karatsuba.client.gui.stable.shell.widget.SectionTitle;
import dev.px.karatsuba.client.gui.stable.shell.widget.TextButton;

import java.util.ArrayList;
import java.util.List;

/**
 * The HUD: every element in a list with a switch to show or hide it, and the
 * chosen element's own settings and its place on screen beside them, laid out
 * exactly as the modules are.
 *
 * <p>Where an element sits is set here by numbers (anchor, scale, lock, order)
 * or by hand in the {@link LayoutSection} editor, entered from the button under
 * the list or from an element's "Edit on screen". Both edit the same layout.
 */
public final class HudSection implements Section {

    public static final String ID = "hud";

    /** What {@link #lastSelected} holds while the HUD Style is chosen rather than an element. */
    private static final String STYLE = "#style";

    /** The element last chosen, across openings, and for the editor to start on. */
    private static String lastSelected;

    private final Shell shell;
    private final Row content;
    private final ModuleColumn list;
    private final SettingsColumn inspector;
    private final SettingList settings = new SettingList();
    private final Reveal paneReveal = new Reveal();

    private HudElement selected;
    private HudElement built;
    private boolean styleSelected;
    private boolean builtStyle;
    private boolean firstBuild = true;

    public HudSection(Shell shell) {
        this.shell = shell;
        this.content = new Row();
        List<HudElement> elements = Core.hud().getElements().all();

        // ---- the elements, under the section's title
        this.list = content.add(new ModuleColumn(new Reveal()));
        list.gap(Palette.GAP_ROW);
        list.add(new SectionTitle("HUD", () -> shownCount() + " of " + elements.size() + " shown"));
        list.add(new Spacer(Palette.INSET - Palette.GAP_ROW));
        SmoothScroll rows = list.add(new SmoothScroll());
        rows.gap(Palette.GAP_ROW);
        rows.grow(1f);
        // The style every element inherits, first: it is where a HUD's look starts.
        rows.add(new NavItem("Style", () -> "All elements", () -> styleSelected, this::selectStyle))
                .tooltip("The panel, colours and text protection every element uses unless it has its own");
        rows.add(new Spacer(Palette.GAP_ROW));
        for (HudElement element : elements) {
            rows.add(new ListItem(element::getDisplayName, null,
                    () -> isShown(element), () -> toggleShown(element),
                    () -> !styleSelected && element == selected, () -> select(element)));
        }
        Row bulk = list.add(new Row().gap(Palette.GAP_ROW));
        bulk.add(new TextButton("Show all", () -> false, () -> setAllShown(true)));
        bulk.add(new TextButton("Hide all", () -> false, () -> setAllShown(false)));
        list.add(new Spacer(Palette.GAP_ROW));
        list.add(new PillButton(Glyph.LAYOUT, "Edit layout", false, PillButton.Style.PRIMARY, null, null,
                () -> shell.show(LayoutSection.ID)))
                .tooltip("Move and scale the elements on screen    Ctrl+E");

        // ---- the chosen element
        this.inspector = content.add(new SettingsColumn(paneReveal));
        inspector.gap(Palette.GAP_ROW * 2f);
        inspector.grow(1f);
        shell.veil(paneReveal, () -> inspector);

        HudElement start = lastSelected == null ? null : Core.hud().find(lastSelected).orElse(null);
        selected = start != null ? start : elements.isEmpty() ? null : elements.get(0);
        styleSelected = STYLE.equals(lastSelected);
    }

    /** Makes {@code id} the chosen element: the editor hands back what was selected there. */
    public static void remember(String id) {
        if (id != null) {
            lastSelected = id;
        }
    }

    /** @return the id of the element chosen here, for the editor to start with selected; may be null */
    public static String selectedId() {
        return lastSelected;
    }

    // --------------------------------------------------------------- Section

    @Override
    public String id() {
        return ID;
    }

    @Override
    public String title() {
        return "HUD";
    }

    @Override
    public Glyph glyph() {
        return Glyph.HUD;
    }

    @Override
    public Widget content() {
        return content;
    }

    @Override
    public void entered() {
        // Coming back from the editor: show the element that was selected there.
        if (lastSelected != null && (selected == null || !selected.getId().equals(lastSelected))) {
            Core.hud().find(lastSelected).ifPresent(this::select);
        }
    }

    @Override
    public void tick() {
        float squeeze = Math.max(0f, Math.min(1f, (Palette.FRAME_WIDTH - shell.windowWidth()) / 280f));
        list.width(Palette.LIST_WIDTH + 14f - 36f * squeeze);
        if (built != selected || builtStyle != styleSelected || firstBuild) {
            rebuild();
        }
        settings.refresh();
    }

    // ------------------------------------------------------------- internals

    private void selectStyle() {
        if (!styleSelected) {
            paneReveal.start();
        }
        styleSelected = true;
        lastSelected = STYLE;
    }

    private void select(HudElement element) {
        if (element != selected || styleSelected) {
            paneReveal.start();
        }
        selected = element;
        styleSelected = false;
        lastSelected = element == null ? null : element.getId();
    }

    private static boolean isShown(HudElement element) {
        HudLayout layout = Core.hud().layoutOf(element);
        return layout != null && !layout.isHidden();
    }

    private static void toggleShown(HudElement element) {
        HudLayout layout = Core.hud().layoutOf(element);
        if (layout != null) {
            layout.toggleHidden();
            AutoSave.markDirty();
        }
    }

    private static void setAllShown(boolean shown) {
        for (HudElement element : Core.hud().getElements().all()) {
            if (isShown(element) != shown) {
                toggleShown(element);
            }
        }
    }

    private static int shownCount() {
        int count = 0;
        for (HudElement element : Core.hud().getElements().all()) {
            if (isShown(element)) {
                count++;
            }
        }
        return count;
    }

    /** Fills the inspector for the chosen element; only when the choice changes. */
    private void rebuild() {
        firstBuild = false;
        built = selected;
        builtStyle = styleSelected;
        inspector.clear();
        settings.clear();
        if (styleSelected) {
            buildStyle();
            return;
        }
        if (selected == null) {
            inspector.add(new Caption("No HUD elements"));
            return;
        }
        HudElement element = selected;
        String id = element.getId();
        inspector.add(new InspectorHeader(element.getDisplayName(), "Shown", "Hidden",
                () -> isShown(element), () -> toggleShown(element)));
        inspector.add(new Spacer(Palette.INSET - Palette.GAP_ROW * 2f));
        SmoothScroll rows = inspector.add(new SmoothScroll());
        rows.gap(Palette.GAP_ROW);
        rows.grow(1f);

        boolean ownSettings = element instanceof SettingHolder holder && !holder.getSettings().isEmpty();
        if (ownSettings) {
            settings.build(ordered((SettingHolder) element), null, rows, "General");
            rows.add(new Spacer(Palette.GAP_SECTION));
        }

        HudLayout defaults = element.defaultLayout();
        rows.add(new SectionHeader("Layout", null));
        rows.add(new AnchorRow("Anchor", "Which part of the screen it holds to as the window changes size",
                defaults.getAnchor(), () -> layout(element).getAnchor(), anchor -> {
            Core.hud().setAnchor(id, anchor);
            AutoSave.markDirty();
        }));
        rows.add(new ValueRow("Scale", 0.5d, 3d, 0.05d, defaults.getScale(),
                () -> layout(element).getScale(), value -> {
            layout(element).setScale((float) value);
            AutoSave.markDirty();
        }, value -> Math.round(value * 100d) + "%"));
        rows.add(new SwitchRow("Locked", "A locked element cannot be dragged in the editor",
                defaults.isLocked(), () -> layout(element).isLocked(), locked -> {
            layout(element).setLocked(locked);
            AutoSave.markDirty();
        }));
        rows.add(new ActionRow("Order", "Which element is drawn on top where two overlap", List.of(
                new ActionRow.Action("To front", () -> {
                    Core.hud().bringToFront(id);
                    AutoSave.markDirty();
                }),
                new ActionRow.Action("To back", () -> {
                    Core.hud().sendToBack(id);
                    AutoSave.markDirty();
                }))));
        rows.add(new ActionRow("Position", "Put it back where it started, or place it by hand", List.of(
                new ActionRow.Action("Reset", () -> {
                    Core.hud().resetLayout(id);
                    AutoSave.markDirty();
                }),
                new ActionRow.Action("Edit on screen", () -> shell.show(LayoutSection.ID)))));
    }

    /** Fills the inspector with the HUD Style: what every element looks like unless it has its own. */
    private void buildStyle() {
        inspector.add(new SectionTitle("HUD Style", () -> "Every element, unless it has Custom Style on"));
        inspector.add(new Spacer(Palette.INSET - Palette.GAP_ROW * 2f));
        SmoothScroll rows = inspector.add(new SmoothScroll());
        rows.gap(Palette.GAP_ROW);
        rows.grow(1f);
        settings.build(HudStyle.get(), rows);
        rows.add(new Spacer(Palette.GAP_SECTION));
        rows.add(new Caption("Every element sits on the plate. Each element's Style group can give it"
                + " its own panel, colours and font instead (Custom Style)."));
    }

    /** @return an element's settings with its Style group moved last, after what is particular to it */
    private static List<Setting<?>> ordered(SettingHolder holder) {
        List<Setting<?>> list = new ArrayList<>(holder.getSettings());
        if (holder instanceof StyledElement styled && list.remove(styled.styleGroup())) {
            list.add(styled.styleGroup());
        }
        return list;
    }

    private static HudLayout layout(HudElement element) {
        return Core.hud().layoutOf(element);
    }
}
