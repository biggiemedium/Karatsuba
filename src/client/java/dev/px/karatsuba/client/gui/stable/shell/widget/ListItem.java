package dev.px.karatsuba.client.gui.stable.shell.widget;

import dev.px.core.input.MouseButton;
import dev.px.gui.Widget;
import dev.px.karatsuba.client.gui.stable.shell.style.Motion;

import java.util.function.BooleanSupplier;
import java.util.function.Supplier;

/**
 * A thing in a list that can be switched on and off: a module, a HUD element.
 * Its name, an optional caption, and a switch at the end of the row.
 *
 * <p>Pressing the row selects it, to show it in the inspector; pressing the
 * switch, or right-clicking anywhere on the row, switches it. One that is on has
 * a bar in the accent at its edge and brighter text, so the state reads by more
 * than colour alone.
 */
public final class ListItem extends Widget {

    private final Supplier<String> name;
    private final Supplier<String> caption;
    private final BooleanSupplier on;
    private final Runnable toggle;
    private final BooleanSupplier selected;
    private final Runnable select;

    private final Motion hover = Motion.hover();
    private final Motion knob;

    public ListItem(Supplier<String> name, Supplier<String> caption, BooleanSupplier on, Runnable toggle,
                    BooleanSupplier selected, Runnable select) {
        this.name = name;
        this.caption = caption == null ? () -> "" : caption;
        this.on = on;
        this.toggle = toggle;
        this.selected = selected;
        this.select = select;
        this.knob = Motion.toggle(on.getAsBoolean());
    }

    public String name() {
        return name.get();
    }

    /** @return the small text before the switch, or empty */
    public String caption() {
        return caption.get();
    }

    public boolean isOn() {
        return on.getAsBoolean();
    }

    public boolean isSelected() {
        return selected.getAsBoolean();
    }

    public float hover() {
        return hover.toward(isHovered() && !isSelected());
    }

    /** @return the switch's position, 0 off to 1 on */
    public float knob() {
        return knob.toward(isOn());
    }

    @Override
    protected boolean mousePressed(float x, float y, MouseButton button) {
        if (button == MouseButton.RIGHT) {
            toggle.run();
            return true;
        }
        if (button != MouseButton.LEFT) {
            return false;
        }
        var bounds = part("switch");
        if (bounds != null && bounds.contains(x, y)) {
            toggle.run();
        } else {
            select.run();
        }
        return true;
    }

    @Override
    protected boolean activated() {
        select.run();
        return true;
    }

    @Override
    public boolean isFocusable() {
        return true;
    }
}
