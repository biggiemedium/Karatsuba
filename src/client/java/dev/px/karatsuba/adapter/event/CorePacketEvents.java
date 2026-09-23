package dev.px.karatsuba.adapter.event;

import dev.px.core.event.impl.PacketEvent;

/**
 * Posts Core's own {@link PacketEvent}, alongside the adapter's.
 *
 * <p>Two packet events exist and they answer different callers. The adapter's
 * {@link dev.px.karatsuba.adapter.event.game.PacketEvent} is what modules match
 * on by name. Core's is what Core itself listens to &mdash; the timeline recorder
 * above all &mdash; and it has a third phase the adapter's does not:
 * {@link PacketEvent.Phase#APPLIED}, the moment the game thread actually handles
 * an inbound packet, which is the tick a teleport or a knockback took effect on.
 *
 * <p>The mixins call this rather than naming Core's class themselves, because
 * both classes are called {@code PacketEvent} and a mixin that imports one and
 * spells the other out in full is a mixin that is easy to get backwards.
 *
 * <h2>Refusals carry across</h2>
 * The adapter's handlers run first, because they are the ones that can rewrite a
 * movement packet. If they refused the packet, Core's event is posted already
 * cancelled rather than not at all, so a recording shows the packet the client
 * almost sent and says it was stopped.
 */
public final class CorePacketEvents {

    private CorePacketEvents() {
    }

    /**
     * An outbound packet, about to be written.
     *
     * @param refused whether the adapter's handlers already refused it
     * @return whether it should not be sent
     */
    public static boolean sent(Object packet, boolean refused) {
        return post(PacketEvent.sent(packet), refused);
    }

    /**
     * An inbound packet, just decoded. <b>Netty worker.</b>
     *
     * @param refused whether the adapter's handlers already refused it
     * @return whether it should be dropped
     */
    public static boolean received(Object packet, boolean refused) {
        return post(PacketEvent.received(packet), refused);
    }

    /**
     * An inbound packet, being handled on the game thread.
     *
     * <p>Not cancellable in practice: the injection points that post this cannot
     * skip the handler without skipping the game's own bookkeeping with it.
     */
    public static void applied(Object packet) {
        Events.post(PacketEvent.applied(packet));
    }

    /**
     * A refusal is final: a Core handler that un-cancels the event does not get
     * to send a packet the adapter's handlers stopped.
     */
    private static boolean post(PacketEvent event, boolean refused) {
        if (refused) {
            event.cancel();
        }
        return Events.cancelled(event) || refused;
    }
}
