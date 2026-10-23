package dev.px.karatsuba.client.gui.apple.shell.section.hud;

import dev.px.core.Core;
import dev.px.core.hud.HudElement;
import dev.px.core.hud.HudLayout;
import dev.px.core.setting.Setting;
import dev.px.core.setting.SettingHolder;
import dev.px.gui.Widget;
import dev.px.gui.container.Column;
import dev.px.karatsuba.client.config.AutoSave;
import dev.px.karatsuba.client.hud.group.HudGroups;
import dev.px.karatsuba.client.gui.apple.shell.Section;
import dev.px.karatsuba.client.gui.apple.shell.Shell;
import dev.px.karatsuba.client.gui.apple.shell.card.Card;
import dev.px.karatsuba.client.gui.apple.shell.card.CardList;
import dev.px.karatsuba.client.gui.apple.shell.card.CardPage;
import dev.px.karatsuba.client.gui.apple.shell.card.PushButton;
import dev.px.karatsuba.client.gui.apple.shell.frame.PageStack;
import dev.px.karatsuba.client.gui.apple.shell.rail.Glyph;
import dev.px.karatsuba.client.gui.apple.shell.section.appearance.AppearanceSection;
import dev.px.karatsuba.client.gui.apple.shell.section.layout.LayoutSection;
import dev.px.karatsuba.client.gui.apple.shell.setting.ActionRow;
import dev.px.karatsuba.client.gui.apple.shell.setting.AnchorRow;
import dev.px.karatsuba.client.gui.apple.shell.setting.ChoiceRow;
import dev.px.karatsuba.client.gui.apple.shell.setting.InfoRow;
import dev.px.karatsuba.client.gui.apple.shell.setting.LinkRow;
import dev.px.karatsuba.client.gui.apple.shell.setting.SwitchRow;
import dev.px.karatsuba.client.gui.apple.shell.setting.ValueRow;
import dev.px.karatsuba.client.gui.apple.shell.widget.PageIntro;
import dev.px.karatsuba.client.hud.preset.HudPresets;
import dev.px.karatsuba.client.hud.style.HudStyle;
import dev.px.karatsuba.client.hud.style.StyledElement;

import java.util.ArrayList;
import java.util.List;

/**
 * The HUD, as Tahoe's System Settings pages ({@code hud/DESIGN.md} §7.1b): a list
 * page with a card of HUD Settings and Legacy Settings…, a card of the elements
 * (each with its Shown switch and a ›), and the Edit HUD button under them,
 * right-aligned; each pushed in from the right.
 *
 * <ul>
 *   <li><b>HUD Settings:</b> the Appearance card (Preset, Glass and Appearance while
 *       Apple draws, the accent), then Accessibility and Behaviour.</li>
 *   <li><b>Legacy Settings:</b> Plate, Text and ArrayList, with a Reset that asks twice.</li>
 *   <li><b>An element:</b> its own settings, its Display card, a Layout card, and
 *       Edit on Screen… under them.</li>
 * </ul>
 *
 * <p>Placing elements by hand is the editor's ({@link LayoutSection}), opened from
 * the Edit HUD button or an element's Edit on Screen….
 */
public final class HudSection implements Section {

    public static final String ID = "hud";

    /** The places of the pages under the list, for the history; an element's is its id. */
    private static final String SETTINGS = "#settings";
    private static final String LEGACY = "#legacy";

    /** How long a Reset waits for its confirming second press. */
    private static final long CONFIRM_MILLIS = 3000L;

    /** The element last chosen, across openings, and for the editor to start on. */
    private static String lastSelected;

    private final Shell shell;
    private final PageStack stack = new PageStack();
    private final CardPage list = new CardPage();
    private final CardPage settingsPage = new CardPage();
    private final CardPage legacyPage = new CardPage();
    private final CardPage elementPage = new CardPage();
    private final CardList hudRows = new CardList(true);
    private final CardList legacyRows = new CardList(true);
    private final CardList elementRows = new CardList(true);

    /** What is open under the list: {@link #SETTINGS}, {@link #LEGACY}, an element's id, or null for the list. */
    private String open;
    private String built;
    private long resetArmedAt;

    public HudSection(Shell shell) {
        this.shell = shell;
        stack.page(list);
        stack.page(settingsPage);
        stack.page(legacyPage);
        stack.page(elementPage);

        Card hud = list.card(null);
        hud.add(new LinkRow("HUD Settings", () -> HudPresets.active().name(), () -> push(SETTINGS)));
        LinkRow legacy = hud.add(new LinkRow("Legacy Settings", () -> "", () -> push(LEGACY)));
        legacy.visibleWhen(HudSection::legacyDraws);

        Column elements = list.group("Elements");
        Card card = elements.add(new Card());
        for (HudElement element : Core.hud().getElements().all()) {
            card.add(new ElementRow(element, () -> isShown(element), () -> toggleShown(element),
                    () -> push(element.getId())));
        }
        list.actions(elements, new PushButton("Edit HUD…", () -> shell.show(LayoutSection.ID)));
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
        return stack;
    }

    @Override
    public void tick() {
        // Legacy Settings only while Legacy draws: a page for another preset's look would mislead.
        if (LEGACY.equals(open) && !legacyDraws()) {
            open = null;
            stack.jump(list);
        }
        if (open != null && !open.equals(built)) {
            build(open);
        }
        hudRows.refresh();
        legacyRows.refresh();
        elementRows.refresh();
    }

    @Override
    public String heading() {
        if (open == null) {
            return title();
        }
        if (SETTINGS.equals(open)) {
            return "HUD Settings";
        }
        if (LEGACY.equals(open)) {
            return "Legacy Settings";
        }
        return Core.hud().find(open).map(HudElement::getDisplayName).orElse(title());
    }

    @Override
    public String place() {
        return open == null ? "" : open;
    }

    @Override
    public boolean slidesPlaces() {
        return true;
    }

    @Override
    public void restore(String place, int direction) {
        open = place.isEmpty() || !exists(place) ? null : place;
        if (open != null) {
            build(open);
        }
        Widget page = pageFor(open);
        if (direction > 0) {
            stack.push(page);
        } else if (direction < 0) {
            stack.pop(page);
        } else {
            stack.jump(page);
        }
    }

    /** Escape on a page goes back to the list, as ‹ does. */
    @Override
    public boolean back() {
        if (open == null) {
            return false;
        }
        open = null;
        stack.pop(list);
        return true;
    }

    // ----------------------------------------------------------------- pages

    private void push(String place) {
        open = place;
        resetArmedAt = 0L;
        if (!SETTINGS.equals(place) && !LEGACY.equals(place)) {
            lastSelected = place;
        }
        build(place);
        stack.push(pageFor(place));
    }

    private Widget pageFor(String place) {
        if (place == null) {
            return list;
        }
        return SETTINGS.equals(place) ? settingsPage : LEGACY.equals(place) ? legacyPage : elementPage;
    }

    private boolean exists(String place) {
        return SETTINGS.equals(place) || LEGACY.equals(place) || Core.hud().find(place).isPresent();
    }

    /** Fills a page: only when another opens, never per frame. */
    private void build(String place) {
        built = place;
        if (SETTINGS.equals(place)) {
            buildSettings();
        } else if (LEGACY.equals(place)) {
            buildLegacy();
        } else {
            Core.hud().find(place).ifPresent(this::buildElement);
        }
    }

    /** HUD Settings: Appearance, then Accessibility and Behaviour (§1.8). */
    private void buildSettings() {
        HudStyle hud = HudStyle.get();
        settingsPage.clearCards();
        hudRows.clear();
        Card appearance = settingsPage.card("Appearance");
        appearance.add(ChoiceRow.of(hud.presetSetting()));
        // Apple only (§1.4): each row hides with its setting while another preset draws.
        appearance.add(ChoiceRow.of(hud.glassSetting()));
        appearance.add(ChoiceRow.of(hud.appearanceSetting()));
        appearance.add(new InfoRow("Accent", () -> Core.themes().getActive().getName(), "Appearance…",
                () -> shell.show(AppearanceSection.ID)).describe("The theme's colour, shared with the GUI"));
        hudRows.build(List.<Setting<?>>of(hud.accessibilityGroup(), hud.behaviourGroup()), null, settingsPage);
    }

    /** Legacy Settings: Plate, Text and ArrayList, and a Reset that asks twice (§5). */
    private void buildLegacy() {
        HudStyle hud = HudStyle.get();
        legacyPage.clearCards();
        legacyRows.clear();
        legacyPage.group(null).add(new PageIntro("How the Legacy preset draws every element. An element's Display"
                + " card can give it its own instead (Custom Style)"));
        legacyRows.build(new ArrayList<Setting<?>>(hud.legacyGroups()), null, legacyPage);
        Column foot = legacyPage.group(null);
        legacyPage.actions(foot, new PushButton(this::resetLabel, this::resetLegacy));
    }

    /** An element: its own settings, its Display card, its Layout card, and Edit on Screen…. */
    private void buildElement(HudElement element) {
        elementPage.clearCards();
        elementRows.clear();
        String id = element.getId();
        if (element instanceof SettingHolder holder && !holder.getSettings().isEmpty()) {
            elementRows.build(ordered(holder), null, elementPage);
        }
        HudLayout defaults = element.defaultLayout();
        Column layoutGroup = elementPage.group("Layout");
        Card layout = layoutGroup.add(new Card());
        layout.add(new AnchorRow("Anchor", "Which part of the screen it holds to as the window changes size",
                defaults.getAnchor(), () -> layoutOf(element).getAnchor(), anchor -> {
            Core.hud().setAnchor(id, anchor);
            AutoSave.markDirty();
        }));
        layout.add(new ValueRow("Scale", 0.5d, 3d, 0.05d, defaults.getScale(),
                () -> layoutOf(element).getScale(), value -> {
            layoutOf(element).setScale((float) value);
            AutoSave.markDirty();
        }, value -> Math.round(value * 100d) + "%"));
        layout.add(new SwitchRow("Locked", "A locked element cannot be dragged in the editor",
                defaults.isLocked(), () -> layoutOf(element).isLocked(), locked -> {
            layoutOf(element).setLocked(locked);
            AutoSave.markDirty();
        }));
        layout.add(new ActionRow("Order", "Which element is drawn on top where two overlap", List.of(
                new ActionRow.Action("To Front", () -> {
                    Core.hud().bringToFront(id);
                    AutoSave.markDirty();
                }),
                new ActionRow.Action("To Back", () -> {
                    Core.hud().sendToBack(id);
                    AutoSave.markDirty();
                }))));
        elementPage.actions(layoutGroup,
                new PushButton("Reset Position", () -> {
                    HudGroups.resetLayout(id);
                    AutoSave.markDirty();
                }),
                new PushButton("Edit on Screen…", () -> {
                    lastSelected = id;
                    shell.show(LayoutSection.ID);
                }));
    }

    // ---------------------------------------------------------------- legacy

    private void resetLegacy() {
        if (armed()) {
            resetArmedAt = 0L;
            HudStyle.get().resetLegacy();
            AutoSave.markDirty();
        } else {
            resetArmedAt = System.currentTimeMillis();
        }
    }

    private String resetLabel() {
        return armed() ? "Click Again to Reset" : "Reset Legacy Settings…";
    }

    private boolean armed() {
        return resetArmedAt != 0L && System.currentTimeMillis() - resetArmedAt < CONFIRM_MILLIS;
    }

    private static boolean legacyDraws() {
        return HudPresets.active() == HudPresets.LEGACY;
    }

    // -------------------------------------------------------------- elements

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

    /** @return an element's settings with its Display group moved last, after what is particular to it */
    private static List<Setting<?>> ordered(SettingHolder holder) {
        List<Setting<?>> list = new ArrayList<>(holder.getSettings());
        if (holder instanceof StyledElement styled && list.remove(styled.styleGroup())) {
            list.add(styled.styleGroup());
        }
        return list;
    }

    private static HudLayout layoutOf(HudElement element) {
        return Core.hud().layoutOf(element);
    }
}
