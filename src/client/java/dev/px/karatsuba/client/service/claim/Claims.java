package dev.px.karatsuba.client.service.claim;

import dev.px.core.registry.Named;
import lombok.Getter;

import java.util.ArrayList;
import java.util.List;

/**
 * A table of expiring claims on one contended thing, and the rule for picking a
 * winner.
 *
 * <p>The mechanism behind every arbitrated service in this package, lifted out so
 * the services themselves are about <em>what</em> is contended rather than about
 * bookkeeping. Modelled directly on Core's {@code RotationService}, because the
 * problem is identical and the answer it arrived at is the right one.
 *
 * <h2>Why a lease rather than acquire and release</h2>
 * A claim lasts one tick unless renewed. A module keeps the thing by continuing
 * to want it, and loses it by going quiet &mdash; which is what happens when it
 * is switched off, returns early, or throws.
 *
 * <p>That is the whole point. Acquire-and-release has one failure mode that
 * matters: a module disabled while holding something never releases it, and the
 * player is left with a locked slot or a frozen velocity and nothing to blame.
 * There is a {@link #release} for letting go early, but <b>nothing depends on it
 * being called</b>. A module cannot leak a claim.
 *
 * <h2>Ties</h2>
 * Highest priority wins. Equal priorities go to <b>whoever claimed first</b> and
 * stay there while they keep asking. Most-recent-wins would make two equal
 * modules trade the thing back and forth once a tick, which looks exactly like a
 * bug and is miserable to find; if the other one should win, give it a different
 * number.
 *
 * <p>Not thread safe, and deliberately so: claims are filed and resolved on the
 * game thread inside one tick.
 *
 * @param <T> what is being claimed &mdash; a slot number, a velocity, an action
 */
public final class Claims<T> {

    private final List<Claim<T>> claims = new ArrayList<>(4);

    /** Monotonic tick counter, used for expiry and for breaking ties by age. */
    private long tick;

    /**
     * Files or renews a claim.
     *
     * <p>One claim per owner: asking again replaces what that owner wanted rather
     * than stacking up, so a module calling this every tick is the normal case
     * and costs nothing.
     *
     * @param holdTicks how many ticks the claim survives without being renewed.
     *                  One is right for anything asking every tick; more buys
     *                  tolerance for a module that only acts on some of them
     */
    public void file(Object owner, T value, int priority, int holdTicks) {
        Claim<T> existing = of(owner);
        if (existing == null) {
            claims.add(new Claim<>(owner, value, priority, tick, tick + Math.max(1, holdTicks) - 1));
            return;
        }
        existing.value = value;
        existing.priority = priority;
        existing.expiresAtTick = tick + Math.max(1, holdTicks) - 1;
    }

    /** @return whether this owner had a claim to drop */
    public boolean release(Object owner) {
        for (int i = 0; i < claims.size(); i++) {
            if (claims.get(i).owner == owner) {
                claims.remove(i);
                return true;
            }
        }
        return false;
    }

    /** Drops everything. For a world change, a disconnect or a shutdown. */
    public void clear() {
        claims.clear();
    }

    /**
     * Advances the clock and drops what has expired.
     *
     * <p>Called once per tick by the owning service, before anything resolves.
     */
    public void beginTick() {
        tick++;
        for (int i = claims.size() - 1; i >= 0; i--) {
            if (claims.get(i).expiresAtTick < tick) {
                claims.remove(i);
            }
        }
    }

    /**
     * @return the winning claim, or null when nobody wants it
     *
     * <p>Highest priority, ties broken by who asked first. Cheap enough to call
     * more than once a tick: it is a walk of a list that is almost always empty
     * or one long.
     */
    public Claim<T> best() {
        Claim<T> best = null;
        for (int i = 0; i < claims.size(); i++) {
            Claim<T> candidate = claims.get(i);
            if (best == null
                    || candidate.priority > best.priority
                    || (candidate.priority == best.priority && candidate.acquiredTick < best.acquiredTick)) {
                best = candidate;
            }
        }
        return best;
    }

    /** @return every live claim, highest priority first. For diagnostics and for composing. */
    public List<Claim<T>> all() {
        List<Claim<T>> sorted = new ArrayList<>(claims);
        sorted.sort((left, right) -> left.priority != right.priority
                ? Integer.compare(right.priority, left.priority)
                : Long.compare(left.acquiredTick, right.acquiredTick));
        return sorted;
    }

    public boolean isHeldBy(Object owner) {
        Claim<T> winner = best();
        return winner != null && winner.owner == owner;
    }

    public boolean hasClaim(Object owner) {
        return of(owner) != null;
    }

    public boolean isEmpty() {
        return claims.isEmpty();
    }

    public int size() {
        return claims.size();
    }

    private Claim<T> of(Object owner) {
        for (int i = 0; i < claims.size(); i++) {
            if (claims.get(i).owner == owner) {
                return claims.get(i);
            }
        }
        return null;
    }

    /** One module's standing request. */
    @Getter
    public static final class Claim<T> {

        /** Whoever filed it. Compared by identity, never called into. */
        private final Object owner;

        private T value;
        private int priority;

        /** When this owner first claimed, which is what breaks a tie. */
        private final long acquiredTick;

        private long expiresAtTick;

        private Claim(Object owner, T value, int priority, long acquiredTick, long expiresAtTick) {
            this.owner = owner;
            this.value = value;
            this.priority = priority;
            this.acquiredTick = acquiredTick;
            this.expiresAtTick = expiresAtTick;
        }

        /**
         * @return a name for the owner, for notifications and debug readouts
         *
         * <p>A {@link Named} owner &mdash; every module is one &mdash; gives its
         * display name; anything else gives its class name.
         */
        public String getOwnerName() {
            return owner instanceof Named ? ((Named) owner).getName() : owner.getClass().getSimpleName();
        }

        @Override
        public String toString() {
            return "Claim(" + getOwnerName() + " -> " + value + " @" + priority + ")";
        }
    }
}
