package dev.px.karatsuba.client.gui.apple.shell.section.layout;

import dev.px.core.hud.Placement;
import dev.px.core.layout.Bounds;
import dev.px.karatsuba.client.hud.preset.PlateShape;
import dev.px.karatsuba.client.hud.style.StyledElement;

import java.util.List;

/**
 * The shapes the editor outlines an element by: its plates as its preset drew them
 * last frame (a capsule, a tile, a rounded rectangle, one per toast), so the
 * outline, the selection's ring and the badge follow the element's own shape
 * rather than its bounding box. An element with no plates is its bounds.
 */
final class Outlines {

    private Outlines() {
    }

    static List<PlateShape> of(Placement placement) {
        Bounds b = placement.getBounds();
        if (placement.getElement() instanceof StyledElement element) {
            List<PlateShape> shapes = element.outlines(b.getX(), b.getY(), placement.getLayout().getScale(),
                    placement.getNatural().getWidth(), placement.getNatural().getHeight());
            if (!shapes.isEmpty()) {
                return shapes;
            }
        }
        return List.of(new PlateShape(b.getX(), b.getY(), b.getWidth(), b.getHeight(), 0f));
    }
}
