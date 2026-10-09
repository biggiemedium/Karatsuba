package dev.px.karatsuba.client.gui.shell.setting;

import dev.px.core.setting.impl.MultiEnumSetting;
import dev.px.core.util.text.TextUtil;
import dev.px.gui.Popup;
import dev.px.karatsuba.client.gui.shell.popup.Menu;
import dev.px.karatsuba.client.gui.shell.popup.MenuItem;
import dev.px.karatsuba.client.gui.shell.popup.Unroll;

/**
 * Any number of options: a chip summing up what is ticked ("2 of 5"), opening a
 * menu of checkboxes that stays open while you tick.
 */
public final class MultiChoiceRow<E extends Enum<E>> extends SettingRow<MultiEnumSetting<E>> {

    public MultiChoiceRow(MultiEnumSetting<E> setting) {
        super(setting);
    }

    /** What the chip says: None, All, the one ticked, or how many. */
    public String summary() {
        int ticked = setting.get().size();
        int all = setting.getOptions().size();
        if (ticked == 0) {
            return "None";
        }
        if (ticked == all) {
            return "All";
        }
        if (ticked == 1) {
            return TextUtil.prettify(setting.get().iterator().next());
        }
        return ticked + " of " + all;
    }

    public boolean isEmpty() {
        return setting.get().isEmpty();
    }

    @Override
    protected boolean pressed(float x, float y) {
        openMenu();
        return true;
    }

    @Override
    protected boolean activated() {
        openMenu();
        return true;
    }

    private void openMenu() {
        Menu menu = new Menu();
        for (E option : setting.getOptions()) {
            menu.add(new MenuItem(TextUtil.prettify(option), () -> setting.has(option), true,
                    () -> setting.toggle(option)));
        }
        Unroll.open(getScreen(), menu, Popup.below(this).gap(2f).matchWidth());
    }
}
