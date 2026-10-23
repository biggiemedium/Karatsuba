/**
 * HUD presets: whole ways of drawing the HUD ({@code hud/DESIGN.md} §1). Elements
 * describe roles; the active preset turns them into pixels.
 *
 * <p>This package is the seam everything else uses: {@link
 * dev.px.karatsuba.client.hud.preset.HudPreset} (what a preset must draw), {@link
 * dev.px.karatsuba.client.hud.preset.HudPresets} (which one is drawing now), {@link
 * dev.px.karatsuba.client.hud.preset.HudTokens} (a preset's fonts and colours for an
 * element), {@link dev.px.karatsuba.client.hud.preset.HudPlate} and {@link
 * dev.px.karatsuba.client.hud.preset.PlateShape} (where plates are), and {@link
 * dev.px.karatsuba.client.hud.preset.Curve} (how plates move).
 *
 * <table>
 *   <caption>What each folder holds</caption>
 *   <tr><td>{@code role/}</td><td>what elements describe: text and colour roles, icons, categories</td></tr>
 *   <tr><td>{@code color/}</td><td>accent variants and contrast lifting, shared by the presets</td></tr>
 *   <tr><td>{@code glass/}</td><td>the host that draws glass plates behind the elements, and the backdrop reading</td></tr>
 *   <tr><td>{@code apple/}</td><td>the Apple preset and its glyphs</td></tr>
 *   <tr><td>{@code legacy/}</td><td>the Legacy preset</td></tr>
 * </table>
 *
 * <p>Adding a preset is a folder of its own beside {@code apple/} and {@code legacy/},
 * and one line in {@code HudPresets}; no element changes.
 */
package dev.px.karatsuba.client.hud.preset;
