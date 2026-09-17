package dev.px.karatsuba.client.hud;

import dev.px.core.Core;
import dev.px.core.hud.HudElement;

/**
 * The HUD catalogue.
 *
 * <p>Registration order is not draw order: Core resolves z-order from each
 * element's saved layout, and the HUD editor changes it with PageUp and PageDown.
 * What order here does decide is the order they appear in the editor's list.
 */
public final class HudElements {

    private HudElements() {
    }

    public static void registerAll(Core core) {
        core.getHudService().registerAll(all());
    }

    public static HudElement[] all() {
        return new HudElement[]{
                new WatermarkElement(),
                new CoordinatesElement(),
                new ArrayListElement(),
                new NotificationElement(),
        };
    }
}
