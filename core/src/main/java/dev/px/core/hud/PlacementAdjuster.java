package dev.px.core.hud;

import dev.px.core.layout.Bounds;

/**
 * Moves an element after the anchor formula has placed it, for this frame only.
 *
 * <p>For things the layout cannot know about: the game's own HUD, a boss bar
 * that appears mid-fight, a scoreboard a server sends. An adjuster answers where
 * the element should be drawn instead; nothing is written back to its layout, so
 * the element returns to where it was put the moment the obstacle goes.
 *
 * <p>Not consulted while the editor is open, so dragging an element never fights
 * whatever would push it.
 */
@FunctionalInterface
public interface PlacementAdjuster {

    /**
     * @param element the element being placed
     * @param placed  where the layout puts it, scaled and clamped to the screen
     * @return where to draw it this frame; {@code placed} to leave it
     */
    Bounds adjust(HudElement element, Bounds placed);
}
