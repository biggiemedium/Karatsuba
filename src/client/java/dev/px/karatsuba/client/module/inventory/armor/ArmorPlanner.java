package dev.px.karatsuba.client.module.inventory.armor;

import dev.px.karatsuba.adapter.game.item.Armor;
import dev.px.karatsuba.adapter.game.item.ArmorStats;
import dev.px.karatsuba.adapter.game.item.Inventories;
import dev.px.karatsuba.client.service.inventory.InventoryClick;

/**
 * Wears the best armour carried, one click at a time.
 *
 * <p>Stateless: each tick it looks at what is worn and what is carried and
 * answers with the next click, or null. After a click the game shows the result
 * at once, so the next tick's answer carries on from there. Putting a piece on
 * takes one click when the slot is empty or the piece is in the hotbar; otherwise
 * the old piece comes off first, or, with nowhere to put it, the new one is
 * brought to the hotbar first.
 *
 * <h2>Ranking</h2>
 * Armour points first, then what the enchantments stop: Protection IV is worth
 * more than the step from diamond to netherite, which is how the game's damage
 * maths has it. {@link Preference#BLAST} weights Blast Protection over Protection,
 * for crystal fights. Never put on: Curse of Binding (it would never come off) or
 * an elytra (that is the player's call).
 *
 * <h2>Blast Protection on request</h2>
 * Velocity's Blast Protection mode asks for {@link Preference#BLAST} while an
 * explosion is likely ({@link #preferBlastFor}), and whoever plans armour passes
 * its own choice through {@link #effective} so it keeps the blast pieces on
 * rather than taking them straight back off. The request expires on its own, so
 * nothing has to remember to withdraw it: once it lapses, the usual set goes
 * back on.
 */
public final class ArmorPlanner {

    /** {@code System.nanoTime()} until which Blast Protection is asked for. */
    private static long blastUntil = System.nanoTime();

    /** Ranks Blast Protection first for the next {@code millis}, or longer if already asked. */
    public static void preferBlastFor(long millis) {
        long until = System.nanoTime() + millis * 1_000_000L;
        if (until - blastUntil > 0) {
            blastUntil = until;
        }
    }

    /** @return whether Blast Protection is currently asked for */
    public static boolean blastPreferred() {
        return blastUntil - System.nanoTime() > 0;
    }

    /** @return {@link Preference#BLAST} while it is asked for, otherwise {@code chosen} */
    public static Preference effective(Preference chosen) {
        return blastPreferred() ? Preference.BLAST : chosen;
    }

    /** Which protection to rank first. */
    public enum Preference {
        /** Protection: a little off every kind of damage. */
        PROTECTION,
        /** Blast Protection: much more off explosions, for crystal and bed fights. */
        BLAST
    }

    /** Most valuable first, so a broken chestplate is replaced before boots are upgraded. */
    private static final Armor.Piece[] ORDER = {
            Armor.Piece.CHESTPLATE, Armor.Piece.LEGGINGS, Armor.Piece.HELMET, Armor.Piece.BOOTS};

    /** A new piece must beat the worn one by this much, so equal pieces are never swapped back and forth. */
    private static final double MARGIN = 0.01d;

    /**
     * @param replaceBelow durability, 0 to 1, under which a worn piece counts as
     *                     broken: replaced by anything, and not put back on while
     *                     something better than nothing is carried
     * @param keepElytra   leave a worn elytra on rather than swap a chestplate in
     * @return the next click, or null when the best armour is already on
     */
    public Plan next(Preference preference, float replaceBelow, boolean keepElytra) {
        for (Armor.Piece piece : ORDER) {
            int wornSlot = piece.inventorySlot();
            ArmorStats worn = ArmorStats.at(wornSlot);
            if (worn != null && (worn.isBinding() || (worn.isElytra() && keepElytra))) {
                continue;
            }
            boolean empty = Inventories.stackAt(wornSlot).isEmpty();
            if (!empty && worn == null) {
                // A carved pumpkin or a head: worn on purpose, not armour to upgrade.
                continue;
            }
            boolean broken = worn != null && worn.getDurability() < replaceBelow;
            double wornScore = worn == null || broken ? Double.NEGATIVE_INFINITY : score(worn, preference);

            int best = bestCarried(piece, preference, empty, replaceBelow);
            if (best == Inventories.NOT_FOUND) {
                continue;
            }
            if (!empty && score(ArmorStats.at(best), preference) <= wornScore + MARGIN) {
                continue;
            }
            return new Plan(clickFor(wornSlot, empty, best), empty);
        }
        return null;
    }

    /** What to click, and whether the slot it fills is bare (worth stopping for). */
    public static final class Plan {

        private final InventoryClick click;
        private final boolean filling;

        Plan(InventoryClick click, boolean filling) {
            this.click = click;
            this.filling = filling;
        }

        public InventoryClick click() {
            return click;
        }

        /** @return whether the slot is empty, so the player has no armour there at all */
        public boolean fillsEmptySlot() {
            return filling;
        }
    }

    /**
     * @return the best carried piece for the slot, or {@link Inventories#NOT_FOUND}
     *
     * <p>A broken piece is never chosen over a sound one. Into a bare slot a broken
     * one is still better than nothing, so it is chosen when it is all there is.
     */
    private static int bestCarried(Armor.Piece piece, Preference preference, boolean empty, float replaceBelow) {
        int best = Inventories.NOT_FOUND;
        double bestScore = Double.NEGATIVE_INFINITY;
        boolean bestSound = false;
        for (int slot = 0; slot < Inventories.ARMOR_START; slot++) {
            ArmorStats stats = ArmorStats.at(slot);
            if (stats == null || stats.getPiece() != piece || stats.isBinding() || stats.isElytra()) {
                continue;
            }
            boolean sound = stats.getDurability() >= replaceBelow;
            if (!sound && !empty) {
                continue;
            }
            double score = score(stats, preference);
            if ((sound && !bestSound) || (sound == bestSound && score > bestScore)) {
                best = slot;
                bestScore = score;
                bestSound = sound;
            }
        }
        return best;
    }

    private static InventoryClick clickFor(int wornSlot, boolean empty, int carried) {
        if (empty) {
            // Shift-clicking armour into an empty slot of its kind puts it on.
            return InventoryClick.quickMove(carried);
        }
        if (carried < Inventories.HOTBAR_SIZE) {
            // A number key over the worn slot: the new piece goes on, the old one
            // takes its place in the hotbar.
            return InventoryClick.swap(wornSlot, carried);
        }
        if (hasFreeSlot()) {
            // Off first; next tick the slot is empty and the new piece goes on.
            return InventoryClick.quickMove(wornSlot);
        }
        // Nowhere to put the old piece: bring the new one to the hotbar, and next
        // tick it swaps straight on.
        return InventoryClick.swap(carried, borrowedHotbarSlot());
    }

    private static boolean hasFreeSlot() {
        for (int slot = 0; slot < Inventories.ARMOR_START; slot++) {
            if (Inventories.stackAt(slot).isEmpty()) {
                return true;
            }
        }
        return false;
    }

    /** A hotbar slot that is not the one in hand, so what the player holds does not change under them. */
    private static int borrowedHotbarSlot() {
        int selected = Inventories.selectedSlot();
        return selected == Inventories.HOTBAR_SIZE - 1 ? Inventories.HOTBAR_SIZE - 2 : Inventories.HOTBAR_SIZE - 1;
    }

    /**
     * How good a piece is, in roughly armour points.
     *
     * <p>Each level of the preferred protection is weighed at 1.5 points and the
     * other at 0.5 to 1; toughness at a quarter; netherite's knockback resistance
     * as a small bonus; durability only breaks ties.
     */
    public static double score(ArmorStats stats, Preference preference) {
        double protection = preference == Preference.BLAST
                ? stats.getBlastProtection() * 1.5d + stats.getProtection()
                : stats.getProtection() * 1.5d + stats.getBlastProtection() * 0.5d;
        return stats.getArmor()
                + stats.getToughness() * 0.25d
                + stats.getKnockbackResistance() * 2d
                + protection
                + (stats.getFireProtection() + stats.getProjectileProtection()) * 0.25d
                + (stats.isMending() ? 0.1d : 0d)
                + stats.getDurability() * 0.01d;
    }
}
