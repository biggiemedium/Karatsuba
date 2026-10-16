package dev.px.karatsuba.client.gui.apple.shell.section.themes;

import dev.px.gui.Widget;
import dev.px.karatsuba.client.gui.apple.shell.Section;
import dev.px.karatsuba.client.gui.apple.shell.frame.Reveal;
import dev.px.karatsuba.client.gui.apple.shell.rail.Glyph;

/**
 * The themes, at the foot of the rail with the other things about the GUI
 * itself rather than among the things you work on.
 */
public final class ThemesSection implements Section {

    public static final String ID = "themes";

    private final ThemeView view = new ThemeView(new Reveal());

    @Override
    public String id() {
        return ID;
    }

    @Override
    public String title() {
        return "Themes";
    }

    @Override
    public Glyph glyph() {
        return Glyph.THEMES;
    }

    @Override
    public Group group() {
        return Group.BOTTOM;
    }

    @Override
    public Widget content() {
        return view;
    }

    @Override
    public void entered() {
        // The cards arrive one after another each time the section is opened.
        view.opened();
    }
}
