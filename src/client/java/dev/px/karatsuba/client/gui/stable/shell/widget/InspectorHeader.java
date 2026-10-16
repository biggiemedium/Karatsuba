package dev.px.karatsuba.client.gui.stable.shell.widget;

import dev.px.core.input.MouseButton;
import dev.px.gui.Widget;
import dev.px.karatsuba.client.gui.stable.shell.style.Motion;

import java.util.function.BooleanSupplier;

/**
 * The inspector's heading: the selected thing's name, with its on/off switch
 * beside it and a word saying which it is ("Enabled", "Shown").
 */
public final class InspectorHeader extends Widget {

    private final String name;
    private final String onWord;
    private final String offWord;
    private final BooleanSupplier on;
    private final Runnable toggle;
    private final Motion knob;

    public InspectorHeader(String name, String onWord, String offWord, BooleanSupplier on, Runnable toggle) {
        this.name = name;
        this.onWord = onWord;
        this.offWord = offWord;
        this.on = on;
        this.toggle = toggle;
        this.knob = Motion.toggle(on.getAsBoolean());
    }

    public String name() {
        return name;
    }

    public String state() {
        return on.getAsBoolean() ? onWord : offWord;
    }

    public boolean isOn() {
        return on.getAsBoolean();
    }

    public float knob() {
        return knob.toward(isOn());
    }

    @Override
    protected boolean mousePressed(float x, float y, MouseButton button) {
        var bounds = part("switch");
        if (button == MouseButton.LEFT && bounds != null && bounds.contains(x, y)) {
            toggle.run();
            return true;
        }
        return false;
    }
}
