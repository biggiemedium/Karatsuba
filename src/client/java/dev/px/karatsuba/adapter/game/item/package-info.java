/**
 * What the player is holding and wearing.
 *
 * <p>{@code Inventories} covers the hotbar and {@code Armor} covers the four
 * worn slots. Both name items by registry id and hand back plain numbers, so a
 * module never holds an {@code ItemStack} &mdash; an id is version-proof, it is
 * what a setting would store anyway, and it is what the player sees in F3.
 *
 * <p>Split from {@code game.player} because "what am I carrying" is a different
 * question from "where am I and what am I doing", and because the answer to it
 * is the same shape whether it is asked about the hotbar or about armour.
 *
 * <p><b>Game thread only.</b>
 */
package dev.px.karatsuba.adapter.game.item;
