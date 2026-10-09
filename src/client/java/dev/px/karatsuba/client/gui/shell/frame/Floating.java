package dev.px.karatsuba.client.gui.shell.frame;

import dev.px.core.layout.Bounds;

/**
 * A widget the {@link Panel} places itself rather than letting it fill the
 * window: a resize grip along an edge, a veil over one column.
 */
public interface Floating {

    /** @return where it goes, given the window's inside */
    Bounds placeIn(Bounds window);
}
