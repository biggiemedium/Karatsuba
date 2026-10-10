package dev.px.karatsuba.adapter.network;

import net.minecraft.network.ClientConnection;
import net.minecraft.network.PacketCallbacks;
import net.minecraft.network.packet.Packet;

import java.util.ArrayDeque;
import java.util.Deque;

/**
 * Holds every outgoing packet back, in order, and lets them all go at once.
 *
 * <p>What a short network stall looks like from the server's side: nothing for a
 * tick or two, then everything the client did in between, in the order it did
 * it. Nothing is dropped, reordered or rewritten. The packets were seen by every
 * send handler when the game sent them; letting go only puts them on the wire.
 *
 * <p>The point is which server tick a packet is read in. The server reads every
 * packet that has arrived, then ticks its players, so packets let go together are
 * read together, and the server only acts on how things stand after the last of
 * them. Elytra Fly's Bounce holds the landing packet until the hop's packet is
 * ready: read together, the server never sees the player standing at the moment
 * it ticks them, and never ends the glide.
 *
 * <p>Why everything and not only the movement: Grim learns what the client has
 * processed from the order of its replies (transaction pongs) against its
 * movement. Holding all of it keeps that order exactly as it happened, so Grim
 * sees nothing but latency, which it is built to absorb. The keep-alive reply is
 * held as well; a few ticks is nothing to its 15 second timeout.
 *
 * <p>A hold always ends: {@link #MAX_TICKS} after it began it lets go by itself
 * (see {@link #tick()}), and leaving a world lets go too.
 *
 * <p>Sending can happen on Netty's thread (a ping answered inline), so the queue
 * is guarded.
 */
public final class PacketHold {

    /** The longest a hold may last before it lets go on its own. */
    public static final int MAX_TICKS = 5;

    private record Held(ClientConnection connection, Packet<?> packet, PacketCallbacks callbacks, boolean flush) {
    }

    private static final Object LOCK = new Object();
    private static final Deque<Held> QUEUE = new ArrayDeque<>();

    private static boolean holding;
    private static int ticks;

    /** Set while letting go, so the packets going out are not held again or seen twice. */
    private static final ThreadLocal<Boolean> RELEASING = ThreadLocal.withInitial(() -> false);

    private PacketHold() {
    }

    /** Starts holding. Everything sent from now on waits for {@link #release()}. */
    public static void hold() {
        synchronized (LOCK) {
            if (!holding) {
                holding = true;
                ticks = 0;
            }
        }
    }

    /** @return whether packets are being held */
    public static boolean isHolding() {
        synchronized (LOCK) {
            return holding;
        }
    }

    /** Lets everything held go, in the order it was sent, and stops holding. */
    public static void release() {
        Deque<Held> out;
        synchronized (LOCK) {
            holding = false;
            if (QUEUE.isEmpty()) {
                return;
            }
            out = new ArrayDeque<>(QUEUE);
            QUEUE.clear();
        }
        RELEASING.set(true);
        try {
            // Written without flushing, then flushed once: one write on the wire,
            // so the server reads the lot in one go rather than maybe across a tick.
            Held last = out.peekLast();
            for (Held held : out) {
                if (held.connection().isOpen()) {
                    held.connection().send(held.packet(), held.callbacks(), held == last);
                }
            }
        } finally {
            RELEASING.set(false);
        }
    }

    /** Called once a tick by the event bridge: a hold left on too long lets go. */
    public static void tick() {
        boolean expired;
        synchronized (LOCK) {
            expired = holding && ++ticks > MAX_TICKS;
        }
        if (expired) {
            release();
        }
    }

    /** Drops anything held for a connection that is gone. */
    public static void clear() {
        synchronized (LOCK) {
            holding = false;
            QUEUE.clear();
        }
    }

    /** @return whether this send is a held packet going out, which nothing should see again */
    public static boolean isReleasing() {
        return RELEASING.get();
    }

    /**
     * Called by the connection mixin for each packet that survived the send
     * handlers.
     *
     * @return whether the packet was taken into the hold, and must not go now
     */
    public static boolean take(ClientConnection connection, Packet<?> packet, PacketCallbacks callbacks, boolean flush) {
        synchronized (LOCK) {
            if (!holding) {
                return false;
            }
            QUEUE.add(new Held(connection, packet, callbacks, flush));
            return true;
        }
    }
}
