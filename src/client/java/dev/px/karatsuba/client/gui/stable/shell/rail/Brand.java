package dev.px.karatsuba.client.gui.stable.shell.rail;

import dev.px.gui.Widget;

/**
 * The client's mark at the top of the rail: its initial on a tile of the
 * theme's gradient, with the full name as its tooltip. Takes no presses, so
 * dragging it drags the window.
 */
public final class Brand extends Widget {

    private final String name;

    public Brand(String name) {
        this.name = name;
        tooltip(name);
    }

    public String name() {
        return name;
    }

    public String initial() {
        return name.isEmpty() ? "" : name.substring(0, 1).toUpperCase();
    }
}
