package dev.px.karatsuba.client.gui.stable.shell.setting;

import dev.px.core.setting.impl.ColorSetting;
import dev.px.gui.Popup;
import dev.px.karatsuba.client.gui.stable.shell.popup.ColorPicker;
import dev.px.karatsuba.client.gui.stable.shell.popup.Unroll;

/** A colour: a swatch of what it resolves to now, opening a {@link ColorPicker} under the row. */
public final class ColorRow extends SettingRow<ColorSetting> {

    public ColorRow(ColorSetting setting) {
        super(setting);
    }

    @Override
    protected boolean pressed(float x, float y) {
        open();
        return true;
    }

    @Override
    protected boolean activated() {
        open();
        return true;
    }

    private void open() {
        Unroll.open(getScreen(), new ColorPicker(setting), Popup.below(this).gap(2f));
    }
}
