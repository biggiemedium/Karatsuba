package dev.px.karatsuba.client.gui.apple.shell.widget;

import dev.px.gui.widget.TextField;
import dev.px.karatsuba.client.gui.apple.shell.style.Palette;

/** A text field for filtering a list: a placeholder saying what it searches, and focus on demand. */
public final class SearchField extends TextField {

    public SearchField(String placeholder) {
        super("");
        placeholder(placeholder);
        maxLength(48);
        // Measured in the font it is drawn in, so the caret lands between the right letters.
        font(Palette.body());
    }

    /** Puts the caret here, so typing goes straight into the search. */
    public void grab() {
        focus();
    }
}
