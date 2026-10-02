package dev.px.karatsuba.adapter.event.game.network;

import dev.px.core.event.CancellableEvent;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * A state change the client is about to report to the server: sprinting,
 * sneaking, leaving a bed, opening a horse's inventory.
 *
 * <p>The same pattern as {@link ScreenCloseEvent}, and it exists for the same
 * reason. All of these travel as one packet with a mode field, so a module
 * matching on {@link PacketEvent} alone can only see "some client command" and
 * cancelling that would suppress sneaking and elytra deploys along with
 * whatever it meant to stop. The mode is what a module actually cares about, so
 * the adapter reads it once and publishes it as an enum.
 *
 * <pre>{@code
 * @Subscribe
 * private void onCommand(ClientCommandEvent event) {
 *     if (event.isSprintChange()) {
 *         event.cancel();
 *     }
 * }
 * }</pre>
 *
 * <p>Cancelling suppresses the packet. The client's own state still changes;
 * the server is simply never told, which for sprinting is the whole trick &mdash;
 * the player keeps the speed and the server keeps thinking they are walking.
 *
 * <p>Posted on the game thread, from the same injection as
 * {@link PacketEvent.Send}.
 */
@Getter
@RequiredArgsConstructor
public final class ClientCommandEvent extends CancellableEvent {

    /**
     * What is being reported.
     *
     * <p>{@link #OTHER} covers the modes nothing has needed yet rather than
     * naming all nine: a module matching {@code OTHER} is asking for something it
     * cannot act on meaningfully, and adding a constant here is the honest way to
     * grow the list.
     */
    public enum Action {

        START_SPRINTING,
        STOP_SPRINTING,
        START_SNEAKING,
        STOP_SNEAKING,
        START_FALL_FLYING,
        OTHER
    }

    private final Action action;

    /** @return whether this is a sprint packet in either direction */
    public boolean isSprintChange() {
        return action == Action.START_SPRINTING || action == Action.STOP_SPRINTING;
    }

    /** @return whether this is a sneak packet in either direction */
    public boolean isSneakChange() {
        return action == Action.START_SNEAKING || action == Action.STOP_SNEAKING;
    }
}
