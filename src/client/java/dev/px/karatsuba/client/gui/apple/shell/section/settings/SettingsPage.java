package dev.px.karatsuba.client.gui.apple.shell.section.settings;

import dev.px.core.setting.Setting;
import dev.px.core.setting.impl.GroupSetting;
import dev.px.gui.Widget;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * One page of the Settings section: a title, a sentence saying what it governs,
 * an optional note under that, and its settings.
 *
 * <p>The settings are read through a supplier, not copied, so a page always
 * shows what its owner holds now: a client section's own fields, or one of
 * Core's services.
 *
 * <h2>A page that lays itself out</h2>
 * Most pages are their settings in order. A page with a {@code layout} decides its
 * own rows instead, for when the order is a sequence of steps and some steps are
 * not settings: a status and the button that changes it, say. It is handed the
 * function that makes a setting's row, and returns every row in the order shown.
 * {@code settings} still lists the page's settings, for search, the changed count
 * and reset; the other rows are none of those things. The layout runs each time
 * the page is built, and its rows read their values live.
 */
public record SettingsPage(String id, String title, String description, String note,
                           Supplier<List<Setting<?>>> settings,
                           Function<Function<Setting<?>, Widget>, List<Widget>> layout) {

    /** A page of settings, in order, and nothing else. */
    public SettingsPage(String id, String title, String description, String note,
                        Supplier<List<Setting<?>>> settings) {
        this(id, title, description, note, settings, null);
    }

    /** @return every setting on the page that can show, a group's children included, groups themselves not */
    public List<Setting<?>> flat() {
        List<Setting<?>> out = new ArrayList<>();
        for (Setting<?> setting : settings.get()) {
            flatten(setting, out);
        }
        return out;
    }

    /** @return how many settings on the page differ from their defaults */
    public int modified() {
        int count = 0;
        for (Setting<?> setting : flat()) {
            if (!setting.isDefault()) {
                count++;
            }
        }
        return count;
    }

    /** Puts every changed setting on the page back to its default. */
    public void resetAll() {
        for (Setting<?> setting : flat()) {
            setting.reset();
        }
    }

    private static void flatten(Setting<?> setting, List<Setting<?>> out) {
        if (!setting.isVisible()) {
            return;
        }
        if (setting instanceof GroupSetting group) {
            for (Setting<?> child : group.getChildren()) {
                flatten(child, out);
            }
        } else {
            out.add(setting);
        }
    }
}
