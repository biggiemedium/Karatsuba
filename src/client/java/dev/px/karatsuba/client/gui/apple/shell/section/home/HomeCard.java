package dev.px.karatsuba.client.gui.apple.shell.section.home;

import dev.px.core.input.MouseButton;
import dev.px.gui.Widget;
import dev.px.karatsuba.client.gui.apple.shell.rail.Glyph;
import dev.px.karatsuba.client.gui.apple.shell.style.Motion;

import java.util.function.Supplier;

/**
 * One section at a glance, and the way into it.
 *
 * <p>Every card is laid out the same, so the eye learns where to look: the
 * section's icon and name as on the rail, one number that says how things stand
 * ("7 of 36 on"), a bar under it where a share means something, and a line of
 * detail. The whole card opens its section. A card may carry one action of its
 * own in its corner (the HUD's "Edit layout"), set apart from the rest.
 */
public final class HomeCard extends Widget {

    private final Glyph glyph;
    private final String title;
    private final Supplier<String> metric;
    private final Supplier<String> detail;
    private final Supplier<Float> bar;
    private final Runnable open;
    private final String actionLabel;
    private final Runnable action;

    private final Motion hover = Motion.hover();
    private boolean swatch;

    public HomeCard(Glyph glyph, String title, Supplier<String> metric, Supplier<String> detail,
                    Supplier<Float> bar, Runnable open, String actionLabel, Runnable action) {
        this.glyph = glyph;
        this.title = title;
        this.metric = metric;
        this.detail = detail;
        this.bar = bar;
        this.open = open;
        this.actionLabel = actionLabel;
        this.action = action;
        tooltip("Open " + title);
    }

    public Glyph glyph() {
        return glyph;
    }

    public String title() {
        return title;
    }

    public String metric() {
        return metric.get();
    }

    public String detail() {
        return detail.get();
    }

    /** @return the bar's fill, 0 to 1, or null for a card without one */
    public Float bar() {
        return bar == null ? null : bar.get();
    }

    /** @return the card's own action's label, or null */
    public String actionLabel() {
        return actionLabel;
    }

    /** Shows the theme's gradient where other cards show a bar: the Themes card. */
    public HomeCard swatch() {
        this.swatch = true;
        return this;
    }

    public boolean isSwatch() {
        return swatch;
    }

    public float hover() {
        return hover.toward(isHovered());
    }

    @Override
    protected boolean mousePressed(float x, float y, MouseButton button) {
        if (button != MouseButton.LEFT) {
            return false;
        }
        var corner = part("action");
        if (action != null && corner != null && corner.contains(x, y)) {
            action.run();
        } else {
            open.run();
        }
        return true;
    }

    @Override
    protected boolean activated() {
        open.run();
        return true;
    }

    @Override
    public boolean isFocusable() {
        return true;
    }
}
