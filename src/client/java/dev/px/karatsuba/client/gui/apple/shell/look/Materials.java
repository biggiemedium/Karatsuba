package dev.px.karatsuba.client.gui.apple.shell.look;

import dev.px.core.render.Color;
import dev.px.core.render.Render;
import dev.px.karatsuba.client.gui.apple.shell.style.Appearance;
import dev.px.karatsuba.client.gui.apple.shell.style.Palette;
import dev.px.karatsuba.client.render.Glass;

/**
 * The Apple UI's surfaces ({@code gui/apple/DESIGN.md} §5): Liquid Glass for
 * the control layer and the near-opaque window under the content, as the
 * Appearance settings ask for them.
 *
 * <p>Drawn by the host before the Screen that sits on them, never from a widget's
 * renderer (rule 1.4). With Reduce Transparency on, each is a solid fill instead
 * and no pass runs.
 */
public final class Materials {

    /** The blur at Clear and at Tinted, as mip levels. */
    private static final float BLUR_CLEAR = 2.2f;
    private static final float BLUR_TINTED = 3.6f;

    /** How strongly the glass tone is mixed in, at Clear and at Tinted. */
    private static final float TINT_CLEAR = 0.06f;
    private static final float TINT_TINTED = 0.55f;

    private Materials() {
    }

    /** @return a layer for the shapes about to be drawn side by side */
    public static Glass.Layer layer() {
        return Glass.layer();
    }

    /**
     * Liquid Glass.
     *
     * @param presence 0 to 1: how formed it is (animate this, not opacity)
     * @param press    0 to 1: a press brightening the rim
     */
    public static void glass(Glass.Layer layer, float x, float y, float w, float h, float radius,
                             float presence, float press) {
        Appearance appearance = Appearance.get();
        if (appearance.reduceTransparency()) {
            solid(x, y, w, h, radius, Palette.GLASS_SOLID.get(), presence);
            return;
        }
        float amount = appearance.glass();
        float blur = BLUR_CLEAR + (BLUR_TINTED - BLUR_CLEAR) * amount;
        float tint = TINT_CLEAR + (TINT_TINTED - TINT_CLEAR) * amount;
        layer.draw(x, y, w, h, radius, Glass.Optics.glass(Palette.GLASS_RIM, blur),
                Palette.GLASS_TONE.get().withAlpha(Math.round(tint * 255f)), presence, press);
    }

    /** The backdrop's blur: deep enough that the game behind is never legible. */
    private static final float BLUR_BACKDROP = 4f;

    /**
     * The game behind the open GUI: dimmed and blurred, like a macOS sheet's
     * background (§5.1). Dimmed alone, with no pass, under Reduce Transparency.
     */
    public static void backdrop(Glass.Layer layer, float width, float height, float presence) {
        if (presence <= 0f) {
            return;
        }
        if (Appearance.get().reduceTransparency()) {
            Render.rect(0f, 0f, width, height, Palette.faded(Palette.BACKDROP_DIM.withAlpha(150), presence));
            return;
        }
        layer.draw(0f, 0f, width, height, 0f, Glass.Optics.material(BLUR_BACKDROP), Palette.BACKDROP_DIM,
                presence, 0f);
    }

    /**
     * The window's content pane: a near-opaque solid in the window colour, with
     * only a hint of the game through it. Not glass, and no pass: rows never sit on
     * glass (§5.1). Fully opaque with Reduce Transparency on.
     */
    public static void pane(float x, float y, float w, float h, float radius, float presence) {
        Color fill = Appearance.get().reduceTransparency() ? Palette.PANE_SOLID.get() : Palette.WINDOW.get();
        solid(x, y, w, h, radius, fill, presence);
    }

    private static void solid(float x, float y, float w, float h, float radius, Color fill, float presence) {
        if (presence > 0f) {
            Render.roundRect(x, y, w, h, radius, Palette.faded(fill, presence));
        }
    }
}
