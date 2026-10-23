package dev.px.karatsuba.client.hud.preset;

/**
 * Where one of an element's plates is and how round it is, in screen space: what
 * the HUD editor outlines, so an outline follows the plate's own shape (a capsule,
 * a tile, a rounded rectangle) rather than the element's bounding box.
 */
public record PlateShape(float x, float y, float width, float height, float radius) {
}
