package dev.px.karatsuba.client.gui.shell.setting;

import dev.px.core.setting.impl.StringSetting;
import dev.px.gui.container.Row;
import dev.px.gui.widget.TextField;

/**
 * Free text: the name, then a field taking the rest of the row.
 *
 * <p>Every edit is written to the setting as it is typed. A setting that refuses
 * a value (one with a validator) keeps its old one, and the field shows the
 * setting again once it loses focus, so what you see is what is saved.
 */
public final class TextRow extends Row {

    private final StringSetting setting;
    private final TextField field;
    private boolean detailed;

    public TextRow(StringSetting setting) {
        this.setting = setting;
        this.field = add(new TextField(setting.get()));
        field.grow(1f);
        if (setting.getMaxLength() > 0) {
            field.maxLength(setting.getMaxLength());
        }
        field.onChange(setting::set);
        visibleWhen(setting::isVisible);
        if (!setting.getDescription().isEmpty()) {
            tooltip(setting.getDescription());
        }
    }

    public String name() {
        return setting.getName();
    }

    public String description() {
        return setting.getDescription();
    }

    public boolean isModified() {
        return !setting.isDefault();
    }

    /** Shows the description under the name, for the Settings section. */
    public TextRow detailed() {
        this.detailed = true;
        tooltip((String) null);
        return this;
    }

    public boolean isDetailed() {
        return detailed;
    }

    /** Shows the setting's value while nobody is typing in the field. Once a frame. */
    public void refresh() {
        if (!field.isFocused() && !field.getText().equals(setting.get())) {
            field.setText(setting.get());
        }
    }
}
