package dev.px.karatsuba.client.hud.preset;

import dev.px.karatsuba.client.hud.preset.apple.ApplePreset;
import dev.px.karatsuba.client.hud.preset.legacy.LegacyPreset;
import dev.px.karatsuba.client.hud.style.HudStyle;
import dev.px.karatsuba.client.settings.ClientSettings;
import dev.px.karatsuba.client.settings.gui.GuiSettings;

import java.util.List;

/**
 * The presets the HUD can be drawn in, and which one is drawing it now
 * ({@code hud/DESIGN.md} §1.3, §1.5). The Preset setting is Match GUI by default:
 * the Apple UI means the Apple preset, the Classic UI means Legacy. Choosing one by
 * hand fixes it until Match GUI is chosen again.
 */
public final class HudPresets {

    public static final HudPreset LEGACY = new LegacyPreset();
    public static final HudPreset APPLE = new ApplePreset();

    /** Every preset, in the order they are offered. */
    private static final List<HudPreset> ALL = List.of(APPLE, LEGACY);

    private HudPresets() {
    }

    /** @return every preset, in the order they are offered */
    public static List<HudPreset> all() {
        return ALL;
    }

    /** @return the presets the player can pick by hand */
    public static List<HudPreset> offered() {
        return ALL;
    }

    /** @return the preset with {@code id}, or null */
    public static HudPreset byId(String id) {
        for (HudPreset each : ALL) {
            if (each.id().equals(id)) {
                return each;
            }
        }
        return null;
    }

    /** @return the preset drawing the HUD now: the one chosen, or the active GUI's under Match GUI */
    public static HudPreset active() {
        HudStyle style = HudStyle.get();
        String choice = style == null ? HudStyle.MATCH_GUI : style.presetChoice();
        if (!HudStyle.MATCH_GUI.equals(choice)) {
            HudPreset chosen = byId(choice);
            if (chosen != null) {
                return chosen;
            }
        }
        return forGui(activeGui());
    }

    /** @return the preset that matches a GUI: Apple's for the Apple UI, Legacy for the Classic UI */
    private static HudPreset forGui(GuiSettings.Style gui) {
        return gui == GuiSettings.Style.APPLE ? APPLE : LEGACY;
    }

    private static GuiSettings.Style activeGui() {
        try {
            return ClientSettings.get(GuiSettings.class).style();
        } catch (IllegalStateException notRegistered) {
            return GuiSettings.Style.CLASSIC;
        }
    }
}
