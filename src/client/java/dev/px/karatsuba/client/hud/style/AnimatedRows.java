package dev.px.karatsuba.client.hud.style;

import dev.px.core.render.animation.Animation;
import dev.px.core.render.animation.Easing;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

/**
 * Rows of a list that come and go with motion ({@code hud/DESIGN.md} §4.16b): what an
 * element like ArrayList or Potions keeps from frame to frame so a row grows in when
 * it appears and shrinks away when it goes, rather than popping.
 *
 * <p>The element hands over what should show this frame, every frame; this answers
 * with every row to draw, the ones still leaving included, in order, each with how
 * far it has come in. The element draws them; this owns only the motion.
 *
 * <pre>{@code
 * for (AnimatedRows.Row<Effect> row : rows.update(effects, byDuration)) {
 *     // height row.grown() of the way, faded to row.shown(), with row.gapAbove() of a gap over it
 * }
 * }</pre>
 *
 * <p>Coming in takes {@link #ENTER_MILLIS} and going {@link #LEAVE_MILLIS}, easing
 * out. What is there when the list is first drawn is simply there. At Motion: Reduced
 * a row only fades, keeping its room until it has gone; at Off it appears and goes at
 * once.
 *
 * @param <T> what a row shows; rows are told apart by a key, so a value that changes
 *            (a countdown) keeps its row
 */
public final class AnimatedRows<T> {

    /** A row coming in, and going: going is quicker than coming (§2.10). */
    public static final long ENTER_MILLIS = 260L;
    public static final long LEAVE_MILLIS = 200L;

    /**
     * One row to draw this frame.
     *
     * @param value    what it shows (the last value, while it leaves)
     * @param shown    how faded in it is, 0 to 1
     * @param grown    how much of its room it takes, 0 to 1: its height (and width, if
     *                 the element grows it) as it comes and goes
     * @param gapAbove how much of the list's row gap sits above it, 0 to 1. The rows are
     *                 always exactly one gap apart however far each has grown: each brings
     *                 a gap as big as it has grown, and one gap's worth is taken back off
     *                 from the top down, so a row growing in anywhere never jolts the next
     */
    public record Row<T>(T value, float shown, float grown, float gapAbove) {
    }

    private final Function<T, String> key;
    private final Map<String, State<T>> states = new LinkedHashMap<>();
    private boolean primed;

    /** @param key tells rows apart: a module's name, an effect's id */
    public AnimatedRows(Function<T, String> key) {
        this.key = key;
    }

    /**
     * @param wanted what should show this frame, in any order
     * @param order  how every row is ordered, the leaving ones included
     * @return every row to draw this frame, in order
     */
    public List<Row<T>> update(List<T> wanted, Comparator<T> order) {
        HudStyle.Motion motion = HudStyle.get().motion();
        Set<String> keys = new HashSet<>();
        for (T value : wanted) {
            String id = key.apply(value);
            keys.add(id);
            State<T> state = states.get(id);
            if (state == null) {
                // What is on when the list first draws is simply there; only a change animates.
                states.put(id, state = new State<>(!primed));
            }
            state.value = value;
        }
        primed = true;
        for (Map.Entry<String, State<T>> entry : states.entrySet()) {
            boolean coming = keys.contains(entry.getKey());
            State<T> state = entry.getValue();
            state.wanted = coming;
            state.presence.duration(motion == HudStyle.Motion.OFF ? 0L : coming ? ENTER_MILLIS : LEAVE_MILLIS)
                    .target(coming ? 1f : 0f);
        }
        states.values().removeIf(state -> !state.wanted && state.presence.isIdleAtZero());

        List<State<T>> ordered = new ArrayList<>(states.values());
        ordered.sort((a, b) -> order.compare(a.value, b.value));

        float[] gaps = new float[ordered.size()];
        float total = 0f;
        for (int i = 0; i < gaps.length; i++) {
            gaps[i] = ordered.get(i).grown(motion);
            total += gaps[i];
        }
        float takeBack = Math.min(1f, total);
        for (int i = 0; i < gaps.length && takeBack > 0f; i++) {
            float taken = Math.min(gaps[i], takeBack);
            gaps[i] -= taken;
            takeBack -= taken;
        }

        List<Row<T>> rows = new ArrayList<>(ordered.size());
        for (int i = 0; i < gaps.length; i++) {
            State<T> state = ordered.get(i);
            rows.add(new Row<>(state.value, state.presence.get(), state.grown(motion), gaps[i]));
        }
        return rows;
    }

    /** @return whether any row is still coming or going with movement: its content may run past the plate */
    public boolean moving() {
        if (HudStyle.get().motion() != HudStyle.Motion.FULL) {
            return false;
        }
        for (State<T> state : states.values()) {
            if (!state.presence.isFinished()) {
                return true;
            }
        }
        return false;
    }

    /** A row's motion: how far it has come in, and what it shows (kept while it leaves). */
    private static final class State<T> {

        private final Animation presence;
        private T value;
        private boolean wanted;

        private State(boolean already) {
            this.presence = Animation.of(already ? 1f : 0f, ENTER_MILLIS, Easing.CUBIC_OUT);
        }

        /** @return how much room it takes: as it shows at Full; whole while it shows otherwise */
        private float grown(HudStyle.Motion motion) {
            float shown = presence.get();
            return motion == HudStyle.Motion.FULL ? shown : shown > 0f ? 1f : 0f;
        }
    }
}
