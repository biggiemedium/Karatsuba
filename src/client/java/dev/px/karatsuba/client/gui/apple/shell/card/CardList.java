package dev.px.karatsuba.client.gui.apple.shell.card;

import dev.px.core.module.Module;
import dev.px.core.setting.Setting;
import dev.px.core.setting.SettingHolder;
import dev.px.core.setting.impl.BooleanSetting;
import dev.px.core.setting.impl.GroupSetting;
import dev.px.gui.Widget;
import dev.px.karatsuba.client.gui.apple.shell.setting.SettingList;
import dev.px.karatsuba.client.setting.SettingLabels;

import java.util.ArrayList;
import java.util.List;

/**
 * Settings as inset grouped cards ({@code gui/apple/DESIGN.md} §2.4): the loose
 * settings in the first card (under "General" when groups follow), each group in a
 * card under its name, nested groups in cards of their own, and, for a module,
 * the bind and ArrayList switch every module has in a "Module" card at the end.
 *
 * <p>The rows are {@link SettingList}'s, chosen by setting type exactly as
 * everywhere else, so a setting looks and behaves the same wherever it shows. A
 * card hides while none of its rows show.
 */
public final class CardList {

    private final SettingList rows;

    /** @param detailed descriptions under each name, and changed values marked */
    public CardList(boolean detailed) {
        this.rows = new SettingList(detailed);
    }

    /** Adds {@code owner}'s settings to {@code page}, as cards. */
    public void build(SettingHolder owner, CardPage page) {
        build(owner.getSettings(), owner instanceof Module m ? m : null, page);
    }

    /** Runs {@code decorator} on each row as it is built: a keybind's clash warning, say. */
    public CardList decorate(java.util.function.Consumer<dev.px.karatsuba.client.gui.apple.shell.setting.ControlRow> decorator) {
        rows.decorate(decorator);
        return this;
    }

    /** @return one setting's row, as this list makes them: for a page that lays itself out */
    public Widget row(Setting<?> setting) {
        return rows.row(setting);
    }

    /** Adds {@code settings} to {@code page} as cards, as {@link #build(SettingHolder, CardPage)} does. */
    public void build(List<Setting<?>> settings, Module module, CardPage page) {
        List<Setting<?>> loose = new ArrayList<>();
        List<GroupSetting> groups = new ArrayList<>();
        List<Setting<?>> own = new ArrayList<>();
        for (Setting<?> setting : settings) {
            if (setting instanceof GroupSetting group) {
                groups.add(group);
            } else if (module != null && isEveryModules(module, setting)) {
                own.add(setting);
            } else {
                loose.add(setting);
            }
        }
        if (!loose.isEmpty()) {
            card(page, groups.isEmpty() && own.isEmpty() ? null : "General", loose, () -> true);
        }
        for (GroupSetting group : groups) {
            addGroup(page, group);
        }
        if (!own.isEmpty()) {
            card(page, "Module", own, () -> true);
        }
    }

    /** Shows each text setting's saved value while it is not being typed in. Once a frame. */
    public void refresh() {
        rows.refresh();
    }

    /** Forgets the rows built so far, before building afresh. */
    public void clear() {
        rows.clear();
    }

    private void addGroup(CardPage page, GroupSetting group) {
        List<Setting<?>> loose = new ArrayList<>();
        List<GroupSetting> nested = new ArrayList<>();
        for (Setting<?> child : group.getChildren()) {
            if (child instanceof GroupSetting inner) {
                nested.add(inner);
            } else {
                loose.add(child);
            }
        }
        if (!loose.isEmpty()) {
            card(page, SettingLabels.of(group), loose, group::isVisible);
        }
        for (GroupSetting inner : nested) {
            addGroup(page, inner);
        }
    }

    private void card(CardPage page, String header, List<Setting<?>> settings,
                      java.util.function.BooleanSupplier shown) {
        var group = page.group(header);
        Card card = group.add(new Card());
        for (Setting<?> setting : settings) {
            card.add(rows.row(setting));
        }
        group.visibleWhen(() -> shown.getAsBoolean() && card.getChildren().stream().anyMatch(Widget::isVisible));
    }

    /** Whether a setting is one {@code ClientModule} gives every module: its bind and its ArrayList switch. */
    private static boolean isEveryModules(Module module, Setting<?> setting) {
        return setting == module.getToggleBind()
                || (setting instanceof BooleanSetting && setting.getName().equals("Visible"));
    }
}
