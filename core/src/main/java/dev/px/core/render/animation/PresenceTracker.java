package dev.px.core.render.animation;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * A set of things that animate in when they appear and out when they go.
 *
 * <p>The problem it solves: a thing that is drawn only while some condition
 * holds &mdash; a player in range, a hole near you &mdash; would vanish mid-fade the
 * moment the condition stopped. So each key gets an {@link Entry} that outlives
 * the condition, holding its own {@link Animation} and the last value it was
 * given. An entry no longer wanted animates out from where it was, and is
 * dropped once it has gone.
 *
 * <p>Updated in passes, as often as the caller learns something new (each frame
 * for a name tag, each tick for a world scan), and read every frame:
 *
 * <pre>{@code
 * private final PresenceTracker<Vec3i, Box> holes = PresenceTracker.popIn();
 *
 * holes.begin();
 * for (Vec3i cell : scan()) holes.report(cell, true, boxOf(cell));
 * holes.end();                       // cells not reported start fading out
 *
 * for (PresenceTracker.Entry<Box> hole : holes.entries()) {
 *     draw(hole.value(), hole.presence());
 * }
 * }</pre>
 *
 * <p>Keys need {@code equals} and {@code hashCode}. Not thread-safe: update and
 * read from the same thread.
 *
 * @param <K> what identifies a thing across passes
 * @param <V> what is drawn for it
 */
public final class PresenceTracker<K, V> {

    /** The client's standard pop: quick, with a slight overshoot so it lands rather than appears. */
    public static final long POP_IN_MILLIS = 260L;

    /** And its standard exit: shorter, with no overshoot, so it gets out of the way. */
    public static final long FADE_OUT_MILLIS = 180L;

    private final long inMillis;
    private final Easing inEasing;
    private final long outMillis;
    private final Easing outEasing;

    private final Map<K, Entry<V>> entries = new HashMap<>();

    private boolean animated = true;

    /**
     * @param inMillis  how long appearing takes
     * @param inEasing  the curve for appearing; an overshooting one gives a bounce
     * @param outMillis how long disappearing takes
     * @param outEasing the curve for disappearing
     */
    public PresenceTracker(long inMillis, Easing inEasing, long outMillis, Easing outEasing) {
        this.inMillis = inMillis;
        this.inEasing = inEasing;
        this.outMillis = outMillis;
        this.outEasing = outEasing;
    }

    /** The client's standard pop in and fade out, so everything that uses it moves alike. */
    public static <K, V> PresenceTracker<K, V> popIn() {
        return new PresenceTracker<>(POP_IN_MILLIS, Easing.BACK_OUT, FADE_OUT_MILLIS, Easing.QUAD_OUT);
    }

    /** One thing, alive from the moment it starts appearing until it has fully gone. */
    public static final class Entry<V> {

        private final Animation presence;
        private boolean wanted;
        private boolean seen;
        private V value;

        private Entry(long millis, Easing easing) {
            this.presence = Animation.of(0f, millis, easing);
        }

        /**
         * @return how present it is: 0 gone, 1 fully shown. May overshoot past 1
         *         briefly while appearing with an overshooting curve, which callers
         *         usually turn into a little growth past full size.
         */
        public float presence() {
            return presence.get();
        }

        /** @return {@link #presence()} clamped to 0..1, for use as an opacity */
        public float alpha() {
            float value = presence.get();
            return value < 0f ? 0f : value > 1f ? 1f : value;
        }

        /** @return the value last reported; kept as it was once it stops being reported */
        public V value() {
            return value;
        }

        /** @return whether it is appearing or shown, as opposed to on its way out */
        public boolean isWanted() {
            return wanted;
        }
    }

    /** Turns the animations off or on. Off, things appear and disappear instantly. */
    public void setAnimated(boolean on) {
        this.animated = on;
    }

    /** Starts a pass. Call before any {@link #report}. */
    public void begin() {
        for (Entry<V> entry : entries.values()) {
            entry.seen = false;
        }
    }

    /**
     * Reports a thing this pass, with what to draw for it.
     *
     * @param wanted whether it should be shown. A thing reported as unwanted keeps
     *               its entry while it animates out, with the new value; one that
     *               has no entry yet is not given one.
     */
    public void report(K key, boolean wanted, V value) {
        Entry<V> entry = entries.get(key);
        if (entry == null) {
            if (!wanted) {
                return;
            }
            entry = new Entry<>(inMillis, inEasing);
            entries.put(key, entry);
        }
        entry.seen = true;
        entry.value = value;
        want(entry, wanted);
    }

    /**
     * Ends a pass. Anything not reported is gone and starts animating out where it
     * last was; anything that has finished animating out is dropped.
     */
    public void end() {
        Iterator<Entry<V>> each = entries.values().iterator();
        while (each.hasNext()) {
            Entry<V> entry = each.next();
            if (!entry.seen) {
                want(entry, false);
            }
            if (!entry.wanted && entry.presence.isIdleAtZero()) {
                each.remove();
            }
        }
    }

    /** @return whether this key has an entry: appearing, shown or on its way out */
    public boolean has(K key) {
        return entries.containsKey(key);
    }

    /** @return every live entry, in no particular order. A snapshot: safe to sort. */
    public List<Entry<V>> entries() {
        return new ArrayList<>(entries.values());
    }

    /** @return how many entries are live, including those on their way out */
    public int size() {
        return entries.size();
    }

    /** Forgets everything at once, with no animation. */
    public void clear() {
        entries.clear();
    }

    private void want(Entry<V> entry, boolean shown) {
        if (shown == entry.wanted) {
            return;
        }
        entry.wanted = shown;
        float target = shown ? 1f : 0f;
        if (!animated) {
            entry.presence.snapTo(target);
            return;
        }
        // Curve and length are set on the turn, not every pass: changing them
        // mid-flight would make the value jump.
        entry.presence.easing(shown ? inEasing : outEasing)
                .duration(shown ? inMillis : outMillis)
                .target(target);
    }
}
