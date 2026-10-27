package dev.px.karatsuba.client.gui.apple.shell;

import dev.px.karatsuba.client.gui.apple.shell.frame.WindowFrame;
import dev.px.karatsuba.client.gui.apple.shell.section.accounts.AccountsSection;
import dev.px.karatsuba.client.gui.apple.shell.section.appearance.AppearanceSection;
import dev.px.karatsuba.client.gui.apple.shell.section.friends.FriendsSection;
import dev.px.karatsuba.client.gui.apple.shell.section.gallery.GallerySection;
import dev.px.karatsuba.client.gui.apple.shell.section.home.HomeSection;
import dev.px.karatsuba.client.gui.apple.shell.section.hud.HudSection;
import dev.px.karatsuba.client.gui.apple.shell.section.layout.LayoutSection;
import dev.px.karatsuba.client.gui.apple.shell.section.modules.ModulesSection;
import dev.px.karatsuba.client.gui.apple.shell.section.profiles.ProfilesSection;
import dev.px.karatsuba.client.gui.apple.shell.section.settings.SettingsSection;
import dev.px.karatsuba.client.gui.apple.shell.section.themes.ThemesSection;

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
 * <p>Navigation, when it comes, goes after Friends: it is a thing you work on,
 * and it then takes Ctrl+6, with Themes and Settings moving down one.
 */
public final class Sections {

    private Sections() {
    }

    static List<Section> create(Shell shell, WindowFrame frame) {
        return List.of(
                new HomeSection(shell),
                new ModulesSection(shell, frame),
                new HudSection(shell),
                new LayoutSection(shell),
                new FriendsSection(shell),
                new ThemesSection(),
                new AppearanceSection(),
                new SettingsSection(shell, frame),
                // TEMPORARY: every row type in cards, for PLAN.md step 8; removed in step 17.
                new GallerySection(),
                // After the temporary Gallery so every Ctrl+number keeps its section;
                // Accounts and Profiles are reached from the top of the sidebar.
                new AccountsSection(shell),
                new ProfilesSection(shell)
        );
    }
}
