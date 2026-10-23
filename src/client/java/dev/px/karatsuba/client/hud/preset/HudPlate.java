package dev.px.karatsuba.client.hud.preset;

/**
 * One plate behind an element, in screen space: the rectangle its glass is drawn
 * at, already scaled by the element's scale ({@code hud/DESIGN.md} §4.2).
 *
 * @param owner    the element's id, so what is behind its plates can be followed
 *                 from frame to frame ({@link dev.px.karatsuba.client.hud.preset.glass.Backdrop})
 * @param capsule  whether it holds a single row, and so is a capsule (§4.9)
 * @param presence 0 to 1: how formed it is, following the element's fade (§6.5 of
 *                 the Apple rules: glass forms and melts, it does not fade)
 * @param cue      0 to 1: how strongly a change cue lights its rim now (§4.12)
 */
public record HudPlate(String owner, float x, float y, float width, float height, boolean capsule, float presence,
                       float cue) {
}
