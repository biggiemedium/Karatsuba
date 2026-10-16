package dev.px.karatsuba.client.gui.apple.shell.frame;

import dev.px.core.layout.Bounds;

/**
 * A widget in the control layer that sits on Liquid Glass ({@code gui/apple/DESIGN.md}
 * §1.4). Its renderer names the part the glass fills, {@link #PART}; the host draws
 * the glass there before the widget's own Screen, since no widget may draw a pass.
 */
public interface GlassSurface {

    /** The part every glass surface's renderer names. */
    String PART = "glass";

    /** @return where the glass goes, from the last update; null before the first */
    Bounds glassBounds();

    /** @return its corner radius, in screen units */
    float glassRadius();

    /** @return 0 to 1: a press brightening its rim */
    default float glassPress() {
        return 0f;
    }
}
