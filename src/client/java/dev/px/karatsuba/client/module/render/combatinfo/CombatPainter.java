package dev.px.karatsuba.client.module.render.combatinfo;

import dev.px.core.render.Color;

/**
 * One look for a Combat Info card. Each is handed the same {@link CombatCard} and
 * the point beside the player its left-middle edge goes, so the module can switch
 * between them without changing what it knows or how it tracks.
 */
public interface CombatPainter {

    /**
     * @param x          screen X of the card's left edge, at the middle of its height
     * @param y          screen Y of that point
     * @param scale      1 draws at HUD text size
     * @param alpha      0 to 1, the whole card
     * @param accent     the outline
     * @param background the panel
     */
    void paint(float x, float y, float scale, float alpha, CombatCard card, Color accent, Color background);
}
