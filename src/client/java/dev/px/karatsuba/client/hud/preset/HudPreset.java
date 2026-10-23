package dev.px.karatsuba.client.hud.preset;

import dev.px.core.layout.Content;
import dev.px.core.render.Color;
import dev.px.karatsuba.client.hud.preset.role.HudCategory;
import dev.px.karatsuba.client.hud.preset.role.HudIcon;
import dev.px.karatsuba.client.hud.style.StyledElement;

/**
 * A whole way of drawing the HUD: Legacy, and later Apple ({@code hud/DESIGN.md}
 * §1). Elements describe roles (§3); a preset decides how every one of them looks,
 * and draws each, even if only simply (§3.3), so no element ever needs a version
 * of its own for a preset.
 *
 * <p>Text roles are drawn by {@link StyledElement} in the preset's fonts and
 * colours ({@link #tokens}); the plate and the structural roles are drawn here.
 * Colours given to the drawing methods are already lit by any change cue.
 */
public interface HudPreset {

    /** @return a stable id, for saving which preset is chosen */
    String id();

    /** @return the name the player sees */
    String name();

    /** @return {@code element}'s tokens for this frame */
    HudTokens tokens(StyledElement element);

    /** Puts the plate behind an element's content, faded by {@code opacity} (0 to 1), and its padding inside. */
    void plate(Content content, HudTokens tokens, float opacity);

    /** Draws a plate in a custom part, for an element that plates each item itself (a toast). */
    void drawPlate(float x, float y, float w, float h, HudTokens tokens);

    /** A meter of {@code max} steps with {@code level} lit, in {@code fill}: ordered data (§2.13). */
    void meter(Content into, HudTokens tokens, int level, int max, Color fill);

    /** A thick rounded progress bar ({@code progress} 0 to 1), {@code width} wide, faded by {@code alpha}. */
    void track(Content into, HudTokens tokens, float width, float progress, float alpha);

    /** An icon the size of a line of text, in {@code colour}. */
    void icon(Content into, HudTokens tokens, HudIcon icon, Color colour);

    /** An icon in a well of its own. A preset with no wells draws the bare icon (§3.3). */
    void iconWell(Content into, HudTokens tokens, HudIcon icon, Color colour);

    /** Whether something is switched on, as a small indicator. */
    void toggleOn(Content into, HudTokens tokens, boolean on);

    /** A title over a subtitle, in their own fonts; {@code subtitle} empty for none. */
    void titleBlock(Content into, HudTokens tokens, String title, Color titleColour,
                    String subtitle, Color subtitleColour);

    /**
     * Draws which of a small fixed set something is (the categoryMarker, §2.13) at the
     * left of an item {@code height} tall at {@code x, y}, in a custom part.
     *
     * @return how far right of {@code x} the item's text starts
     */
    float categoryMarker(float x, float y, float height, HudTokens tokens, HudCategory category);

    /** Draws how long an item has left ({@code remaining} 1 to 0) along its bottom; nothing for a preset without one. */
    default void countdown(float x, float y, float width, float height, float remaining, HudTokens tokens,
                           HudCategory category) {
    }

    /** @return how a plate moves to a new size, at Motion: Full: Legacy's ease, Apple's spring (§4.12) */
    default Curve resizeCurve() {
        return Curve.of(dev.px.core.render.animation.Easing.CUBIC_OUT);
    }

    /** @return how long a plate takes to reach a new size, at Motion: Full */
    default long resizeMillis() {
        return 220L;
    }

    /**
     * @return how far a toast still is from its place, in natural units, while it
     *         arrives or leaves ({@code presence} 0 to 1): it comes in from the side
     *         it is anchored to. Toasts always move, whatever Motion says (§2.10)
     */
    default float toastSlide(float presence) {
        return (1f - presence) * 12f;
    }

    /** @return whether a change cue lights the text; a preset with a rim to brighten instead says no (§4.12) */
    default boolean cuesText(HudTokens tokens) {
        return true;
    }

    /** @return whether a plate {@code height} tall (natural units) holds a single row, and so is a capsule (§4.9) */
    default boolean capsule(HudTokens tokens, float height) {
        return false;
    }

    /**
     * @return the corner radius {@code plate} is drawn with, in screen units at
     *         {@code scale}: what the HUD editor outlines it with, so the outline is the
     *         plate's own shape (a capsule, a tile, a rounded rectangle)
     */
    float plateRadius(HudTokens tokens, HudPlate plate, float scale);

    /** @return whether text lines up by the element's anchor (§2.6); Apple left-aligns inside a plate (§4.16) */
    default boolean alignsByAnchor() {
        return true;
    }

    /**
     * @return whether this preset draws its plates as passes behind the elements
     *         (glass), through {@link #shadow} and {@link #glass}; a preset whose
     *         plates are plain fills draws them in {@link #plate} instead
     */
    default boolean drawsBehind() {
        return false;
    }

    /**
     * Draws a plate's shadow, before any glass, at its scaled screen bounds.
     * {@code scale} is the element's, which the radius and rim follow (§4.2).
     */
    default void shadow(HudPlate plate, float scale) {
    }

    /**
     * Draws a plate's glass through {@code layer}, whose one capture every plate shares (§4.3).
     *
     * @return false if no pass could run here (inside an alpha push, say): the host then
     *         draws {@link #standIn} instead, so a plate never goes missing
     */
    default boolean glass(dev.px.karatsuba.client.render.Glass.Layer layer, HudPlate plate, float scale) {
        return true;
    }

    /** Draws a plate's solid stand-in, where its glass could not be drawn. */
    default void standIn(HudPlate plate, float scale) {
    }
}
