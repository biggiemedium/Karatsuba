package dev.px.karatsuba.client.gui.apple.shell.setting;

import dev.px.core.setting.impl.MultiEnumSetting;
import dev.px.core.util.text.TextUtil;

import java.util.List;

/**
 * A few options, any of which can be on: a row of pills, each lit while ticked
 * ({@code gui/apple/DESIGN.md} §2.5). Four at most; more gets the menu of
 * {@link MultiChoiceRow}.
 */
public final class OptionsRow<E extends Enum<E>> extends SettingRow<MultiEnumSetting<E>> {

    /** The most options shown side by side. */
    public static final int MOST = 4;

    private final List<E> options;

    public OptionsRow(MultiEnumSetting<E> setting) {
        super(setting);
        this.options = List.copyOf(setting.getOptions());
    }

    /** @return whether {@code setting} has few enough options to lay side by side */
    public static boolean suits(MultiEnumSetting<?> setting) {
        return setting.getOptions().size() <= MOST;
    }

    public int count() {
        return options.size();
    }

    public String label(int index) {
        return TextUtil.prettify(options.get(index));
    }

    public boolean isOn(int index) {
        return setting.has(options.get(index));
    }

    @Override
    protected boolean pressed(float x, float y) {
        for (int i = 0; i < options.size(); i++) {
            if (inside("option" + i, x, y)) {
                setting.toggle(options.get(i));
                return true;
            }
        }
        return false;
    }
}
