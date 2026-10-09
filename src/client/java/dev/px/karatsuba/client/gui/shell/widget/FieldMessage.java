package dev.px.karatsuba.client.gui.shell.widget;

import dev.px.gui.Widget;

import java.util.function.Supplier;

/**
 * A line under a field saying what went wrong and how to put it right, or
 * confirming what happened. Takes no room while there is nothing to say.
 */
public final class FieldMessage extends Widget {

    /** Whether the message is a problem to fix or just news. */
    public enum Kind {
        ERROR, INFO
    }

    private final Supplier<String> text;
    private final Supplier<Kind> kind;

    public FieldMessage(Supplier<String> text, Supplier<Kind> kind) {
        this.text = text;
        this.kind = kind;
        visibleWhen(() -> !text.get().isEmpty());
    }

    public String text() {
        return text.get();
    }

    public Kind kind() {
        return kind.get();
    }
}
