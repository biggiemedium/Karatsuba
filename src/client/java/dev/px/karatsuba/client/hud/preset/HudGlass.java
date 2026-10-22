package dev.px.karatsuba.client.hud.preset;

import dev.px.core.hud.Placement;
import dev.px.core.layout.Bounds;
import dev.px.core.layout.Size;
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

    /** Draws every plate behind {@code placements}: their shadows first, then their glass. */
    public static void behind(List<Placement> placements) {
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
                    plates.add(plate);
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
