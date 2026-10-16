package dev.px.karatsuba.client.gui.apple.shell.card;

/**
 * A row that can sit in a {@link Card}. The card draws the row's highlight and the
 * hairlines around it, so once {@link #markCarded() marked} the row draws no
 * background of its own; outside a card it keeps drawing its own.
 */
public interface CardRow {

    void markCarded();

    boolean isCarded();
}
