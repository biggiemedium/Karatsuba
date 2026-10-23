package dev.px.karatsuba.client.hud.preset.role;

import dev.px.core.render.Color;

/**
 * A small glyph an element shows: drawn in a square of {@code size}, in one colour
 * the preset picks. The element says which icon; the preset says how big, in what
 * colour, and whether it sits in a well (§3.2's icon and iconWell).
 */
@FunctionalInterface
public interface HudIcon {

    void draw(float x, float y, float size, Color colour);
}
