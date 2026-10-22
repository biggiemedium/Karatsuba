package dev.px.karatsuba.client.hud.style;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import dev.px.core.Core;
import dev.px.core.config.ConfigSection;
import dev.px.core.config.section.SettingsSection;
import dev.px.core.setting.SettingHolder;
import dev.px.core.setting.impl.BooleanSetting;
import dev.px.core.setting.impl.EnumSetting;
import dev.px.core.setting.impl.GroupSetting;
import dev.px.karatsuba.client.gui.apple.shell.style.Appearance;
import dev.px.karatsuba.client.gui.stable.shell.style.Animations;
import dev.px.karatsuba.client.hud.preset.HudPreset;
import dev.px.karatsuba.client.hud.preset.HudPresets;
import dev.px.karatsuba.client.setting.ChoiceSetting;

import java.util.ArrayList;
import java.util.List;

/**
 * Every HUD-wide setting ({@code hud/DESIGN.md} §1.4): the Preset, the
 * Accessibility settings, the Behaviour settings, and the Legacy preset's own
 * settings (its Plate, Text and ArrayList groups, which the player sees as Legacy
 * Settings and each element's Custom Style copies).
 *
 * <p>Saved per profile as {@code hud-style.json}, the same as the HUD layout: a
 * profile for streaming can look different from one for a fight. The file keeps
 * its old name so Legacy's saved values load as they are; values saved before
 * presets (Animate, the Context group) are carried over once by its
 * {@link Section}.
 *
 * <p>Nothing reads a value out of here once and keeps it. Elements ask every
 * frame, so a change shows on everything at once.
 */
public final class HudStyle extends SettingHolder {

    /** The config section id, and its file name. */
    public static final String ID = "hud-style";

    /** The Preset option that follows the active GUI (§1.5). */
    public static final String MATCH_GUI = "match_gui";

    /** What the HUD does while a screen is open. */
    public enum InScreens { SHOW, DIM, HIDE }

    /** How Legacy colours ArrayList's names: a gradient through the theme's accents, or one of the text colours. */
    public enum ListTint { THEME_GRADIENT, LABEL, VALUE }

    /** Text Size (§1.6): Default already meets §2.9's minimum, and the others only go up. */
    public enum TextSize {
        DEFAULT(1f), LARGE(1.2f), LARGER(1.4f);

        private final float scale;

        TextSize(float scale) {
            this.scale = scale;
        }

        /** @return how much larger than the client font HUD text is */
        public float scale() {
            return scale;
        }
    }

    /** Motion (§2.10): the preset's own, fades only, or none at all. */
    public enum Motion { FULL, REDUCED, OFF }

    /**
     * The Apple preset's Glass (§4.1): two opacity levels of Regular glass, in Apple's
     * own words. CLEAR here is the lighter level, never Apple's Clear variant.
     */
    public enum GlassLevel { CLEAR, TINTED }

    /** The Apple preset's Appearance (§4.14). */
    public enum HudAppearance { AUTO, LIGHT, DARK }

    private static HudStyle instance;

    // ------------------------------------------------------------ appearance

    private final ChoiceSetting preset = new ChoiceSetting("Preset", HudStyle::presetOptions)
            .labelledBy(HudStyle::presetLabel)
            .describe("How the whole HUD is drawn. Match GUI follows the interface in use");

    private final EnumSetting<GlassLevel> glass = enumOf("Glass", GlassLevel.CLEAR)
            .describe("How much the Apple preset's glass is tinted. Follows the Apple UI's Glass until set here");
    /** Whether the player set Glass here; until then it follows the Apple UI's Glass slider. */
    private final BooleanSetting glassSet = bool("Glass Set", false);
    private final EnumSetting<HudAppearance> appearance = enumOf("Appearance", HudAppearance.AUTO)
            .describe("Auto lets one-line elements turn light or dark for what is behind them,"
                    + " and the rest follow the Apple UI");

    // --------------------------------------------------------- accessibility

    private final BooleanSetting reduceTransparency = bool("Reduce Transparency", false)
            .describe("Solid plates, with nothing of the world showing through. Follows the Apple UI's until set here");
    /** Whether the player set Reduce Transparency here; until then it follows the Apple UI's. */
    private final BooleanSetting reduceTransparencySet = bool("Reduce Transparency Set", false);
    private final BooleanSetting increaseContrast = bool("Increase Contrast", false)
            .describe("A border on every plate, and text and meters made stronger");
    private final EnumSetting<TextSize> textSize = enumOf("Text Size", TextSize.DEFAULT)
            .describe("How large HUD text is. Default is already sized to read at a glance");
    private final EnumSetting<Motion> motion = enumOf("Motion", Motion.FULL)
            .describe("Full is the HUD's own motion; Reduced only fades; Off changes everything at once."
                    + " Follows the Apple UI's Reduce Motion until set here");
    /** Whether the player set Motion here; until then it follows the Apple UI's Reduce Motion. */
    private final BooleanSetting motionSet = bool("Motion Set", false);

    private final GroupSetting accessibility = group("Accessibility", true)
            .containing(reduceTransparency, reduceTransparencySet, increaseContrast, textSize, motion, motionSet);

    // -------------------------------------------------------------- behaviour

    private final EnumSetting<InScreens> inScreens = enumOf("In Screens", InScreens.DIM)
            .describe("While chat, an inventory or any other game screen is open. The client's own GUI is not counted");
    private final BooleanSetting hideWithF3 = bool("Hide with F3", true)
            .describe("Hide the HUD while the debug screen is up, which covers the corners it uses");
    private final BooleanSetting changeCues = bool("Change Cues", true)
            .describe("Briefly highlight an element when something that matters changes: ping turning bad, a module switched on");
    private final BooleanSetting avoidVanilla = bool("Avoid Vanilla HUD", true)
            .describe("Move elements out of the way of the hotbar, boss bars, effect icons and scoreboard while they show");

    private final GroupSetting behaviour = group("Behaviour", true)
            .containing(inScreens, hideWithF3, changeCues, avoidVanilla);

    // ----------------------------------------------------------------- legacy

    private final StyleSettings style = new StyleSettings(() -> true);

    private final GroupSetting plate = group("Plate", true).containing(style.plate())
            .describe("The panel behind every element, and its spacing");
    private final GroupSetting text = group("Text", true).containing(style.text());

    private final EnumSetting<ListTint> listTint = enumOf("Tint", ListTint.THEME_GRADIENT)
            .describe("A gradient through the theme's accents, or one of the style's text colours");

    private final GroupSetting arrayList = group("ArrayList", true).containing(listTint);

    private HudStyle() {
        reduceTransparencySet.visibleWhen(() -> false);
        motionSet.visibleWhen(() -> false);
        glassSet.visibleWhen(() -> false);
        // Only the Apple preset has glass and an appearance (§1.4): hidden, not greyed, otherwise.
        glass.visibleWhen(HudStyle::appleDraws);
        appearance.visibleWhen(HudStyle::appleDraws);
        glass.onChange(chosen -> glassSet.set(true));
        // Set by hand, it stops following the Apple UI. Loading and following use setSilently, which this does not hear.
        reduceTransparency.onChange(on -> reduceTransparencySet.set(true));
        motion.onChange(chosen -> motionSet.set(true));
    }

    /**
     * Creates the settings and registers their config section. Before
     * {@code Core.start()}, which loads it, and before any element draws.
     */
    public static void install(Core core) {
        instance = new HudStyle();
        core.getConfigService().register(instance.new Section());
        // Listed now rather than on first draw, so a font that cannot be read says so at launch.
        HudFonts.options();
    }

    /** @return the HUD's settings, installed at boot */
    public static HudStyle get() {
        return instance;
    }

    @Override
    public String getName() {
        return "HUD Style";
    }

    // ------------------------------------------------------------ appearance

    /** @return the Preset setting, for the HUD page's Appearance card */
    public ChoiceSetting presetSetting() {
        return preset;
    }

    /** @return the preset chosen: {@link #MATCH_GUI} or a preset's id */
    public String presetChoice() {
        return preset.resolved();
    }

    private static List<String> presetOptions() {
        List<String> options = new ArrayList<>();
        options.add(MATCH_GUI);
        for (HudPreset each : HudPresets.offered()) {
            options.add(each.id());
        }
        return options;
    }

    private static String presetLabel(String option) {
        if (MATCH_GUI.equals(option)) {
            return "Match GUI";
        }
        HudPreset found = HudPresets.byId(option);
        return found == null ? option : found.name();
    }

    private static boolean appleDraws() {
        return HudPresets.active() == HudPresets.APPLE;
    }

    /** @return the Glass setting, for the Appearance card */
    public EnumSetting<GlassLevel> glassSetting() {
        return glass;
    }

    /** @return the Appearance setting, for the Appearance card */
    public EnumSetting<HudAppearance> appearanceSetting() {
        return appearance;
    }

    /** @return whether the Apple preset's glass is Tinted: this setting, or the Apple UI's slider (from halfway) until set here */
    public boolean glassTinted() {
        if (!glassSet.isOn()) {
            Appearance apple = Appearance.get();
            glass.setSilently(apple != null && apple.glass() >= 0.5f ? GlassLevel.TINTED : GlassLevel.CLEAR);
        }
        return glass.get() == GlassLevel.TINTED;
    }

    public HudAppearance hudAppearance() {
        return appearance.get();
    }

    // --------------------------------------------------------- accessibility

    /** @return the Accessibility group, for its card */
    public GroupSetting accessibilityGroup() {
        return accessibility;
    }

    /** @return whether plates are solid: this setting, or the Apple UI's until it is set here */
    public boolean reduceTransparency() {
        if (!reduceTransparencySet.isOn()) {
            Appearance apple = Appearance.get();
            reduceTransparency.setSilently(apple != null && apple.reduceTransparency());
        }
        return reduceTransparency.isOn();
    }

    public boolean increaseContrast() {
        return increaseContrast.isOn();
    }

    public TextSize textSize() {
        return textSize.get();
    }

    /**
     * @return how the HUD moves: this setting, or the Apple UI's Reduce Motion (on
     *         meaning Reduced) until it is set here; Off whenever the client's own
     *         Animations are off
     */
    public Motion motion() {
        if (!motionSet.isOn()) {
            Appearance apple = Appearance.get();
            motion.setSilently(apple != null && apple.reduceMotion() ? Motion.REDUCED : Motion.FULL);
        }
        return Animations.enabled() ? motion.get() : Motion.OFF;
    }

    // -------------------------------------------------------------- behaviour

    /** @return the Behaviour group, for its card */
    public GroupSetting behaviourGroup() {
        return behaviour;
    }

    public InScreens inScreens() {
        return inScreens.get();
    }

    public boolean hideWithF3() {
        return hideWithF3.isOn();
    }

    public boolean changeCues() {
        return changeCues.isOn();
    }

    public boolean avoidVanilla() {
        return avoidVanilla.isOn();
    }

    // ----------------------------------------------------------------- legacy

    /** @return the Legacy preset's groups (Plate, Text, ArrayList), for Legacy Settings */
    public List<GroupSetting> legacyGroups() {
        return List.of(plate, text, arrayList);
    }

    /** Puts every Legacy setting back to its default: the Legacy Settings page's Reset. */
    public void resetLegacy() {
        for (GroupSetting group : legacyGroups()) {
            group.getChildren().forEach(child -> child.reset());
        }
    }

    /** @return Legacy's global style, now: what every element without Custom Style uses */
    public Style resolve() {
        return style.resolve();
    }

    /** @return how the Legacy preset colours ArrayList's names */
    public ListTint listTint() {
        return listTint.get();
    }

    /**
     * Takes a Tint saved on the ArrayList element, where it lived before 2026-10-17
     * ({@code hud/PLAN.md} step 2). Unknown names are ignored.
     */
    public void takeMovedListTint(String saved) {
        for (ListTint each : ListTint.values()) {
            if (each.name().equals(saved)) {
                listTint.set(each);
            }
        }
    }

    // ------------------------------------------------------------ persistence

    /**
     * The settings' config section: Core's own for a setting holder, plus one
     * carry-over. A file saved before presets has an Animate switch and a Context
     * group and no Accessibility or Behaviour groups; their values move to Motion and
     * Behaviour, so nothing a player set is lost ({@code hud/PLAN.md} step 3).
     */
    private final class Section implements ConfigSection {

        private final SettingsSection settings = new SettingsSection(ID, HudStyle.this);

        @Override
        public String getId() {
            return ID;
        }

        @Override
        public JsonObject save() {
            return settings.save();
        }

        @Override
        public void load(JsonObject json) {
            settings.load(json);
            if (!json.has(behaviour.getName())) {
                JsonObject context = children(json, "Context");
                load(context, "In Screens", inScreens);
                load(context, "Change Cues", changeCues);
                load(context, "Avoid Vanilla HUD", avoidVanilla);
                JsonElement withF3 = context.get("With F3");
                if (withF3 != null && withF3.isJsonPrimitive()) {
                    hideWithF3.setSilently(!"SHOW".equals(withF3.getAsString()));
                }
            }
            if (!json.has(accessibility.getName())) {
                JsonElement animate = children(json, "Motion").get("Animate");
                if (animate != null && animate.isJsonPrimitive() && !animate.getAsBoolean()) {
                    // Animate off was a choice: Motion Off, and no longer following the Apple UI.
                    motion.setSilently(Motion.OFF);
                    motionSet.setSilently(true);
                }
            }
        }

        /** @return the settings an old group saved, or nothing */
        private JsonObject children(JsonObject json, String group) {
            JsonElement node = json.get(group);
            if (node != null && node.isJsonObject()) {
                JsonElement inner = node.getAsJsonObject().get("settings");
                if (inner != null && inner.isJsonObject()) {
                    return inner.getAsJsonObject();
                }
            }
            return new JsonObject();
        }

        private void load(JsonObject from, String name, dev.px.core.setting.Setting<?> into) {
            if (from.has(name)) {
                into.fromJson(from.get(name));
            }
        }
    }
}
