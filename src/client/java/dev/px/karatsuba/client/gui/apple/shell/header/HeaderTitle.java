package dev.px.karatsuba.client.gui.apple.shell.header;

import dev.px.gui.Widget;

import java.util.function.Supplier;

/** The page title in the header, read live. */
public final class HeaderTitle extends Widget {

    private final Supplier<String> text;

    public HeaderTitle(Supplier<String> text) {
        this.text = text;
    }

    public String text() {
        return text.get();
    }
}
