package dev.px.karatsuba.client.gui.apple.shell.section.appearance;

import dev.px.gui.Widget;
import dev.px.karatsuba.client.gui.apple.shell.Section;
import dev.px.karatsuba.client.gui.apple.shell.card.Card;
import dev.px.karatsuba.client.gui.apple.shell.card.CardPage;
import dev.px.karatsuba.client.gui.apple.shell.rail.Glyph;
import dev.px.karatsuba.client.gui.apple.shell.setting.SettingList;
import dev.px.karatsuba.client.gui.apple.shell.setting.SliderRow;
import dev.px.karatsuba.client.gui.apple.shell.style.Appearance;

/**
 * The Apple UI's own look settings ({@code gui/apple/DESIGN.md} §7, PLAN.md step
 * 10), reached from the sidebar and never shown in Client Settings: Light or Dark
 * and the theme, the glass, motion, and how Modules lays out. Every change shows at
 * once, since everything reads {@link Appearance} each frame.
 */
public final class AppearanceSection implements Section {

    public static final String ID = "appearance";

    private final CardPage page = new CardPage();

    public AppearanceSection() {
        Appearance appearance = Appearance.get();
        SettingList rows = new SettingList(true);

        Card look = page.card("Appearance");
        look.add(rows.row(appearance.modeSetting()));
        look.add(new ThemeCircles());

        Card glass = page.card("Glass");
        Widget amount = rows.row(appearance.glassSetting());
        if (amount instanceof SliderRow slider) {
            slider.ends("Clear", "Tinted");
        }
        glass.add(amount);
        glass.add(rows.row(appearance.reduceTransparencySetting()));

        Card motion = page.card("Motion");
        motion.add(rows.row(appearance.reduceMotionSetting()));

        Card modules = page.card("Modules");
        modules.add(rows.row(appearance.modulesLayoutSetting()));
    }

    @Override
    public String id() {
        return ID;
    }

    @Override
    public String title() {
        return "Appearance";
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
        return page;
    }
}
