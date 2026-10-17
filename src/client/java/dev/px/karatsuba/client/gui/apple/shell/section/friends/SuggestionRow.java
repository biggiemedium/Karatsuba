package dev.px.karatsuba.client.gui.apple.shell.section.friends;

import dev.px.karatsuba.client.gui.apple.shell.setting.ControlRow;

import java.util.function.Consumer;

/**
 * A player who is not a friend yet, with an Add button: someone on this server, or
 * the name typed into the search. The quickest way to add someone; the whole row
 * adds, as its button does.
 */
public final class SuggestionRow extends ControlRow {

    private final String name;
    private final String caption;
    private final Consumer<String> add;

    /** @param caption a few words beside the name ("Not a friend yet"); empty for none */
    SuggestionRow(String name, String caption, Consumer<String> add) {
        this.name = name;
        this.caption = caption;
        this.add = add;
        tooltip("Add " + name + " as a friend");
    }

    @Override
    public String name() {
        return name;
    }

    public String caption() {
        return caption;
    }

    @Override
    protected boolean pressed(float x, float y) {
        add.accept(name);
        return true;
    }

    @Override
    protected boolean activated() {
        add.accept(name);
        return true;
    }

    @Override
    protected void reset() {
    }

    @Override
    public boolean isFocusable() {
        return true;
    }
}
