package dev.px.karatsuba.client.gui.shell.widget;

import dev.px.gui.widget.TextField;

/** A text field for filtering a list: a placeholder saying what it searches, and focus on demand. */
public final class SearchField extends TextField {

    public SearchField(String placeholder) {
        super("");
        placeholder(placeholder);
        maxLength(48);
    }

    /** Puts the caret here, so typing goes straight into the search. */
    public void grab() {
        focus();
    }
}
