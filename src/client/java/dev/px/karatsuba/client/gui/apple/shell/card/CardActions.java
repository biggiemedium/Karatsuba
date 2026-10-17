package dev.px.karatsuba.client.gui.apple.shell.card;

import dev.px.gui.container.Row;
import dev.px.karatsuba.client.gui.apple.shell.frame.Spacer;
import dev.px.karatsuba.client.gui.apple.shell.style.Palette;

/**
 * Standalone actions under a card, right-aligned, as Tahoe's "Screen Saver…
 * Clock Appearance…" under its wallpaper card: buttons that act on the card above
 * rather than on any one row.
 */
public final class CardActions extends Row {

    public CardActions(PushButton... buttons) {
        gap(Palette.GAP_ACTIONS);
        // Takes the room to their left, so they sit at the right edge: the look's slot
        // would share it evenly with a fill, leaving them in the middle.
        add(new Spacer(0f)).grow(1f);
        for (PushButton button : buttons) {
            add(button);
        }
    }
}
