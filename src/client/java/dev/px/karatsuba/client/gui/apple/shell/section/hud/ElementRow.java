package dev.px.karatsuba.client.gui.apple.shell.section.hud;

import dev.px.core.hud.HudElement;
import dev.px.karatsuba.client.gui.apple.shell.setting.ControlRow;
import dev.px.karatsuba.client.gui.apple.shell.style.Motion;

import java.util.function.BooleanSupplier;

/**
 * A HUD element in its card ({@code hud/DESIGN.md} §7.1b): its name, its Shown
 * switch and a ›. The switch shows or hides it; anywhere else on the row opens its
 * page, as a module's row does.
 */
public final class ElementRow extends ControlRow {

    private final HudElement element;
    private final BooleanSupplier shown;
    private final Runnable toggle;
    private final Runnable open;
    private final Motion knob;

    ElementRow(HudElement element, BooleanSupplier shown, Runnable toggle, Runnable open) {
        this.element = element;
        this.shown = shown;
        this.toggle = toggle;
        this.open = open;
        this.knob = Motion.toggle(shown.getAsBoolean());
    }

    @Override
    public String name() {
        return element.getDisplayName();
    }

    /** @return the switch's knob, 0 hidden to 1 shown */
    public float knob() {
        return knob.toward(shown.getAsBoolean());
    }

    @Override
    protected boolean pressed(float x, float y) {
        if (inside("switch", x, y)) {
            toggle.run();
        } else {
            open.run();
        }
        return true;
    }

    /** Enter on a focused row opens its page, as a press on it would. */
    @Override
    protected boolean activated() {
        open.run();
        return true;
    }

    /** A right press does nothing: showing has no default to go back to here. */
    @Override
    protected void reset() {
    }

    @Override
    public boolean isFocusable() {
        return true;
    }
}
