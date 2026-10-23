package dev.px.karatsuba.adapter.network;

import dev.px.core.network.packet.PacketDescription;
import dev.px.core.network.packet.PacketKind;
import net.minecraft.network.ClientConnection;
import net.minecraft.network.PacketCallbacks;
import net.minecraft.network.packet.Packet;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Deque;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

/**
 * Holds outgoing packets back, in order, and lets them go at once.
 *
 * <p>What a short network stall looks like from the server's side: nothing for a
 * tick or two, then everything the client did in between, in the order it did
 * it. Nothing is dropped or rewritten. The packets were seen by every send
 * handler when the game sent them; letting go only puts them on the wire.
 *
 * <p>The point is which server tick a packet is read in. The server reads every
 * packet that has arrived, then ticks its players, so packets let go together are
 * read together, and the server only acts on how things stand after the last of
 * them. Elytra Fly's Bounce holds the landing packet until the hop's packet is
 * ready: read together, the server never sees the player standing at the moment
 * it ticks them, and never ends the glide.
 *
 * <h2>Everything, or only some kinds</h2>
 * By default a hold takes everything. Grim learns what the client has processed
 * from the order of its replies (transaction pongs) against its movement, and
 * holding all of it keeps that order exactly as it happened, so Grim sees nothing
 * but latency, which it is built to absorb. The keep-alive reply is held as well;
 * a few ticks is nothing to its 15 second timeout.
 *
 * <p>A {@link Hold} can instead take {@linkplain Hold#only only some kinds}, as
 * {@link MinecraftPacketDescriber} files them. Then the rest go out as they are
 * sent, ahead of the held ones: the order the server reads them in is no longer
 * the order they were sent in, and Grim can see that. {@link #isSplitting()} says
 * when that is happening, so the services that send for modules can wait it out
 * rather than send an action against a position the server has not been told.
 *
 * <h2>Holds</h2>
 * Any number of holds can be on at once, each its own: a packet is held while any
 * hold on takes it, and letting one hold go sends only what no other hold still
 * takes. {@link #hold()} and {@link #release()} drive a shared hold of everything,
 * for anything short-lived; a module that holds for longer, or holds only some
 * kinds, {@linkplain #create creates} its own, so another module letting go never
 * lets go of its packets.
 *
 * <p>A hold always ends: its {@linkplain Hold#lasting limit} after it began it
 * lets go by itself (see {@link #tick()}), and leaving a world lets go of all.
 *
 * <p>Sending can happen on Netty's thread (a ping answered inline), so the queue
 * and every hold's state are guarded, and a hold's kinds are checked under that
 * guard: classifying a packet reads only the packet.
 */
public final class PacketHold {

    /** The longest the shared hold may last before it lets go on its own, and a new hold's limit. */
    public static final int MAX_TICKS = 5;

    private record Held(ClientConnection connection, Packet<?> packet, PacketCallbacks callbacks, boolean flush) {
    }

    private static final Object LOCK = new Object();
    private static final Deque<Held> QUEUE = new ArrayDeque<>();

    /** The holds that are on, oldest first. */
    private static final List<Hold> ACTIVE = new ArrayList<>();

    /** The hold {@link #hold()} and {@link #release()} drive: everything, for {@link #MAX_TICKS}. */
    private static final Hold SHARED = new Hold("shared");

    /** Set while letting go, so the packets going out are not held again or seen twice. */
    private static final ThreadLocal<Boolean> RELEASING = ThreadLocal.withInitial(() -> false);

    private PacketHold() {
    }

    /** @return a new hold of everything, off until {@link Hold#start()}; {@code name} is for display */
    public static Hold create(String name) {
        return new Hold(name);
    }

    /** Starts the shared hold. Everything sent from now on waits for {@link #release()}. */
    public static void hold() {
        SHARED.start();
    }

    /** Lets the shared hold go: everything it held that no other hold takes, in the order it was sent. */
    public static void release() {
        SHARED.release();
    }

    /** @return whether any hold is on */
    public static boolean isHolding() {
        synchronized (LOCK) {
            return !ACTIVE.isEmpty();
        }
    }

    /** @return whether a packet of this kind sent now would be held */
    public static boolean isHolding(PacketKind kind) {
        synchronized (LOCK) {
            for (Hold hold : ACTIVE) {
                if (hold.takes(kind)) {
                    return true;
                }
            }
            return false;
        }
    }

    /**
     * @return whether some packets are held while others go: holds are on and none
     *         takes everything, so the server reads what is sent now ahead of what
     *         was sent before it
     */
    public static boolean isSplitting() {
        synchronized (LOCK) {
            if (ACTIVE.isEmpty()) {
                return false;
            }
            for (Hold hold : ACTIVE) {
                if (hold.kinds == null) {
                    return false;
                }
            }
            return true;
        }
    }

    /** Called once a tick by the event bridge: a hold left on past its limit lets go. */
    public static void tick() {
        List<Hold> expired = new ArrayList<>();
        synchronized (LOCK) {
            for (Hold hold : ACTIVE) {
                if (++hold.ticks > hold.maxTicks) {
                    expired.add(hold);
                }
            }
        }
        for (Hold hold : expired) {
            hold.release();
        }
    }

    /** Drops anything held for a connection that is gone, and turns every hold off. */
    public static void clear() {
        synchronized (LOCK) {
            for (Hold hold : ACTIVE) {
                hold.active = false;
            }
            ACTIVE.clear();
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
            if (ACTIVE.isEmpty() || !wanted(packet)) {
                return false;
            }
            QUEUE.add(new Held(connection, packet, callbacks, flush));
            return true;
        }
    }

    /** @return the kind {@link MinecraftPacketDescriber} files a packet under; {@link PacketKind#OTHER} if none */
    public static PacketKind kindOf(Packet<?> packet) {
        PacketDescription description = MinecraftPacketDescriber.INSTANCE.describe(packet);
        return description == null ? PacketKind.OTHER : description.getKind();
    }

    /** @return whether a hold that is on takes this packet. Under {@link #LOCK}. */
    private static boolean wanted(Packet<?> packet) {
        PacketKind kind = null;
        for (Hold hold : ACTIVE) {
            if (hold.kinds == null) {
                return true;
            }
            if (kind == null) {
                kind = kindOf(packet);
            }
            if (hold.kinds.contains(kind)) {
                return true;
            }
        }
        return false;
    }

    /** Turns {@code hold} off and sends, in order, everything no hold still on takes. */
    private static void letGo(Hold hold) {
        List<Held> out = new ArrayList<>();
        synchronized (LOCK) {
            hold.active = false;
            ACTIVE.remove(hold);
            for (Iterator<Held> it = QUEUE.iterator(); it.hasNext(); ) {
                Held held = it.next();
                if (!wanted(held.packet())) {
                    out.add(held);
                    it.remove();
                }
            }
        }
        if (out.isEmpty()) {
            return;
        }
        RELEASING.set(true);
        try {
            // Written without flushing, then flushed once: one write on the wire,
            // so the server reads the lot in one go rather than maybe across a tick.
            Held last = out.get(out.size() - 1);
            for (Held held : out) {
                if (held.connection().isOpen()) {
                    held.connection().send(held.packet(), held.callbacks(), held == last);
                }
            }
        } finally {
            RELEASING.set(false);
        }
    }

    /**
     * One module's hold: which kinds it takes, how long it may last, and whether it
     * is on.
     *
     * <p>Kinds and limit can change while it is on; a new limit counts from when it
     * began, and new kinds apply to what is sent from then.
     */
    public static final class Hold {

        private final String name;

        /** What it takes; null for everything. */
        private Set<PacketKind> kinds;

        private int maxTicks = MAX_TICKS;

        private boolean active;
        private int ticks;

        private Hold(String name) {
            this.name = name;
        }

        /** Takes only packets of these kinds; none at all if empty. */
        public Hold only(Collection<? extends PacketKind> only) {
            synchronized (LOCK) {
                kinds = Set.copyOf(only);
            }
            return this;
        }

        /** Takes every packet. The default. */
        public Hold everything() {
            synchronized (LOCK) {
                kinds = null;
            }
            return this;
        }

        /** Lets go by itself once on for more than {@code limit} ticks. {@link #MAX_TICKS} by default. */
        public Hold lasting(int limit) {
            synchronized (LOCK) {
                maxTicks = Math.max(1, limit);
            }
            return this;
        }

        /** Turns it on. Already on, it carries on, its time not reset. */
        public void start() {
            synchronized (LOCK) {
                if (!active) {
                    active = true;
                    ticks = 0;
                    ACTIVE.add(this);
                }
            }
        }

        /** Turns it off and sends everything it held that no other hold takes, in the order it was sent. */
        public void release() {
            letGo(this);
        }

        public String getName() {
            return name;
        }

        /** @return whether it is on: false again once let go, by anyone or by its limit */
        public boolean isActive() {
            synchronized (LOCK) {
                return active;
            }
        }

        /** @return ticks it has been on; 0 while off */
        public int getTicks() {
            synchronized (LOCK) {
                return active ? ticks : 0;
            }
        }

        /** @return the packets waiting that it takes */
        public int size() {
            synchronized (LOCK) {
                if (!active) {
                    return 0;
                }
                if (kinds == null) {
                    return QUEUE.size();
                }
                int count = 0;
                for (Held held : QUEUE) {
                    if (kinds.contains(kindOf(held.packet()))) {
                        count++;
                    }
                }
                return count;
            }
        }

        /** Under {@link #LOCK}. */
        private boolean takes(PacketKind kind) {
            return kinds == null || kinds.contains(kind);
        }

        @Override
        public String toString() {
            return name;
        }
    }
}
