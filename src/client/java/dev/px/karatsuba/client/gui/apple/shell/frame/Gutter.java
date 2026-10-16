package dev.px.karatsuba.client.gui.apple.shell.frame;

import dev.px.gui.container.Column;

import java.util.function.DoubleSupplier;

/**
 * Room beside something, as wide as it says each frame: where the sidebar floats
 * over the window's left edge, narrower when the sidebar collapses to its icons.
 */
public final class Gutter extends Column {

    private final DoubleSupplier width;

    public Gutter(DoubleSupplier width) {
        this.width = width;
    }

    public float width() {
        return (float) width.getAsDouble();
    }
}
