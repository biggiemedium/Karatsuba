package dev.px.karatsuba.client.gui.shell.widget;

import dev.px.core.input.MouseButton;
import dev.px.gui.Widget;
import dev.px.karatsuba.client.gui.shell.rail.Glyph;
import dev.px.karatsuba.client.gui.shell.style.Motion;

import java.util.function.BooleanSupplier;
import java.util.function.Supplier;

/**
 * A button: a glyph and a label, or a glyph alone with the label as its tooltip.
 *
 * <p>Three weights. {@link Style#PRIMARY} is filled with the accent and is used
 * once per view at most (Done, Edit layout), so the one thing to do next is
 * plain. {@link Style#TOGGLE} lights while its state is on (Snap).
 * {@link Style#PLAIN} is everything else.
 */
public final class PillButton extends Widget {

    public enum Style {
        PLAIN, PRIMARY, TOGGLE
    }

    private final Glyph glyph;
    private Supplier<String> label;
    private final boolean iconOnly;
    private final Style style;
    private final BooleanSupplier lit;
    private final BooleanSupplier enabled;
    private final Runnable action;

    private final Motion hover = Motion.hover();
    private final Motion on;
    private boolean rounded;

    public PillButton(Glyph glyph, String label, boolean iconOnly, Style style,
                      BooleanSupplier lit, BooleanSupplier enabled, Runnable action) {
        this.glyph = glyph;
        this.label = () -> label;
        this.iconOnly = iconOnly;
        this.style = style;
        this.lit = lit == null ? () -> false : lit;
        this.enabled = enabled == null ? () -> true : enabled;
        this.action = action;
        this.on = Motion.toggle(this.lit.getAsBoolean());
        enabledWhen(this.enabled);
        if (iconOnly) {
            tooltip(() -> this.label.get());
        }
    }

    /** A plain button with a glyph and a label. */
    public static PillButton plain(Glyph glyph, String label, Runnable action) {
        return new PillButton(glyph, label, false, Style.PLAIN, null, null, action);
    }

    public Glyph glyph() {
        return glyph;
    }

    public String label() {
        return label.get();
    }

    /** Reads the label live, for a button whose words change (one asking to confirm). */
    public PillButton labelledBy(Supplier<String> source) {
        this.label = source;
        return this;
    }

    /**
     * Fully rounds the button's ends. For a button inside something itself fully
     * rounded, such as the editor's toolbar, so their curves run alongside each
     * other and no corner reaches past the edge around it.
     */
    public PillButton rounded() {
        this.rounded = true;
        return this;
    }

    public boolean isRounded() {
        return rounded;
    }

    public boolean iconOnly() {
        return iconOnly;
    }

    public Style style() {
        return style;
    }

    public float hover() {
        return hover.toward(isHovered() && isEnabled());
    }

    /** @return how lit a toggle looks, 0 to 1 */
    public float on() {
        return on.toward(lit.getAsBoolean());
    }

    @Override
    protected boolean mousePressed(float x, float y, MouseButton button) {
        if (button != MouseButton.LEFT) {
            return false;
        }
        action.run();
        return true;
    }

    @Override
    protected boolean activated() {
        action.run();
        return true;
    }

    @Override
    public boolean isFocusable() {
        return true;
    }
}
