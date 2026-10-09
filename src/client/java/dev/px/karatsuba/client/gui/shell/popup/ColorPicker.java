package dev.px.karatsuba.client.gui.shell.popup;

import dev.px.core.input.MouseButton;
import dev.px.core.render.Color;
import dev.px.core.setting.impl.ColorSetting;
import dev.px.gui.Widget;

/**
 * Picks a colour: saturation and brightness on a square, hue and opacity on bars
 * under it, and two switches for following the theme or cycling a rainbow.
 *
 * <p>Works in hue, saturation and brightness rather than red, green and blue,
 * because that is how people think about a colour ("the same, but darker"). The
 * hue is kept here rather than read back from the colour, so dragging to black
 * or grey and back does not lose it.
 */
public final class ColorPicker extends Widget {

    public static final float SQUARE_WIDTH = 140f;
    public static final float SQUARE_HEIGHT = 84f;
    public static final float BAR_HEIGHT = 8f;

    public final ColorSetting setting;

    public float hue;
    public float saturation;
    public float brightness;

    /** Which part the current drag holds. */
    private String dragging;

    public ColorPicker(ColorSetting setting) {
        this.setting = setting;
        float[] hsb = hsb(setting.get());
        this.hue = hsb[0];
        this.saturation = hsb[1];
        this.brightness = hsb[2];
    }

    public float alpha() {
        return setting.get().getAlpha() / 255f;
    }

    /** The picked hue at full saturation and brightness: the square's colour. */
    public Color pureHue() {
        return Color.hsb(hue, 1f, 1f);
    }

    @Override
    protected boolean mousePressed(float x, float y, MouseButton button) {
        if (button != MouseButton.LEFT) {
            return false;
        }
        if (in("rainbow", x, y)) {
            setting.rainbow(!setting.isRainbow());
            return true;
        }
        if (in("theme", x, y)) {
            setting.syncToTheme(!setting.isSyncToTheme());
            return true;
        }
        for (String part : new String[]{"square", "hue", "alpha"}) {
            if (in(part, x, y)) {
                dragging = part;
                capture();
                mouseDragged(x, y);
                return true;
            }
        }
        // Inside the picker but on nothing: still ours, so it does not close.
        return true;
    }

    @Override
    protected void mouseDragged(float x, float y) {
        var bounds = dragging == null ? null : part(dragging);
        if (bounds == null) {
            return;
        }
        float across = clamp((x - bounds.getX()) / bounds.getWidth());
        float down = clamp((y - bounds.getY()) / bounds.getHeight());
        int alpha = setting.get().getAlpha();
        switch (dragging) {
            case "square":
                saturation = across;
                brightness = 1f - down;
                break;
            case "hue":
                hue = across;
                break;
            case "alpha":
                alpha = Math.round(across * 255f);
                break;
            default:
                return;
        }
        // Picking a colour by hand means this colour, not the theme's or a rainbow's.
        setting.rainbow(false);
        setting.syncToTheme(false);
        setting.set(Color.hsb(hue, saturation, brightness).withAlpha(alpha));
    }

    @Override
    protected void mouseReleased(float x, float y, MouseButton button) {
        dragging = null;
    }

    private boolean in(String part, float x, float y) {
        var bounds = part(part);
        return bounds != null && bounds.contains(x, y);
    }

    private static float clamp(float value) {
        return Math.max(0f, Math.min(1f, value));
    }

    /** @return hue, saturation and brightness, each 0 to 1 */
    public static float[] hsb(Color color) {
        float r = color.redF();
        float g = color.greenF();
        float b = color.blueF();
        float max = Math.max(r, Math.max(g, b));
        float min = Math.min(r, Math.min(g, b));
        float delta = max - min;
        float hue = 0f;
        if (delta > 0f) {
            if (max == r) {
                hue = ((g - b) / delta) % 6f;
            } else if (max == g) {
                hue = (b - r) / delta + 2f;
            } else {
                hue = (r - g) / delta + 4f;
            }
            hue /= 6f;
            if (hue < 0f) {
                hue += 1f;
            }
        }
        return new float[]{hue, max == 0f ? 0f : delta / max, max};
    }
}
