package dev.px.karatsuba.client.service;

import dev.px.core.Core;
import dev.px.core.event.EventBus;
import dev.px.core.event.Listenable;
import dev.px.core.event.Priority;
import dev.px.core.event.Stage;
import dev.px.core.event.Subscription;
import dev.px.core.event.impl.TickEvent;
import dev.px.core.event.impl.WorldEvent;
import dev.px.core.service.Service;
import dev.px.core.util.CoreLogger;
import dev.px.karatsuba.adapter.event.game.network.PacketEvent;
import lombok.Getter;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

/**
 * Routes packets to the modules that asked for them, and counts the rest.
 *
 * <p>Minecraft is a packet-heavy game: a busy tick moves hundreds, and the
 * obvious arrangement &mdash; every interested module subscribing to
 * {@link PacketEvent} &mdash; wakes every one of them for every packet and makes
 * each do its own name comparison. Twenty modules is twenty string compares per
 * packet, several thousand times a second, to find the one packet in four
 * hundred that anybody wanted.
 *
 * <p>So this subscribes <b>once</b> and dispatches by name:
 *
 * <pre>{@code
 * public MyModule() {
 *     Services.packets().onSend(this, "ClientCommandC2SPacket", event -> event.cancel());
 * }
 * }</pre>
 *
 * <p>A module registers once, for good, and is called only for the packet it
 * named. There is no unregister to forget, because delivery is gated on the
 * owner: a {@link Listenable} owner &mdash; and every module is one &mdash; stops
 * receiving the moment it is disabled. That is the same rule Core's event bus
 * uses for {@code @Subscribe} handlers, and it means a listener registered in a
 * constructor is correct for the life of the client.
 *
 * <h2>Threading, which is the thing to get right</h2>
 * <b>{@link #onSend} runs on the game thread. {@link #onReceive} runs on a Netty
 * worker.</b> Receiving is the only place a packet can be refused before the game
 * acts on it, so it cannot be moved; that means an inbound listener must not
 * touch the world, the player or anything a frame is drawing.
 *
 * <p>{@link #onReceiveSync} is the safe version: the packet is delivered on the
 * next tick, on the game thread, and cannot be cancelled because by then the game
 * has already dealt with it. Reach for that one unless cancelling is the point.
 *
 * <h2>When not to use this</h2>
 * A packet with a meaning has an event of its own &mdash;
 * {@code MovementPacketEvent}, {@code KnockbackEvent},
 * {@code ClientCommandEvent}, {@code ScreenCloseEvent}, {@code PlacementResultEvent}
 * (the server settling a placed block, from its sequence acknowledgement). Those carry Core types,
 * arrive on the right thread, and survive a rename that would silently break a
 * string match. Use this for packets nothing has named yet, and when a module
 * needs one often enough, name it.
 */
public final class PacketService implements Service {

    private final EventBus bus;
    private final CoreLogger logger;

    /** Keyed by packet simple name. Concurrent: outbound is the game thread, inbound is not. */
    private final Map<String, List<Listener>> outbound = new ConcurrentHashMap<>();
    private final Map<String, List<Listener>> inbound = new ConcurrentHashMap<>();

    private final AtomicInteger sentThisSecond = new AtomicInteger();
    private final AtomicInteger receivedThisSecond = new AtomicInteger();

    /** Packets out per second, as of the last whole second. */
    @Getter
    private int sentPerSecond;

    /** Packets in per second, as of the last whole second. */
    @Getter
    private int receivedPerSecond;

    private long secondStartedAt;

    private Subscription sendSubscription;
    private Subscription receiveSubscription;
    private Subscription tickSubscription;
    private Subscription worldSubscription;

    public PacketService(EventBus bus, CoreLogger logger) {
        this.bus = bus;
        this.logger = logger;
    }

    @Override
    public String getName() {
        return "Packets";
    }

    @Override
    public void start() {
        // LOWEST so a module with its own @Subscribe on PacketEvent, and anything
        // that means to cancel outright, has already been heard.
        sendSubscription = bus.on(PacketEvent.Send.class, Priority.LOWEST, event -> {
            sentThisSecond.incrementAndGet();
            dispatch(outbound, event);
        });
        receiveSubscription = bus.on(PacketEvent.Receive.class, Priority.LOWEST, event -> {
            receivedThisSecond.incrementAndGet();
            dispatch(inbound, event);
        });
        tickSubscription = bus.on(TickEvent.class, event -> {
            if (event.getStage() == Stage.POST) {
                rollCounters();
            }
        });
        // Rates from the last server mean nothing on this one.
        worldSubscription = bus.on(WorldEvent.class, event -> resetCounters());
        secondStartedAt = System.currentTimeMillis();
    }

    @Override
    public void stop() {
        close(sendSubscription);
        close(receiveSubscription);
        close(tickSubscription);
        close(worldSubscription);
        sendSubscription = null;
        receiveSubscription = null;
        tickSubscription = null;
        worldSubscription = null;

        outbound.clear();
        inbound.clear();
        resetCounters();
    }

    // ------------------------------------------------------------ listening

    /**
     * Called for every outgoing packet with this class name, on the game thread.
     *
     * <p>The handler may cancel. Register once, at construction; there is nothing
     * to unregister.
     *
     * @param packetName the packet class's simple name, e.g. {@code PlayerMoveC2SPacket}
     */
    public void onSend(Object owner, String packetName, Consumer<PacketEvent.Send> handler) {
        add(outbound, owner, packetName, handler);
    }

    /**
     * Called for every incoming packet with this class name, <b>on a Netty
     * worker</b>.
     *
     * <p>The handler may cancel, which is the only reason to be here rather than
     * on {@link #onReceiveSync}. It must not touch game state; see the note on
     * this class.
     */
    public void onReceive(Object owner, String packetName, Consumer<PacketEvent.Receive> handler) {
        add(inbound, owner, packetName, handler);
    }

    /**
     * Called for every incoming packet with this class name, on the game thread,
     * on the next tick.
     *
     * <p>Cannot cancel &mdash; the game has already handled the packet by the time
     * this runs &mdash; and in exchange it is safe to read the world from. The
     * right choice for anything counting, recording or reacting rather than
     * intercepting.
     */
    public void onReceiveSync(Object owner, String packetName, Consumer<PacketEvent.Receive> handler) {
        // Typed explicitly: inferred from add(...)'s wildcard the lambda would be a
        // Consumer<PacketEvent>, which is not what the caller handed us.
        Consumer<PacketEvent.Receive> deferred =
                event -> Core.threads().sync(() -> handler.accept(event));
        add(inbound, owner, packetName, deferred);
    }

    /**
     * Drops every listener this owner registered.
     *
     * <p>Rarely needed: delivery already stops when a {@link Listenable} owner
     * goes quiet. This is for an owner that is genuinely going away, such as a
     * screen or a one-off task.
     */
    public boolean forget(Object owner) {
        return remove(outbound, owner) | remove(inbound, owner);
    }

    /** @return how many listeners are registered, across both directions */
    public int getListenerCount() {
        return count(outbound) + count(inbound);
    }

    // ------------------------------------------------------------ internals

    private void add(Map<String, List<Listener>> table, Object owner,
                     String packetName, Consumer<? extends PacketEvent> handler) {
        if (owner == null || packetName == null || packetName.isEmpty() || handler == null) {
            return;
        }
        table.computeIfAbsent(packetName, name -> new CopyOnWriteArrayList<>())
                .add(new Listener(owner, handler));
    }

    private static boolean remove(Map<String, List<Listener>> table, Object owner) {
        boolean removed = false;
        for (List<Listener> listeners : table.values()) {
            removed |= listeners.removeIf(listener -> listener.owner == owner);
        }
        return removed;
    }

    private static int count(Map<String, List<Listener>> table) {
        int total = 0;
        for (List<Listener> listeners : table.values()) {
            total += listeners.size();
        }
        return total;
    }

    /**
     * Hands one packet to whoever asked for that name.
     *
     * <p>A map lookup and nothing else for the overwhelming majority of packets,
     * which is the entire point of the class. A listener that throws is logged
     * once per owner and skipped, because a broken module must not stop the
     * client from talking to the server.
     */
    @SuppressWarnings("unchecked")
    private <T extends PacketEvent> void dispatch(Map<String, List<Listener>> table, T event) {
        List<Listener> listeners = table.get(event.getName());
        if (listeners == null) {
            return;
        }
        for (Listener listener : listeners) {
            if (!listener.isListening()) {
                continue;
            }
            try {
                ((Consumer<T>) listener.handler).accept(event);
            } catch (RuntimeException failure) {
                listener.reportOnce(logger, event.getName(), failure);
            }
        }
    }

    private void rollCounters() {
        long now = System.currentTimeMillis();
        if (now - secondStartedAt < 1_000L) {
            return;
        }
        secondStartedAt = now;
        sentPerSecond = sentThisSecond.getAndSet(0);
        receivedPerSecond = receivedThisSecond.getAndSet(0);
    }

    private void resetCounters() {
        sentThisSecond.set(0);
        receivedThisSecond.set(0);
        sentPerSecond = 0;
        receivedPerSecond = 0;
    }

    private static void close(Subscription subscription) {
        if (subscription != null) {
            subscription.close();
        }
    }

    /** One registration, plus the gate that makes unregistering unnecessary. */
    private static final class Listener {

        private final Object owner;
        private final Consumer<? extends PacketEvent> handler;
        private boolean reported;

        private Listener(Object owner, Consumer<? extends PacketEvent> handler) {
            this.owner = owner;
            this.handler = handler;
        }

        /**
         * @return whether the owner currently wants packets
         *
         * <p>A module is {@link Listenable} and answers false while disabled,
         * which is what lets a listener be registered once and never torn down.
         * An owner that is not {@code Listenable} always receives.
         */
        boolean isListening() {
            return !(owner instanceof Listenable) || ((Listenable) owner).isListening();
        }

        void reportOnce(CoreLogger logger, String packetName, RuntimeException failure) {
            if (reported) {
                return;
            }
            reported = true;
            logger.error("Packet listener on " + owner.getClass().getSimpleName()
                    + " threw handling " + packetName
                    + "; further failures from it will not be logged", failure);
        }
    }

    /**
     * @return a snapshot of how many listeners want each packet name
     *
     * <p>For a debug command or a HUD readout. Ordered by name so the output is
     * stable between calls.
     */
    public Map<String, Integer> getListenerBreakdown() {
        Map<String, Integer> breakdown = new LinkedHashMap<>();
        List<String> names = new ArrayList<>(outbound.keySet());
        names.addAll(inbound.keySet());
        names.sort(String::compareTo);

        for (String name : names) {
            int total = size(outbound.get(name)) + size(inbound.get(name));
            if (total > 0) {
                breakdown.put(name, total);
            }
        }
        return breakdown;
    }

    private static int size(List<Listener> listeners) {
        return listeners == null ? 0 : listeners.size();
    }
}
