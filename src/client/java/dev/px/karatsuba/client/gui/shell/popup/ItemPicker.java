package dev.px.karatsuba.client.gui.shell.popup;

import dev.px.gui.container.Column;
import dev.px.gui.container.Row;
import dev.px.karatsuba.client.gui.shell.widget.Caption;
import dev.px.karatsuba.client.gui.shell.widget.SearchField;
import dev.px.karatsuba.client.gui.shell.widget.TextButton;
import dev.px.karatsuba.client.setting.ItemSetting;

/**
 * Picks items: a search box over the list, ticked with a click, and a footer
 * saying how many are ticked with a way to clear them.
 *
 * <p>Opens with the cursor in the search box, so you can type straight away;
 * Enter ticks the top match.
 */
public final class ItemPicker extends Column {

    public final ItemSetting setting;
    public final SearchField search;
    public final ItemList list;

    public ItemPicker(ItemSetting setting) {
        this.setting = setting;
        gap(4f);
        this.search = add(new SearchField("Search " + (setting.getScope() == ItemSetting.Scope.BLOCKS ? "blocks" : "items")));
        this.list = add(new ItemList(setting));
        search.onChange(list::query);
        search.onSubmit(text -> list.tickFirst());

        Row footer = add(new Row().gap(4f));
        footer.add(new Caption(() -> setting.size() + " selected")).grow(1f);
        footer.add(new TextButton("Selected only", list::isSelectedOnly, list::toggleSelectedOnly));
        footer.add(new TextButton("Clear", () -> false, setting::clear));
    }

    public void takeFocus() {
        search.grab();
        search.selectAll();
        list.query(search.getText());
    }
}
