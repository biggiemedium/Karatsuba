package dev.px.karatsuba.client.hud.style;

import dev.px.core.Core;
import dev.px.core.config.section.SettingsSection;
import dev.px.core.setting.SettingHolder;
import dev.px.core.setting.impl.BooleanSetting;
import dev.px.core.setting.impl.EnumSetting;
import dev.px.core.setting.impl.GroupSetting;
import dev.px.core.setting.impl.NumberSetting;
import dev.px.karatsuba.client.gui.shell.style.Animations;

/**
 * The HUD's one style: what every {@link StyledElement} looks like unless it has
 * Custom Style on, and how the HUD behaves around what the player is doing.
 *
 * <p>Edited from the Style entry at the top of the HUD section's list, beside the
 * elements it styles. Saved per profile as {@code hud-style.json}, the same as the
 * HUD layout: a profile for streaming can look different from one for a fight.
 *
 * <p>Nothing reads a value out of here once and keeps it. Elements ask every
 * frame, so a change shows on everything at once.
 */
public final class HudStyle extends SettingHolder {

    /** The config section id, and its file name. */
    public static final String ID = "hud-style";

    /** What the HUD does while a screen is open. */
    public enum InScreens { SHOW, DIM, HIDE }

    /** What the HUD does while the F3 debug screen is up. */
    public enum WithDebug { SHOW, HIDE }

    private static HudStyle instance;

    private final StyleSettings style = new StyleSettings(() -> true);

    private final GroupSetting plate = group("Plate", true).containing(style.plate())
            .describe("The panel behind carded elements, and their spacing");
    private final GroupSetting text = group("Text", true).containing(style.text());

    private final BooleanSetting animate = bool("Animate", true)
            .describe("Ease elements between sizes, fades and positions. Also needs Animations on in Interface");

    private final GroupSetting motion = group("Motion", true).containing(animate);

    // ---------------------------------------------------------------- context

    private final EnumSetting<InScreens> inScreens = enumOf("In Screens", InScreens.DIM)
            .describe("While chat, an inventory or any other game screen is open. The client's own GUI is not counted");
    private final EnumSetting<WithDebug> withDebug = enumOf("With F3", WithDebug.HIDE)
            .describe("While the debug screen is up, which covers the corners the HUD uses");
    private final NumberSetting<Integer> idleAfter = integer("Idle After", 10, 2, 120)
            .describe("Seconds without a change before an element with Fade When Idle fades");
    private final NumberSetting<Integer> idleOpacity = integer("Idle Opacity", 35, 0, 90)
            .describe("How visible an idle element stays, in percent. Zero hides it until it changes");
    private final BooleanSetting changeCues = bool("Change Cues", true)
            .describe("Briefly highlight an element when something that matters changes: ping turning bad, a module switched on");
    private final BooleanSetting avoidVanilla = bool("Avoid Vanilla HUD", true)
            .describe("Move elements out of the way of the hotbar, boss bars, effect icons and scoreboard while they show");

    private final GroupSetting context = group("Context", true)
            .containing(inScreens, withDebug, idleAfter, idleOpacity, changeCues, avoidVanilla);

    private HudStyle() {
    }

    /**
     * Creates the style and registers its config section. Before {@code Core.start()},
     * which loads it, and before any element draws.
     */
    public static void install(Core core) {
        instance = new HudStyle();
        core.getConfigService().register(new SettingsSection(ID, instance));
        // Listed now rather than on first draw, so a font that cannot be read says so at launch.
        HudFonts.options();
    }

    /** @return the global style, installed at boot */
    public static HudStyle get() {
        return instance;
    }

    @Override
    public String getName() {
        return "HUD Style";
    }

    /** @return the global style, now */
    public Style resolve() {
        return style.resolve();
    }

    /** @return whether the HUD should move: this switch and the client's Animations both on */
    public boolean animates() {
        return animate.isOn() && Animations.enabled();
    }

    public InScreens inScreens() {
        return inScreens.get();
    }

    public WithDebug withDebug() {
        return withDebug.get();
    }

    /** @return milliseconds without a change before an element counts as idle */
    public long idleAfterMillis() {
        return idleAfter.getInt() * 1000L;
    }

    /** @return how visible an idle element stays, 0 to 1 */
    public float idleOpacity() {
        return idleOpacity.getInt() / 100f;
    }

    public boolean changeCues() {
        return changeCues.isOn();
    }

    public boolean avoidVanilla() {
        return avoidVanilla.isOn();
    }
}
