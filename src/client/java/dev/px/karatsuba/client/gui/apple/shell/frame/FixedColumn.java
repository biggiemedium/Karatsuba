package dev.px.karatsuba.client.gui.apple.shell.frame;

import dev.px.gui.container.Column;

/**
 * A column of a set width beside things that grow: a row's text field, say. A
 * look's {@code width()} cannot do this round a slot, since a box holding a slot
 * always takes a share of the room spare; a child's own width can.
 */
public final class FixedColumn extends Column {

    private final float width;

    public FixedColumn(float width) {
        this.width = width;
    }

    public float width() {
        return width;
    }
}
