package dev.px.karatsuba.client.gui.apple.shell.section.gallery;

import dev.px.gui.Widget;
import dev.px.karatsuba.client.gui.apple.shell.Section;
import dev.px.karatsuba.client.gui.apple.shell.card.Card;
import dev.px.karatsuba.client.gui.apple.shell.card.CardList;
import dev.px.karatsuba.client.gui.apple.shell.card.CardPage;
import dev.px.karatsuba.client.gui.apple.shell.card.PushButton;
import dev.px.karatsuba.client.gui.apple.shell.rail.Glyph;
import dev.px.karatsuba.client.gui.apple.shell.setting.InfoRow;

/**
 * Every kind of row, in cards, for checking the grouped-list look in both
 * appearances (PLAN.md step 8). Not in the sidebar; Ctrl and its number reaches
 * it. TEMPORARY: removed in step 17 once real pages show every row type.
 */
public final class GallerySection implements Section {

    public static final String ID = "gallery";

    private final CardPage page = new CardPage();
    private final CardList list = new CardList(true);

    public GallerySection() {
        list.build(new GallerySettings(), page);

        // A header with a trailing action, push buttons in rows, and standalone ones under the card.
        int[] shown = {3};
        var group = page.group("Buttons", () -> "Show All (" + shown[0] + ")", () -> shown[0]++);
        Card card = group.add(new Card());
        card.add(new InfoRow("Version", () -> "1.0.0", "Copy", () -> { }));
        card.add(new InfoRow("Folder", () -> ".minecraft/karatsuba", "Open…", () -> { }));
        page.actions(group, new PushButton("Screen Saver…", () -> { }), new PushButton("Clock Appearance…", () -> { }));
    }

    @Override
    public String id() {
        return ID;
    }

    @Override
    public String title() {
        return "Gallery";
    }

    @Override
    public Glyph glyph() {
        return Glyph.MODULES;
    }

    @Override
    public Widget content() {
        return page;
    }

    @Override
    public void tick() {
        list.refresh();
    }
}
