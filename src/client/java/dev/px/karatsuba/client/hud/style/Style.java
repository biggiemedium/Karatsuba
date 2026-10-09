package dev.px.karatsuba.client.hud.style;

import dev.px.core.render.Color;
import dev.px.core.render.font.Font;

/**
 * One element's style for one frame: the global HUD Style, or the element's own
 * where it has Custom Style on. Resolved fresh each frame, so a change to either
 * shows at once.
 *
 * @param plate   the plate behind every element, its opacity already applied
 * @param radius  the plate's corner radius
 * @param padding between the plate's edge and what is on it
 * @param gap     between rows
 * @param label   what a value is: "Ping"
 * @param value   the value itself: "42ms"
 * @param muted   secondary text, and the empty part of bars
 * @param font    the face everything is set in
 */
public record Style(Color plate, float radius, float padding, float gap,
                    Color label, Color value, Color muted, Font font) {
}
