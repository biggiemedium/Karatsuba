package dev.px.karatsuba.client.gui.shell.setting;

import dev.px.core.module.Module;
import dev.px.core.setting.SettingHolder;
import dev.px.core.setting.Setting;
import dev.px.core.setting.impl.BindSetting;
import dev.px.core.setting.impl.BooleanSetting;
import dev.px.core.setting.impl.ColorSetting;
import dev.px.core.setting.impl.EnumSetting;
import dev.px.core.setting.impl.GroupSetting;
import dev.px.core.setting.impl.MultiEnumSetting;
import dev.px.core.setting.impl.NumberSetting;
import dev.px.core.setting.impl.RangeSetting;
import dev.px.core.setting.impl.StringSetting;
import dev.px.gui.Widget;
import dev.px.gui.container.Column;
import dev.px.karatsuba.client.gui.shell.frame.Fold;
import dev.px.karatsuba.client.gui.shell.frame.Spacer;
import dev.px.karatsuba.client.gui.shell.widget.Caption;
import dev.px.karatsuba.client.gui.shell.widget.SectionHeader;
import dev.px.karatsuba.client.gui.shell.style.Palette;
import dev.px.karatsuba.client.setting.ChoiceSetting;
import dev.px.karatsuba.client.setting.ItemSetting;

import java.util.ArrayList;
import java.util.List;

/**
 * A module's settings, laid out in sections: its loose settings under
 * "General", each of its groups under its own folding heading, and the bind and
 * ArrayList switch every module has under "Module" at the end.
 *
 * <p>Built once per module shown, never per frame, so a row keeps its hover and
 * its switch's motion for as long as its module is up.
 */
public final class SettingList {

    private final List<TextRow> textRows = new ArrayList<>();
    private final boolean detailed;
    private java.util.function.Consumer<ControlRow> decorate = row -> {
    };

    /** Compact rows, descriptions as tooltips: for a module's or a HUD element's settings. */
    public SettingList() {
        this(false);
    }

    /** @param detailed descriptions under each name, and changed values marked: for the Settings section */
    public SettingList(boolean detailed) {
        this.detailed = detailed;
    }

    /** Runs {@code decorator} on each row as it is built, to add a note or anything else. */
    public SettingList decorate(java.util.function.Consumer<ControlRow> decorator) {
        this.decorate = decorator;
        return this;
    }

    /** Fills {@code into} with {@code owner}'s settings: a module's, a HUD element's, anything holding settings. */
    public void build(SettingHolder owner, Column into) {
        build(owner, into, false);
    }

    /**
     * @param alwaysHead whether loose settings get their "General" heading even
     *                   when nothing else follows them in the list
     */
    public void build(SettingHolder owner, Column into, boolean alwaysHead) {
        build(owner.getSettings(), owner instanceof Module module ? module : null, into, alwaysHead ? "General" : null);
    }

    /**
     * Fills {@code into} with {@code settings}, in sections: loose settings first,
     * under {@code looseTitle} if given (or "General" when groups follow), then each
     * group, then, for a module, the bind and ArrayList switch every module has.
     */
    public void build(List<Setting<?>> settings, Module module, Column into, String looseTitle) {
        List<Setting<?>> general = new ArrayList<>();
        List<GroupSetting> groups = new ArrayList<>();
        List<Setting<?>> own = new ArrayList<>();
        for (Setting<?> setting : settings) {
            if (setting instanceof GroupSetting group) {
                groups.add(group);
            } else if (module != null && isEveryModules(module, setting)) {
                own.add(setting);
            } else {
                general.add(setting);
            }
        }

        boolean headed = looseTitle != null || !groups.isEmpty() || !own.isEmpty();
        boolean first = true;
        if (!general.isEmpty()) {
            if (headed) {
                into.add(new SectionHeader(looseTitle != null ? looseTitle : "General", null));
            }
            addRows(into, general);
            first = false;
        }
        for (GroupSetting group : groups) {
            addGroup(into, group, first);
            first = false;
        }
        if (!own.isEmpty()) {
            if (!first) {
                into.add(new Spacer(Palette.GAP_SECTION));
            }
            into.add(new SectionHeader("Module", null));
            addRows(into, own);
        }
    }

    /** Forgets the rows built so far, before building afresh. */
    public void clear() {
        textRows.clear();
    }

    /** Shows each text setting's saved value while it is not being typed in. Once a frame. */
    public void refresh() {
        for (TextRow row : textRows) {
            row.refresh();
        }
    }

    private void addGroup(Column into, GroupSetting group, boolean first) {
        if (!first) {
            into.add(new Spacer(Palette.GAP_SECTION)).visibleWhen(group::isVisible);
        }
        into.add(new SectionHeader(group.getName(), group));
        Fold fold = into.add(new Fold(group::isExpanded));
        fold.gap(Palette.GAP_ROW);
        fold.visibleWhen(() -> group.isVisible() && fold.showing());
        List<Setting<?>> loose = new ArrayList<>();
        for (Setting<?> child : group.getChildren()) {
            if (child instanceof GroupSetting nested) {
                addRows(fold, loose);
                loose.clear();
                addGroup(fold, nested, false);
            } else {
                loose.add(child);
            }
        }
        addRows(fold, loose);
    }

    private void addRows(Column into, List<Setting<?>> settings) {
        for (Setting<?> setting : settings) {
            into.add(row(setting));
        }
    }

    /** One setting's row, in this list's density. */
    public Widget row(Setting<?> setting) {
        Widget row = rowFor(setting);
        if (row instanceof ControlRow control) {
            if (detailed) {
                control.detailed();
            }
            decorate.accept(control);
        } else if (row instanceof TextRow text && detailed) {
            text.detailed();
        }
        return row;
    }

    /** The control for a setting, chosen by its type. */
    @SuppressWarnings({"unchecked", "rawtypes"})
    private Widget rowFor(Setting<?> setting) {
        if (setting instanceof BooleanSetting toggle) {
            return new ToggleRow(toggle);
        }
        if (setting instanceof NumberSetting<?> number) {
            return new SliderRow(number);
        }
        if (setting instanceof RangeSetting range) {
            return new RangeRow(range);
        }
        if (setting instanceof EnumSetting<?> choice) {
            return ChoiceRow.of((EnumSetting) choice);
        }
        if (setting instanceof MultiEnumSetting<?> choices) {
            return new MultiChoiceRow((MultiEnumSetting) choices);
        }
        if (setting instanceof ColorSetting color) {
            return new ColorRow(color);
        }
        if (setting instanceof BindSetting bind) {
            return new BindRow(bind);
        }
        if (setting instanceof StringSetting text) {
            TextRow row = new TextRow(text);
            textRows.add(row);
            return row;
        }
        if (setting instanceof ChoiceSetting choice) {
            return ChoiceRow.of(choice);
        }
        if (setting instanceof ItemSetting items) {
            return new ItemRow(items);
        }
        // A type this GUI has no control for: still listed, read-only.
        Widget unknown = new Caption(() -> setting.getName() + ": " + setting.displayValue());
        return unknown.visibleWhen(setting::isVisible);
    }

    /** Whether a setting is one {@code ClientModule} gives every module: its bind and its ArrayList switch. */
    private static boolean isEveryModules(Module module, Setting<?> setting) {
        return setting == module.getToggleBind()
                || (setting instanceof BooleanSetting && setting.getName().equals("Visible"));
    }
}
