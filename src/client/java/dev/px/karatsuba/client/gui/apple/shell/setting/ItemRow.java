package dev.px.karatsuba.client.gui.apple.shell.setting;

import dev.px.gui.Popup;
import dev.px.karatsuba.client.gui.apple.shell.popup.ItemPicker;
import dev.px.karatsuba.client.gui.apple.shell.popup.Unroll;
import dev.px.karatsuba.client.setting.ItemSetting;

/** A set of items or blocks: a chip saying how many, opening an {@link ItemPicker}. */
public final class ItemRow extends SettingRow<ItemSetting> {

    public ItemRow(ItemSetting setting) {
        super(setting);
    }

    public String summary() {
        int count = setting.size();
        if (count == 0) {
            return "None";
        }
        String noun = setting.getScope() == ItemSetting.Scope.BLOCKS ? "block" : "item";
        return count + " " + noun + (count == 1 ? "" : "s");
    }

    public boolean isEmpty() {
        return setting.isEmpty();
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
        ItemPicker picker = new ItemPicker(setting);
        Unroll.open(getScreen(), picker, Popup.below(this).gap(2f).matchWidth());
        picker.takeFocus();
    }
}
