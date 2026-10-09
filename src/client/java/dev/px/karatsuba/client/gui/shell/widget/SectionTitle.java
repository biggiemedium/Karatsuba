package dev.px.karatsuba.client.gui.shell.widget;

import dev.px.gui.Widget;

import java.util.function.Supplier;

/**
 * Every section's title row: its name, and a caption beside it ("35 themes").
 * The same height and place in every section, so switching sections changes
 * what is under the title and never where the title is.
 */
public final class SectionTitle extends Widget {

    private final String title;
    private final Supplier<String> caption;

    public SectionTitle(String title, Supplier<String> caption) {
        this.title = title;
        this.caption = caption == null ? () -> "" : caption;
    }

    public String title() {
        return title;
    }

    public String caption() {
        return caption.get();
    }
}
