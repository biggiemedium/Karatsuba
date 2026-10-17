package dev.px.karatsuba.client.gui.apple.shell.setting;

import dev.px.core.layout.Align;
import dev.px.karatsuba.client.gui.apple.shell.frame.FixedColumn;
import dev.px.karatsuba.client.gui.apple.shell.frame.Spacer;
import dev.px.karatsuba.client.gui.apple.shell.style.Palette;
import dev.px.gui.container.Row;
import dev.px.karatsuba.client.gui.apple.shell.card.CardRow;
import dev.px.gui.widget.TextField;

import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * Free text that is not a {@code Setting}: a friend's alias or note, or a name to
 * add. Looks like a {@link TextRow}, and saves as it is typed.
 */
public final class FieldRow extends Row implements CardRow {

    private boolean carded;

    @Override
    public void markCarded() {
        this.carded = true;
    }

    @Override
    public boolean isCarded() {
        return carded;
    }

    private final String name;
    private final Supplier<String> value;
    private final Field field;

    public FieldRow(String name, String placeholder, int maxLength, Supplier<String> value, Consumer<String> set) {
        this.name = name;
        this.value = value;
        // The field at a set width against the right edge, centred in the row's height.
        align(Align.CENTER);
        add(new Spacer(0f)).grow(1f);
        this.field = add(new FixedColumn(Palette.FIELD_WIDTH + 20f)).add(new Field(value.get()));
        field.font(Palette.body());
        field.placeholder(placeholder);
        field.maxLength(maxLength);
        field.onChange(set);
    }

    public String name() {
        return name;
    }

    /** Enter in the field: {@code submit} gets what it holds. */
    public FieldRow onSubmit(Consumer<String> submit) {
        field.onSubmit(submit);
        return this;
    }

    /** @return what the field holds now */
    public String text() {
        return field.getText();
    }

    /** Puts the caret in the field. */
    public void grab() {
        field.grab();
    }

    /** Types into the field, taking the caret there first. */
    public void insert(String typed) {
        field.grab();
        field.insert(typed);
    }

    public boolean isEditing() {
        return field.isFocused();
    }

    /** Shows the stored value while nobody is typing in the field. Once a frame. */
    public void refresh() {
        String now = value.get();
        if (!field.isFocused() && !field.getText().equals(now)) {
            field.setText(now);
        }
    }

    /** The row's text field, able to take the caret when asked. */
    private static final class Field extends TextField {

        Field(String text) {
            super(text);
        }

        void grab() {
            focus();
        }
    }
}
