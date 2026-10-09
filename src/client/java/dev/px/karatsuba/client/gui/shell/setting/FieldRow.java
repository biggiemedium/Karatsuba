package dev.px.karatsuba.client.gui.shell.setting;

import dev.px.gui.container.Row;
import dev.px.gui.widget.TextField;

import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * Free text that is not a {@code Setting}: a friend's alias or note. Looks like
 * a {@link TextRow}, and saves as it is typed.
 */
public final class FieldRow extends Row {

    private final String name;
    private final Supplier<String> value;
    private final TextField field;

    public FieldRow(String name, String placeholder, int maxLength, Supplier<String> value, Consumer<String> set) {
        this.name = name;
        this.value = value;
        this.field = add(new TextField(value.get()));
        field.grow(1f);
        field.placeholder(placeholder);
        field.maxLength(maxLength);
        field.onChange(set);
    }

    public String name() {
        return name;
    }

    /** Shows the stored value while nobody is typing in the field. Once a frame. */
    public void refresh() {
        String now = value.get();
        if (!field.isFocused() && !field.getText().equals(now)) {
            field.setText(now);
        }
    }
}
