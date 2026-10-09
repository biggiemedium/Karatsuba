package dev.px.karatsuba.client.gui.shell.setting;

import dev.px.core.setting.impl.BooleanSetting;
import dev.px.karatsuba.client.gui.shell.style.Motion;

/** An on/off setting: a switch, flipped by a press anywhere on the row. */
public final class ToggleRow extends SettingRow<BooleanSetting> {

    private final Motion knob;

    public ToggleRow(BooleanSetting setting) {
        super(setting);
        this.knob = Motion.toggle(setting.isOn());
    }

    /** @return the knob's position, 0 off to 1 on */
    public float knob() {
        return knob.toward(setting.isOn());
    }

    @Override
    protected boolean pressed(float x, float y) {
        setting.toggle();
        return true;
    }

    @Override
    protected boolean activated() {
        setting.toggle();
        return true;
    }
}
