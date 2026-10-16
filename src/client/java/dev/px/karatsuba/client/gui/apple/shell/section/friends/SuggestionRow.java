package dev.px.karatsuba.client.gui.apple.shell.section.friends;

import dev.px.core.input.MouseButton;
import dev.px.gui.Widget;
import dev.px.karatsuba.client.gui.apple.shell.style.Motion;

import java.util.function.Consumer;

/**
 * A player on this server who is not a friend, with a button to add them: the
 * quickest way to add someone, without typing their name.
 */
public final class SuggestionRow extends Widget {

    private final String name;
    private final Consumer<String> add;
    private final Motion hover = Motion.hover();

    SuggestionRow(String name, Consumer<String> add) {
        this.name = name;
        this.add = add;
        tooltip("Add " + name + " as a friend");
    }

    public String name() {
        return name;
    }

    public float hover() {
        return hover.toward(isHovered());
    }

    @Override
    protected boolean mousePressed(float x, float y, MouseButton button) {
        if (button != MouseButton.LEFT) {
            return false;
        }
        add.accept(name);
        return true;
    }

    @Override
    protected boolean activated() {
        add.accept(name);
        return true;
    }

    @Override
    public boolean isFocusable() {
        return true;
    }
}
