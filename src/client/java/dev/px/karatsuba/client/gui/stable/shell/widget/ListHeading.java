package dev.px.karatsuba.client.gui.stable.shell.widget;

import dev.px.gui.Widget;

import java.util.function.Supplier;

/** A small heading inside a list, with a live count: "Online — 3". */
public final class ListHeading extends Widget {

    private final String title;
    private final Supplier<String> count;

    public ListHeading(String title, Supplier<String> count) {
        this.title = title;
        this.count = count == null ? () -> "" : count;
    }

    public String title() {
        return title;
    }

    public String count() {
        return count.get();
    }
}
