package dev.px.karatsuba.client.gui.stable.shell.frame;

import dev.px.gui.container.Column;

/** Room between sections, of a fixed height. */
public final class Spacer extends Column {

    private final float height;

    public Spacer(float height) {
        this.height = height;
    }

    public float height() {
        return height;
    }
}
