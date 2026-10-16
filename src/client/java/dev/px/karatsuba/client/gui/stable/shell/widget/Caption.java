package dev.px.karatsuba.client.gui.stable.shell.widget;

import dev.px.gui.widget.Label;

import java.util.function.Supplier;

/** Small, faint text: a count, a hint, an empty list's message. */
public final class Caption extends Label {

    public Caption(String text) {
        super(text);
    }

    public Caption(Supplier<String> text) {
        super(text);
    }
}
