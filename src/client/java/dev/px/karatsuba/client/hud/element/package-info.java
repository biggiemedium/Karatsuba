/**
 * The HUD elements themselves.
 *
 * <p>Foldered away from {@code hud/} for the same reason modules are foldered
 * away from {@code module/}: the catalogue and the content are edited in the
 * same breath but read for different reasons, and a list is easier to scan when
 * it is not surrounded by the things it lists.
 *
 * <p>Each one answers Core's two questions &mdash; who am I, and what am I made
 * of &mdash; and nothing more. Anchoring, scaling, clamping, z-order, hit
 * testing, dragging and persistence all happen above them, which is why none of
 * them computes a coordinate.
 */
package dev.px.karatsuba.client.hud.element;
