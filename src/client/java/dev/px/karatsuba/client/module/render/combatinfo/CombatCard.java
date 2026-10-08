package dev.px.karatsuba.client.module.render.combatinfo;

import dev.px.core.render.Color;

import java.util.List;

/**
 * Everything one Combat Info card says about one player, worked out by the
 * module and drawn by {@link CombatCardPainter}. Plain values; a {@code null}
 * section is switched off and not drawn.
 *
 * @param name     the player's name, and the colour it is drawn in
 * @param ping     milliseconds, or -1 if unknown
 * @param health   health, not counting absorption
 * @param maxHealth health when full
 * @param absorption golden hearts on top
 * @param pops     totems popped since they last died
 * @param popFlash 1 right after a pop, fading to 0, for the pop count's flash
 * @param hands    what the hands hold that matters in a crystal fight; null when off
 * @param armour   the four pieces, head to feet; null when off
 * @param effects  status effects worth knowing in a fight; null when off
 * @param position hole, surround or burrow; null when off
 * @param distance blocks from you
 */
public record CombatCard(String name, Color nameColor, int ping,
                         float health, float maxHealth, float absorption,
                         int pops, float popFlash,
                         List<Chip> hands, List<Piece> armour, List<Chip> effects,
                         Chip position, double distance) {

    /** A short coloured label: "Totem", "Crystal 64", "Str II 1:20", "Hole 4/4". */
    public record Chip(String text, Color color) {
    }

    /**
     * One armour slot.
     *
     * @param durability 0 to 1 left; ignored when {@code missing}
     * @param missing    nothing worn there, which in a crystal fight is the headline
     */
    public record Piece(float durability, boolean missing) {
    }
}
