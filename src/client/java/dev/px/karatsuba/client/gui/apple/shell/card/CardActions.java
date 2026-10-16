package dev.px.karatsuba.client.gui.apple.shell.card;

import dev.px.gui.container.Row;
import dev.px.karatsuba.client.gui.apple.shell.style.Palette;

/**
 * Standalone actions under a card, right-aligned, as Tahoe's "Screen Saver…
 * Clock Appearance…" under its wallpaper card: buttons that act on the card above
 * rather than on any one row.
 */
public final class CardActions extends Row {

    public CardActions(PushButton... buttons) {
        gap(Palette.GAP_ACTIONS);
        for (PushButton button : buttons) {
            add(button);
        }
    }
}
