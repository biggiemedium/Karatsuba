package dev.px.karatsuba.adapter.event;

import dev.px.core.Core;
import dev.px.core.event.Cancellable;
import dev.px.core.event.Event;

/**
 * The gate every mixin posts through.
 *
 * <p>Mixins are installed by the loader long before Core exists, and
 * {@code Core.get()} throws rather than returning null before {@code build()}.
 * A packet arrives, a screen is set and a sound plays during startup, so every
 * injection point in this mod needs the same guard &mdash; and getting it wrong
 * means a crash on the title screen, which is the worst place to find out.
 *
 * <p>One flag, raised once by {@code MinecraftAdapter.install}, rather than one
 * per bridge. Before that, posting is a no-op that hands the event straight back
 * unposted, so a caller's {@code isCancelled()} is false and the game carries on
 * exactly as it would with the mod absent.
 *
 * <pre>{@code
 * if (Events.post(new PlayerJumpEvent(position, sprinting)).isCancelled()) {
 *     callback.cancel();
 * }
 * }</pre>
 */
public final class Events {

    private static volatile boolean armed;

    private Events() {
    }

    /**
     * Opens the gate.
     *
     * <p>Called from {@code MinecraftAdapter.install}, which runs between
     * {@code Core.builder(...).build()} and {@code Core.start()} &mdash; so Core
     * exists by then, and every service it posts to is about to.
     */
    public static void arm() {
        armed = true;
    }

    /** @return whether Core is up and events are reaching it */
    public static boolean isArmed() {
        return armed;
    }

    /**
     * Posts an event if the client is up.
     *
     * @return the same event, so a caller can post and inspect in one expression.
     *         Unposted events come back untouched, which for a
     *         {@link Cancellable} means not cancelled.
     */
    public static <T extends Event> T post(T event) {
        return armed ? Core.bus().post(event) : event;
    }

    /**
     * Posts a cancellable event and reports the verdict.
     *
     * <p>The shape almost every mixin wants, because an injection either cancels
     * or it does not and the event itself is of no further interest.
     *
     * @return whether a handler refused the action
     */
    public static <T extends Event & Cancellable> boolean cancelled(T event) {
        return post(event).isCancelled();
    }
}
