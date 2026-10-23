package dev.px.karatsuba.client.hud;

import dev.px.core.Core;
import dev.px.core.hud.HudElement;
import dev.px.karatsuba.client.hud.element.ArrayListElement;
import dev.px.karatsuba.client.hud.element.CompassElement;
import dev.px.karatsuba.client.hud.element.CoordinatesElement;
import dev.px.karatsuba.client.hud.element.DebugElement;
import dev.px.karatsuba.client.hud.element.NotificationElement;
import dev.px.karatsuba.client.hud.element.PingElement;
import dev.px.karatsuba.client.hud.element.PotionsElement;
import dev.px.karatsuba.client.hud.element.NowPlayingElement;
import dev.px.karatsuba.client.hud.element.ServerElement;
import dev.px.karatsuba.client.hud.element.TaskElement;
import dev.px.karatsuba.client.hud.element.TpsElement;
import dev.px.karatsuba.client.hud.element.WatermarkElement;
import dev.px.karatsuba.client.hud.group.HudGroups;
import dev.px.karatsuba.client.hud.style.HudStyle;
import dev.px.karatsuba.client.hud.style.VanillaAvoidance;

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
        // The style first: every element reads it on its first frame.
        HudStyle.install(core);
        // Moves elements off the game's own HUD while it shows.
        VanillaAvoidance.install(core);
        // Holds grouped elements edge to edge, re-stacked every frame (hud/DESIGN.md §6).
        HudGroups.install();
        core.getHudService().registerAll(all());
    }

    public static HudElement[] all() {
        return new HudElement[]{
                new WatermarkElement(),
                new CoordinatesElement(),
                new CompassElement(),
                new ArrayListElement(),
                new NotificationElement(),
                new TpsElement(),
                new PingElement(),
                new ServerElement(),
                new NowPlayingElement(),
                new PotionsElement(),
                new TaskElement(),
                new DebugElement(),
        };
    }
}
