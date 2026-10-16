package dev.px.karatsuba.client.gui.stable.shell.look;

import dev.px.core.Core;
import dev.px.core.layout.Align;
import dev.px.core.layout.Content;
import dev.px.core.render.Color;
import dev.px.core.render.Render;
import dev.px.gui.render.WidgetRenderer;
import dev.px.gui.render.WidgetRendererRegistry;
import dev.px.karatsuba.adapter.game.item.ItemCatalog;
import dev.px.karatsuba.adapter.game.item.ItemIcons;
import dev.px.karatsuba.client.gui.stable.shell.widget.TextButton;
import dev.px.karatsuba.client.gui.stable.shell.popup.ColorPicker;
import dev.px.karatsuba.client.gui.stable.shell.popup.ItemList;
import dev.px.karatsuba.client.gui.stable.shell.popup.ItemPicker;
import dev.px.karatsuba.client.gui.stable.shell.popup.Menu;
import dev.px.karatsuba.client.gui.stable.shell.popup.MenuItem;

import java.util.List;

import static dev.px.karatsuba.client.gui.stable.shell.look.Shapes.*;
import static dev.px.karatsuba.client.gui.stable.shell.style.Palette.*;

/** Menus, the colour picker and the item picker. */
final class PopupLook {

    private PopupLook() {
    }

    static void register(WidgetRendererRegistry look) {
        look.register(WidgetRenderer.of(Menu.class, (menu, c) -> c.backdrop(Shapes::popupSurface)
                .padding(3f).slot()));

        look.register(WidgetRenderer.of(MenuItem.class, (item, c) -> {
            boolean on = item.isSelected();
            float hover = item.hover();
            float check = item.check();
            c.align(Align.STRETCH).row(r -> {
                r.height(ROW_HEIGHT - 2f).padding(6f, 0f).gap(6f).align(Align.CENTER)
                        .backdrop((x, y, w, h) -> Render.roundRect(x, y, w, h, RADIUS_CONTROL, faded(RAISED, hover)));
                if (item.isCheckbox()) {
                    r.custom(9f, 9f, (x, y, w, h) -> checkbox(x, y, w, h, check));
                }
                r.text(body(), item.label(), TEXT_MUTED.lerp(TEXT, Math.max(hover, on ? 1f : 0f))).fill();
                if (!item.isCheckbox() && on) {
                    r.custom(8f, 6f, (x, y, w, h) -> tick(x, y, w, h, accent()));
                }
            });
        }));

        look.register(WidgetRenderer.of(ColorPicker.class, (picker, c) -> c
                .backdrop(Shapes::popupSurface)
                .padding(INSET)
                .column(column -> column.gap(6f)
                        .row(top -> top.align(Align.CENTER).gap(6f)
                                .custom(16f, 10f, (x, y, w, h) -> swatch(x, y, w, h, picker.setting.resolve()))
                                .text(small(), hex(picker.setting.get()), TEXT_MUTED))
                        .custom("square", ColorPicker.SQUARE_WIDTH, ColorPicker.SQUARE_HEIGHT,
                                (x, y, w, h) -> square(picker, x, y, w, h))
                        .custom("hue", ColorPicker.SQUARE_WIDTH, ColorPicker.BAR_HEIGHT,
                                (x, y, w, h) -> hueBar(picker, x, y, w, h))
                        .custom("alpha", ColorPicker.SQUARE_WIDTH,
                                picker.setting.isAllowAlpha() ? ColorPicker.BAR_HEIGHT : 0f,
                                (x, y, w, h) -> alphaBar(picker, x, y, w, h))
                        .row(option -> switchLabel(option, "theme", "Follow theme", picker.setting.isSyncToTheme()))
                        .row(option -> switchLabel(option, "rainbow", "Rainbow", picker.setting.isRainbow())))));

        look.register(WidgetRenderer.of(ItemPicker.class, (picker, c) -> c.backdrop(Shapes::popupSurface)
                .min(190f, 0f).padding(6f).slot()));

        look.register(WidgetRenderer.of(ItemList.class, (list, c) -> c.align(Align.STRETCH)
                .custom("rows", 0f, ItemList.ROW * ItemList.VISIBLE_ROWS, (x, y, w, h) -> itemRows(list, x, y, w, h))));

        look.register(WidgetRenderer.of(TextButton.class, (button, c) -> c.padding(INSET, 2f)
                .text(small(), button.label(), button.isActive() ? accent()
                        : button.isHovered() ? TEXT : TEXT_MUTED)));
    }

    // ---------------------------------------------------------- colour picker

    private static void square(ColorPicker picker, float x, float y, float w, float h) {
        Render.gradientH(x, y, w, h, Color.WHITE, picker.pureHue());
        Render.gradientV(x, y, w, h, Color.of(0, 0, 0, 0), Color.BLACK);
        float px = x + picker.saturation * w;
        float py = y + (1f - picker.brightness) * h;
        Render.circleOutline(px, py, 3.5f, 1.5f, Color.WHITE);
        Render.circleOutline(px, py, 5f, 1f, Color.of(0, 0, 0, 120));
    }

    private static void hueBar(ColorPicker picker, float x, float y, float w, float h) {
        for (int i = 0; i < 6; i++) {
            float from = x + w * i / 6f;
            Render.gradientH(from, y, w / 6f + 0.5f, h, Color.hsb(i / 6f, 1f, 1f), Color.hsb((i + 1) / 6f, 1f, 1f));
        }
        marker(x + picker.hue * w, y, h);
    }

    private static void alphaBar(ColorPicker picker, float x, float y, float w, float h) {
        if (h <= 0f) {
            return;
        }
        Render.pushClip(x, y, w, h);
        checker(x, y, w, h);
        Render.popClip();
        Color colour = Color.hsb(picker.hue, picker.saturation, picker.brightness);
        Render.gradientH(x, y, w, h, colour.withAlpha(0), colour.withAlpha(255));
        marker(x + picker.alpha() * w, y, h);
    }

    private static void marker(float x, float y, float h) {
        Render.roundRect(x - 2f, y - 1f, 4f, h + 2f, 1.5f, Color.WHITE);
        Render.roundRectOutline(x - 2f, y - 1f, 4f, h + 2f, 1.5f, 1f, Color.of(0, 0, 0, 120));
    }

    private static void switchLabel(Content option, String name, String label, boolean on) {
        option.name(name).gap(6f).align(Align.CENTER)
                .custom(16f, 9f, (x, y, w, h) -> toggle(x, y, w, h, on ? 1f : 0f))
                .text(small(), label, on ? TEXT : TEXT_MUTED);
    }

    // ------------------------------------------------------------ item picker

    /** The item rows in view, their checkboxes and names, then their icons on top. */
    private static void itemRows(ItemList list, float x, float y, float w, float h) {
        list.ease();
        List<ItemCatalog.Entry> entries = list.shown();
        float offset = list.offset;
        int first = Math.max(0, (int) (offset / ItemList.ROW));
        int last = Math.min(entries.size() - 1, (int) ((offset + h) / ItemList.ROW));
        float mouseX = Core.platform().getMouseX();
        float mouseY = Core.platform().getMouseY();
        boolean inside = list.isHovered() && mouseX >= x && mouseX < x + w && mouseY >= y && mouseY < y + h;
        int hovered = inside ? (int) ((mouseY - y + offset) / ItemList.ROW) : -1;
        float iconX = x + 18f;

        Render.pushClip(x, y, w, h);
        if (entries.isEmpty()) {
            Render.text(small(), "Nothing matches", x + 6f, y + 4f, TEXT_FAINT);
        }
        for (int i = first; i <= last; i++) {
            float top = y + i * ItemList.ROW - offset;
            ItemCatalog.Entry entry = entries.get(i);
            if (i == hovered) {
                Render.roundRect(x, top, w, ItemList.ROW, RADIUS_CONTROL, RAISED);
            }
            boolean on = list.setting.contains(entry.getId());
            checkbox(x + 4f, top + (ItemList.ROW - 9f) / 2f, 9f, 9f, on ? 1f : 0f);
            Render.text(body(), entry.getName(), iconX + 16f, top + (ItemList.ROW - body().getHeight()) / 2f,
                    on ? TEXT : TEXT_MUTED);
        }
        if (list.maxOffset() > 0f) {
            float track = h * h / (entries.size() * ItemList.ROW);
            float along = (h - track) * (offset / list.maxOffset());
            Render.roundRect(x + w - 2f, y + along, 2f, track, 1f, TEXT_FAINT);
        }
        Render.popClip();

        // Last, with nothing pushed: the game draws the icons over what is above.
        int from = first;
        int to = last;
        ItemIcons.drawLayer(x, y, w, h, layer -> {
            for (int i = from; i <= to; i++) {
                layer.draw(entries.get(i), iconX, y + i * ItemList.ROW - offset + 2f, 12f);
            }
        });
    }
}
