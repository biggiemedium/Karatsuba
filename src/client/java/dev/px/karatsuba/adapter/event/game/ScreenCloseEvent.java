package dev.px.karatsuba.adapter.event.game;

import dev.px.core.event.CancellableEvent;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * The client is about to tell the server a container screen was closed.
 *
 * <p>A worked example of what this package is for. The underlying fact is a
 * {@code CloseHandledScreenC2SPacket} with a sync id, and a module could match
 * that on {@link PacketEvent} &mdash; but it would then have to know which sync
 * id belongs to the player's own inventory, which means holding a screen handler,
 * which means importing the game. The distinction a module actually cares about
 * is the one below, so the adapter works it out once and publishes it.
 *
 * <p>Cancelling suppresses the packet. The screen still closes on the client; the
 * server is simply never told, which is what leaves the four crafting slots
 * holding whatever was in them instead of dropping it.
 *
 * <pre>{@code
 * @Subscribe
 * private void onScreenClose(ScreenCloseEvent event) {
 *     if (event.isOwnInventory()) {
 *         event.cancel();
 *     }
 * }
 * }</pre>
 *
 * <p>Posted on the game thread, from the same place {@link PacketEvent.Send} is.
 * A handler that cancels this cancels the packet, so both events see the
 * cancellation.
 */
@Getter
@RequiredArgsConstructor
public final class ScreenCloseEvent extends CancellableEvent {

    /** The container's sync id, as the server knows it. */
    private final int syncId;

    /**
     * Whether this is the player's own inventory rather than a chest, a furnace or
     * any other container.
     *
     * <p>The whole reason this event exists. Cancelling a chest close desynchronises
     * the client from the server and makes the next chest unopenable; cancelling
     * the player's own is harmless and is what keeps items in the crafting grid.
     */
    private final boolean ownInventory;
}
