package dev.px.karatsuba.client.gui.apple.shell.sidebar;

import dev.px.core.input.Key;
import dev.px.core.input.Modifier;
import dev.px.gui.widget.TextField;
import dev.px.karatsuba.client.gui.apple.shell.style.Palette;

import java.util.Set;

/**
 * The search field at the top of the sidebar: a pill that filters the entries
 * under it as you type. Enter opens the first one left; Escape clears it.
 */
public final class SidebarSearch extends TextField {

    public SidebarSearch() {
        super("");
        placeholder("Search");
        maxLength(32);
        // Measured in the font it is drawn in, so the caret lands between the right letters.
        font(Palette.text());
    }

    /** Puts the caret here, so typing goes straight into the search. */
    public void grab() {
        focus();
    }

    /** Escape clears a search first, and only an empty one lets Escape go on to blur it. */
    @Override
    protected boolean keyPressed(Key key, Set<Modifier> modifiers) {
        if (key == Key.ESCAPE && !getText().isEmpty()) {
            setText("");
            return true;
        }
        return super.keyPressed(key, modifiers);
    }
}
