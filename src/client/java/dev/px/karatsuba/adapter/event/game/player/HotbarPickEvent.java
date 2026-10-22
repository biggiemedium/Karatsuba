package dev.px.karatsuba.adapter.event.game.player;

import dev.px.core.event.Event;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * The player picked a hotbar slot themselves: a number key or the mouse wheel.
 *
 * <p>Never posted for a slot a module selects. Posted after the game has
 * selected it, on the game thread, so {@link #getSlot()} is what is in hand now.
 */
@Getter
@RequiredArgsConstructor
public final class HotbarPickEvent extends Event {

    /** The slot picked, 0 to 8. */
    private final int slot;
}
