package dev.px.karatsuba.client.gui.shell;

import dev.px.karatsuba.client.gui.shell.frame.WindowFrame;
import dev.px.karatsuba.client.gui.shell.section.hud.HudSection;
import dev.px.karatsuba.client.gui.shell.section.layout.LayoutSection;
import dev.px.karatsuba.client.gui.shell.section.modules.ModulesSection;
import dev.px.karatsuba.client.gui.shell.section.themes.ThemesSection;

import java.util.List;

/**
 * The catalogue of sections, in rail order: the things you work on first, then
 * the things about the GUI itself, which {@link Section#group()} puts at the foot.
 *
 * <p>Same shape as {@code Modules} and {@code Overlays}: adding a section is
 * writing it and adding a line here. The rail, the Ctrl+number keys and the
 * switching all follow from this list. Only sections that exist go in it; a
 * section that is not built yet gets no placeholder, so the rail never offers a
 * dead end.
 *
 * <p>Navigation, when it comes, goes after Layout: it is a thing you work on,
 * and it then takes Ctrl+4 while Themes moves to Ctrl+5.
 */
public final class Sections {

    private Sections() {
    }

    static List<Section> create(Shell shell, WindowFrame frame) {
        return List.of(
                new ModulesSection(shell, frame),
                new HudSection(shell),
                new LayoutSection(shell),
                new ThemesSection()
        );
    }
}
