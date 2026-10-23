package dev.px.karatsuba.client.hud.preset.glass;

import dev.px.core.hud.Placement;
import dev.px.core.layout.Bounds;
import dev.px.core.layout.Size;
import dev.px.karatsuba.client.hud.preset.HudPlate;
import dev.px.karatsuba.client.hud.preset.HudPreset;
import dev.px.karatsuba.client.hud.preset.HudPresets;
import dev.px.karatsuba.client.hud.preset.HudTokens;
import dev.px.karatsuba.client.hud.preset.apple.ApplePreset;
import dev.px.karatsuba.client.hud.style.StyledElement;
import dev.px.karatsuba.client.render.Glass;

import java.util.ArrayList;
import java.util.List;

/**
 * Draws the plates behind HUD elements for a preset that puts them there (glass,
 * {@code hud/DESIGN.md} §4): before the elements are drawn, at each element's
 * scaled screen bounds and never inside its scale transform (§4.2), every plate
 * from one shared capture of the screen (§4.3).
 *
 * <p>Called by whatever draws the HUD (the overlay over the game, the layout
 * editors), with the placements it is about to draw, outside any clip, transform
 * or alpha push: a shader pass refuses inside one.
 */
public final class HudGlass {

    private HudGlass() {
    }

    /**
     * Draws chrome over the game in the HUD's Apple glass, whatever preset draws the
     * elements: the editor's toolbar ({@code hud/DESIGN.md} §7.1c). A capsule at
     * {@code plate}'s screen bounds, formed {@code plate.presence()} of the way. Outside
     * any clip, transform or alpha push, as all glass is.
     */
    public static void chrome(HudPlate plate) {
        if (plate.presence() > 0f && plate.width() > 0f && plate.height() > 0f) {
            ((ApplePreset) HudPresets.APPLE).chrome(plate);
        }
    }

    /** @return the tokens for what sits on {@link #chrome}: its text, separators and accent */
    public static HudTokens chromeTokens(String owner) {
        return ((ApplePreset) HudPresets.APPLE).chromeTokens(owner);
    }

    /** Draws every plate behind {@code placements}: their shadows first, then their glass. */
    public static void behind(List<Placement> placements) {
        behind(placements, 1f);
    }

    /**
     * As {@link #behind(List)}, every plate formed only {@code presence} (0 to 1) of the
     * way: for a host fading in (the layout editor as the GUI steps aside), whose fade
     * cannot be an alpha push around the glass, since a pass refuses inside one. Glass
     * forms and melts rather than fading (Apple rules §6.5).
     */
    public static void behind(List<Placement> placements, float presence) {
        if (presence <= 0f) {
            return;
        }
        List<HudPlate> plates = new ArrayList<>();
        List<HudPreset> presets = new ArrayList<>();
        List<Float> scales = new ArrayList<>();
        for (Placement placement : placements) {
            if (!(placement.getElement() instanceof StyledElement element) || placement.getLayout().isHidden()) {
                continue;
            }
            HudPreset preset = element.drawingPreset();
            Bounds bounds = placement.getBounds();
            Size natural = placement.getNatural();
            if (!preset.drawsBehind() || natural.getWidth() <= 0f || natural.getHeight() <= 0f) {
                continue;
            }
            float scale = placement.getLayout().getScale();
            for (HudPlate plate : element.plates(bounds.getX(), bounds.getY(), scale,
                    natural.getWidth(), natural.getHeight())) {
                if (plate.presence() > 0f && plate.width() > 0f && plate.height() > 0f) {
                    plates.add(presence >= 1f ? plate : new HudPlate(plate.owner(), plate.x(), plate.y(),
                            plate.width(), plate.height(), plate.capsule(), plate.presence() * presence, plate.cue()));
                    presets.add(preset);
                    scales.add(scale);
                }
            }
        }
        if (plates.isEmpty()) {
            return;
        }
        // Shadows before any glass, so the one capture the glass shares already holds them all.
        for (int i = 0; i < plates.size(); i++) {
            presets.get(i).shadow(plates.get(i), scales.get(i));
        }
        Glass.Layer layer = Glass.layer();
        for (int i = 0; i < plates.size(); i++) {
            if (!presets.get(i).glass(layer, plates.get(i), scales.get(i))) {
                presets.get(i).standIn(plates.get(i), scales.get(i));
            }
        }
        // While the capture still holds the screen behind them: before anything later captures again.
        if (layer.captured()) {
            Backdrop.measure(plates);
        }
    }
}
