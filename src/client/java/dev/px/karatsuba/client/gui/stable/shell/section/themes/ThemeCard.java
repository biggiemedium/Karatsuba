package dev.px.karatsuba.client.gui.stable.shell.section.themes;

import dev.px.core.Core;
import dev.px.core.input.MouseButton;
import dev.px.core.render.theme.Theme;
import dev.px.gui.Widget;
import dev.px.karatsuba.client.gui.stable.shell.style.Animations;
import dev.px.karatsuba.client.gui.stable.shell.style.Easing;
import dev.px.karatsuba.client.gui.stable.shell.style.Motion;

/**
 * One theme in the picker: its gradient, a switch and a slider drawn in its
 * accent so you can see what it would look like, and its name. The active one
 * is outlined and ticked.
 *
 * <p>Cards arrive one after another when the picker opens, 20ms apart and never
 * more than 160ms for the whole grid, so the grid reads as one arrival with a
 * little direction rather than a slow procession.
 */
public final class ThemeCard extends Widget {

    private final Theme theme;
    private final int index;
    private final ThemeView view;

    private final Motion hover = Motion.hover();
    private final Motion active;

    public ThemeCard(Theme theme, int index, ThemeView view) {
        this.theme = theme;
        this.index = index;
        this.view = view;
        this.active = Motion.toggle(isActive());
    }

    public Theme theme() {
        return theme;
    }

    public boolean isActive() {
        return Core.themes().getActive() == theme;
    }

    public float hover() {
        return hover.toward(isHovered());
    }

    /** @return how chosen it looks, 0 to 1 */
    public float chosen() {
        return active.toward(isActive());
    }

    /** @return how far it has arrived, 0 to 1, after its place in the stagger */
    public float appear() {
        if (!Animations.enabled() || view.openedAt() == 0L) {
            return 1f;
        }
        float delay = Math.min(index * Animations.STAGGER, Animations.STAGGER_CAP);
        long start = view.openedAt() + (long) (delay * 1_000_000L);
        if (System.nanoTime() < start) {
            return 0f;
        }
        return Easing.DECELERATE.apply(Animations.progress(start, Animations.VIEW));
    }

    @Override
    protected boolean mousePressed(float x, float y, MouseButton button) {
        if (button != MouseButton.LEFT) {
            return false;
        }
        Core.themes().setActive(theme);
        return true;
    }

    @Override
    protected boolean activated() {
        Core.themes().setActive(theme);
        return true;
    }

    @Override
    public boolean isFocusable() {
        return true;
    }
}
