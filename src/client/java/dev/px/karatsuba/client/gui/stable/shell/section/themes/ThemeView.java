package dev.px.karatsuba.client.gui.stable.shell.section.themes;

import dev.px.core.Core;
import dev.px.core.render.theme.Theme;
import dev.px.karatsuba.client.gui.stable.shell.frame.FlowGrid;
import dev.px.karatsuba.client.gui.stable.shell.frame.Reveal;
import dev.px.karatsuba.client.gui.stable.shell.frame.RisingColumn;
import dev.px.karatsuba.client.gui.stable.shell.frame.SmoothScroll;
import dev.px.karatsuba.client.gui.stable.shell.style.Palette;
import dev.px.karatsuba.client.gui.stable.shell.widget.SectionTitle;
import dev.px.karatsuba.client.gui.stable.shell.frame.Spacer;

import java.util.List;

/**
 * The Themes section's content: every theme as a card showing its gradient and
 * name, in as many columns as the window has room for.
 *
 * <p>Pressing a card makes it the theme at once, and the whole GUI's accent
 * crossfades to it, so the effect is seen in place, with the cards still there to
 * compare against.
 */
public final class ThemeView extends RisingColumn {

    /** Narrowest a card gets before the grid drops a column. */
    private static final float CARD_WIDTH = 104f;

    private final SmoothScroll scroll;
    private long opened;

    public ThemeView(Reveal reveal) {
        super(reveal);
        gap(Palette.GAP_ROW);
        List<Theme> themes = Core.themes().getThemes().all();
        add(new SectionTitle("Themes", () -> themes.size() + " themes  ·  " + Core.themes().getActive().getName()));
        add(new Spacer(Palette.INSET - Palette.GAP_ROW));
        this.scroll = add(new SmoothScroll());
        scroll.grow(1f);
        FlowGrid grid = scroll.add(new FlowGrid(CARD_WIDTH, Palette.INSET));
        for (int i = 0; i < themes.size(); i++) {
            grid.add(new ThemeCard(themes.get(i), i, this));
        }
    }

    /** Starts the cards' arrival, and shows the active theme's card. Called as the picker opens. */
    public void opened() {
        opened = System.nanoTime();
        scroll.scrollTo(0f);
    }

    /** @return when the picker last opened, for the cards' stagger */
    public long openedAt() {
        return opened;
    }
}
