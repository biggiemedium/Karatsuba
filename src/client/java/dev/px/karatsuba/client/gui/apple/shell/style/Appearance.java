package dev.px.karatsuba.client.gui.apple.shell.style;

import dev.px.core.Core;
import dev.px.core.config.section.SettingsSection;
import dev.px.core.setting.SettingHolder;
import dev.px.core.setting.impl.BooleanSetting;
import dev.px.core.setting.impl.EnumSetting;
import dev.px.core.setting.impl.NumberSetting;

/**
 * How the Apple UI looks: its own settings, apart from Client Settings
 * ({@code gui/apple/DESIGN.md} §7). Edited from the Appearance page in the Apple
 * UI's sidebar and saved per profile as {@code apple-appearance.json}.
 *
 * <p>Nothing reads a value out of here once and keeps it: the Palette and the
 * glass ask every frame, so a change shows at once.
 */
public final class Appearance extends SettingHolder {

    /** The config section id, and its file name. */
    public static final String ID = "apple-appearance";

    /** Light or dark: every colour token has a value for each. */
    public enum Mode { LIGHT, DARK }

    /** How Modules shows a module: as a page pushed over its list (System Settings), or beside it. */
    public enum ModulesLayout { PAGES, SPLIT }

    private static Appearance instance;

    private final EnumSetting<Mode> mode = enumOf("Appearance", Mode.DARK)
            .describe("Light or dark surfaces and text");

    private final NumberSetting<Integer> glass = integer("Glass", 35, 0, 100)
            .describe("From clear glass at 0 to tinted at 100");

    private final BooleanSetting reduceTransparency = bool("Reduce Transparency", false)
            .describe("Solid fills in place of glass and blurred materials");

    private final BooleanSetting reduceMotion = bool("Reduce Motion", false)
            .describe("Everything changes instantly, with no motion");

    private final EnumSetting<ModulesLayout> modulesLayout = enumOf("Modules Layout", ModulesLayout.PAGES)
            .describe("Pages opens a module over its list; Split shows it beside the list");

    private Appearance() {
    }

    /**
     * Creates the settings, registers their config section and points the Apple UI's
     * {@link Animations} at Reduce Motion. Before {@code Core.start()}, which loads it.
     */
    public static void install(Core core) {
        instance = new Appearance();
        core.getConfigService().register(new SettingsSection(ID, instance));
        Animations.readFrom(() -> !instance.reduceMotion.isOn());
    }

    @Override
    public String getName() {
        return "Appearance";
    }

    /** @return the settings, installed at boot */
    public static Appearance get() {
        return instance;
    }

    public Mode mode() {
        return mode.get();
    }

    public boolean isLight() {
        return mode.get() == Mode.LIGHT;
    }

    /** @return the glass from clear (0) to tinted (1) */
    public float glass() {
        return glass.get() / 100f;
    }

    public boolean reduceTransparency() {
        return reduceTransparency.isOn();
    }

    public boolean reduceMotion() {
        return reduceMotion.isOn();
    }

    public ModulesLayout modulesLayout() {
        return modulesLayout.get();
    }

    // The settings themselves, for the Appearance page to build its rows from.

    public EnumSetting<Mode> modeSetting() {
        return mode;
    }

    public NumberSetting<Integer> glassSetting() {
        return glass;
    }

    public BooleanSetting reduceTransparencySetting() {
        return reduceTransparency;
    }

    public BooleanSetting reduceMotionSetting() {
        return reduceMotion;
    }

    public EnumSetting<ModulesLayout> modulesLayoutSetting() {
        return modulesLayout;
    }
}
