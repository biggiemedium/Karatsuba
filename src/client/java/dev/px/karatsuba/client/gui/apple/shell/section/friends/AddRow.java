package dev.px.karatsuba.client.gui.apple.shell.section.friends;

import dev.px.core.input.MouseButton;
import dev.px.gui.Widget;
import dev.px.karatsuba.client.gui.apple.shell.style.Motion;

import java.util.function.Consumer;

/**
 * At the top of the list while the field holds a name that is not a friend yet:
 * "Add Steve", pressed or taken with Enter. Searching and adding are one field,
 * so a name is found before it can be added twice.
 */
public final class AddRow extends Widget {

    private final String name;
    private final Consumer<String> add;
    private final Motion hover = Motion.hover();

    AddRow(String name, Consumer<String> add) {
        this.name = name;
        this.add = add;
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
}
