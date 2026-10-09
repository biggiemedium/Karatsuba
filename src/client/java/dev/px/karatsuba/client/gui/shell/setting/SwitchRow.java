package dev.px.karatsuba.client.gui.shell.setting;

import dev.px.karatsuba.client.gui.shell.style.Motion;

import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

/** An on/off value that is not a {@code Setting}: a HUD element's lock, say. Looks like a {@link ToggleRow}. */
public final class SwitchRow extends ControlRow {

    private final String name;
    private final BooleanSupplier value;
    private final Consumer<Boolean> set;
    private final boolean byDefault;
    private final Motion knob;

    public SwitchRow(String name, String description, boolean byDefault, BooleanSupplier value, Consumer<Boolean> set) {
        this.name = name;
        this.value = value;
        this.set = set;
        this.byDefault = byDefault;
        this.knob = Motion.toggle(value.getAsBoolean());
        if (description != null && !description.isEmpty()) {
            tooltip(description);
        }
    }

    @Override
    public String name() {
        return name;
    }

    public float knob() {
        return knob.toward(value.getAsBoolean());
    }

    @Override
    protected boolean pressed(float x, float y) {
        set.accept(!value.getAsBoolean());
        return true;
    }

    @Override
    protected boolean activated() {
        set.accept(!value.getAsBoolean());
        return true;
    }

    @Override
    protected void reset() {
        set.accept(byDefault);
    }
}
