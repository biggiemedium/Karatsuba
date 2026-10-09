package dev.px.karatsuba.client.gui.shell.setting;

import dev.px.core.setting.Setting;

/**
 * A row editing one {@code Setting}. The setting's description is the row's
 * tooltip, rather than a line under every row, which keeps the list short
 * enough to scan; a setting hidden by the module (one that only applies in
 * another mode) takes no room, read live as the mode changes.
 */
public abstract class SettingRow<S extends Setting<?>> extends ControlRow {

    protected final S setting;

    protected SettingRow(S setting) {
        this.setting = setting;
        visibleWhen(setting::isVisible);
        if (!setting.getDescription().isEmpty()) {
            tooltip(setting.getDescription());
        }
    }

    public S setting() {
        return setting;
    }

    @Override
    public String name() {
        return setting.getName();
    }

    @Override
    public String description() {
        return setting.getDescription();
    }

    @Override
    public boolean isModified() {
        return !setting.isDefault();
    }

    @Override
    protected void reset() {
        setting.reset();
    }
}
