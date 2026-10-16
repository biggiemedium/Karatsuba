package dev.px.karatsuba.client.gui.apple.shell.section.appearance;

import dev.px.core.Core;
import dev.px.core.layout.Bounds;
import dev.px.core.render.theme.Theme;
import dev.px.karatsuba.client.gui.apple.shell.setting.ControlRow;
import dev.px.karatsuba.client.gui.apple.shell.style.Palette;

import java.util.List;

/**
 * The theme picker ({@code gui/apple/DESIGN.md} §4.6), as macOS Appearance → Color:
 * a circle per theme, split diagonally into its primary and secondary, the chosen
 * one ringed. Its name and the chosen theme's on the first line; the circles under
 * them, wrapping to the row's width. Choosing one sets Core's active theme.
 */
public final class ThemeCircles extends ControlRow {

    private final List<Theme> themes;

    public ThemeCircles() {
        this.themes = List.copyOf(Core.themes().getThemes().all());
    }

    @Override
    public String name() {
        return "Color";
    }

    public List<Theme> themes() {
        return themes;
    }

    public boolean isChosen(Theme theme) {
        return Core.themes().getActive() == theme;
    }

    /** @return how many circles fit on a line, from last frame's width (a sensible guess before the first) */
    public int perLine() {
        Bounds last = getBounds();
        float width = last == null || last.getWidth() <= 0f ? 300f : last.getWidth() - Palette.CELL_INSET * 2f;
        float step = Palette.THEME_CIRCLE + Palette.GAP_THEME;
        return Math.max(1, (int) ((width + Palette.GAP_THEME) / step));
    }

    /** The row is not one target, so it has no hover of its own; each circle answers for itself. */
    @Override
    public float hover() {
        return 0f;
    }

    @Override
    protected boolean pressed(float x, float y) {
        for (int i = 0; i < themes.size(); i++) {
            if (inside("theme" + i, x, y)) {
                Core.themes().setActive(themes.get(i));
                return true;
            }
        }
        return false;
    }

    /** Nothing to put back: the theme is a choice, not a value with a default here. */
    @Override
    protected void reset() {
    }
}
