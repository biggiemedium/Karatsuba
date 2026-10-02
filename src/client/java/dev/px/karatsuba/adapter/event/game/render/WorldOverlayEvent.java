package dev.px.karatsuba.adapter.event.game.render;

import dev.px.core.event.Event;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * Time to draw 2D things that belong to the world: name tags, 2D boxes, anything
 * projected from a world position onto the screen.
 *
 * <p>Core's {@code Render2DEvent} is drawn over everything, open screens
 * included, which is right for the HUD and the client's own menus. It is wrong
 * for a name tag, which is part of the scene: it would float over the inventory,
 * the chat box and the pause menu. This event is posted from the game's HUD pass,
 * which runs before any screen is drawn, so whatever a screen covers, it covers
 * this too &mdash; the same as the game's own HUD.
 *
 * <p>Drawn with the same {@code Render} 2D facade, in GUI pixels, with the
 * camera from this frame's world pass already captured for
 * {@code Render.worldToScreen}. Not posted while the HUD is hidden (F1).
 */
@Getter
@RequiredArgsConstructor
public final class WorldOverlayEvent extends Event {

    private final float partialTicks;

    private final float screenWidth;
    private final float screenHeight;
}
