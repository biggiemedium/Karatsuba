package dev.px.karatsuba.adapter.event.game.player;

import dev.px.core.event.CancellableEvent;
import dev.px.karatsuba.adapter.game.player.DeathCause;
import lombok.Getter;

/**
 * The game is about to show its death screen.
 *
 * <p>Posted from {@code MinecraftClient.setScreen}, before the screen is shown or
 * initialised. There are two ways in, and Core's {@code ScreenEvent} only sees
 * the first:
 *
 * <ul>
 *   <li>the server's death message opens one;</li>
 *   <li>every tick the player is dead with no screen up, the game calls
 *       {@code setScreen(null)} and swaps in a new one.</li>
 * </ul>
 *
 * <p>So this may come more than once for one death: each time the game builds a
 * new death screen, after anything that took the last one away.
 *
 * <ul>
 *   <li>{@link #setTitle} replaces "You Died!" (or "Game Over!" in hardcore).</li>
 *   <li>{@link #cancel()} shows no death screen. If an overlay is capturing once
 *       every handler has run, the bare {@code CoreScreen} goes up for it, so a
 *       handler that wants its own death screen opens its overlay here. If none
 *       is, no screen goes up, and next tick the game asks again.</li>
 * </ul>
 *
 * <p>Posted on the game thread, inside {@code setScreen}: a handler must not
 * change the screen itself.
 */
@Getter
public final class DeathScreenEvent extends CancellableEvent {

    /** What killed the player, as the server worded it; empty when the game gave none. */
    private final String message;

    /** Whether this world is hardcore, where respawning means spectating. */
    private final boolean hardcore;

    /** Who killed the player (the player themselves if nobody did) and with what. */
    private final DeathCause cause;

    /** The title to show in place of the game's, or null for the game's. */
    private String title;

    public DeathScreenEvent(String message, boolean hardcore, DeathCause cause) {
        this.message = message;
        this.hardcore = hardcore;
        this.cause = cause;
    }

    /** Replaces the screen's title. Ignored if the event is cancelled. */
    public void setTitle(String title) {
        this.title = title;
    }
}
