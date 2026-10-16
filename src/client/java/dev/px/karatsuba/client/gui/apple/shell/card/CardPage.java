package dev.px.karatsuba.client.gui.apple.shell.card;

import dev.px.gui.container.Column;
import dev.px.karatsuba.client.gui.apple.shell.frame.SmoothScroll;
import dev.px.karatsuba.client.gui.apple.shell.frame.Spacer;
import dev.px.karatsuba.client.gui.apple.shell.style.Palette;

/**
 * A page of cards ({@code gui/apple/DESIGN.md} §2.4), scrolling below the header
 * in the content column, which already insets and centres it.
 *
 * <p>Add cards with {@link #card(String)}: a header and the card under it, with
 * the gap between cards.
 */
public final class CardPage extends SmoothScroll {

    private final Column cards;

    public CardPage() {
        step(Palette.SCROLL_STEP_FINE);
        Column body = add(new Column());
        this.cards = body.add(new Column().gap(Palette.GAP_CARD));
        body.add(new Spacer(Palette.INSET_CONTENT));
    }

    /** @return a new card, under {@code header} unless it is null */
    public Card card(String header) {
        return group(header).add(new Card());
    }

    /**
     * @return a column holding {@code header} (if any) over whatever is added to it,
     *         kept together so the gap falls between cards rather than under headers
     */
    public Column group(String header) {
        Column group = cards.add(new Column().gap(Palette.GAP_CARD_HEADER));
        if (header != null) {
            group.add(new CardHeader(header));
        }
        return group;
    }

    /** As {@link #group(String)}, its header with a trailing action at the far end: "Show All (9)". */
    public Column group(String header, java.util.function.Supplier<String> action, Runnable run) {
        Column group = cards.add(new Column().gap(Palette.GAP_CARD_HEADER));
        group.add(new CardHeader(header).action(action, run));
        return group;
    }

    /**
     * Adds standalone actions under the last card in {@code group}, right-aligned.
     *
     * @return the row of buttons
     */
    public CardActions actions(Column group, PushButton... buttons) {
        return group.add(new CardActions(buttons));
    }

    /** Empties the page, for a page rebuilt when what it shows changes. */
    public void clearCards() {
        cards.clear();
    }
}
