package dev.px.karatsuba.client.debug;

import dev.px.core.Core;
import dev.px.core.event.Priority;
import dev.px.core.event.Stage;
import dev.px.core.event.Subscribe;
import dev.px.core.event.impl.TickEvent;
import dev.px.core.event.impl.WorldEvent;
import dev.px.core.math.Vec3;
import dev.px.core.network.PacketListener;
import dev.px.core.network.anticheat.AntiCheatChangeEvent;
import dev.px.core.network.lag.LagSpikeEvent;
import dev.px.core.network.packet.PacketDescription;
import dev.px.core.network.packet.PacketKind;
import dev.px.core.network.server.ServerBrand;
import dev.px.core.network.server.ServerChangeEvent;
import dev.px.core.util.collect.CircularQueue;
import dev.px.core.util.time.RateMeter;
import dev.px.karatsuba.adapter.event.game.player.KnockbackEvent;
import dev.px.karatsuba.adapter.event.game.player.ServerTeleportEvent;
import dev.px.karatsuba.adapter.game.player.Players;
import dev.px.karatsuba.client.anticheat.AntiCheats;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Predicate;

/**
 * What the debug HUD shows, collected whether or not it is on screen.
 *
 * <p>Three streams, each kept small and bounded:
 *
 * <ul>
 *   <li><b>Packets</b> &mdash; every packet as {@code Core.network()} described it,
 *       the last {@value #FEED_CAPACITY} in a ring for the live feed, and a rate
 *       per packet type for the busiest list. Nothing here knows a packet class:
 *       the adapter's describer has already turned each into a name and a kind.</li>
 *   <li><b>Movement</b> &mdash; the player's speed, measured from how far they
 *       actually moved each tick rather than from the velocity the client holds,
 *       which is not the same thing while a module is shaping motion.</li>
 *   <li><b>Alerts</b> &mdash; the moments worth reading back: rubberbands, lag
 *       spikes, anticheat verdicts, knockback, servers joined. Each is an event
 *       Core or the adapter already posts; this only words it.</li>
 * </ul>
 *
 * <p>Collecting all the time is what makes the HUD useful: switching it on after
 * a rubberband still shows the rubberband. The cost is a counter and a ring write
 * per packet.
 *
 * <h2>Threading</h2>
 * Packets arrive on the network thread, everything else on the game thread, and
 * the HUD reads on the render thread. Every collection is behind {@link #lock}
 * and every read returns a copy.
 */
public final class DebugMonitor {

    /** Packets kept for the live feed. */
    public static final int FEED_CAPACITY = 256;

    /** Alerts kept for the log. */
    public static final int ALERT_CAPACITY = 128;

    /**
     * A server teleport no further than this is a setback; further is a teleport
     * proper. A setback returns the player to where the server last accepted them,
     * which is never more than a few ticks of movement away, while a command or a
     * portal moves them anywhere.
     */
    public static final double RUBBERBAND_MAX_DISTANCE = 16d;

    private static DebugMonitor instance;

    private final Object lock = new Object();
    private final CircularQueue<PacketSample> packets = CircularQueue.of(FEED_CAPACITY);
    private final Map<String, TypeRate> rates = new HashMap<>();
    private final CircularQueue<DebugAlert> alerts = CircularQueue.of(ALERT_CAPACITY);

    // Game thread only.
    private Vec3 lastPosition;
    private double horizontalSpeed;
    private double verticalSpeed;

    private volatile int rubberbands;
    private volatile double lastRubberbandDistance;

    private DebugMonitor() {
    }

    /** Starts collecting. Call once, at boot. */
    public static void install() {
        DebugMonitor monitor = new DebugMonitor();
        Core.bus().subscribe(monitor);
        Core.network().addListener(monitor.new Traffic());
        instance = monitor;
    }

    /** @return the running monitor, or null before {@link #install()} */
    public static DebugMonitor get() {
        return instance;
    }

    // ----------------------------------------------------------------- reads

    /**
     * @param max     at most this many
     * @param keep    which packets to include
     * @return the newest packets that pass {@code keep}, newest first
     */
    public List<PacketSample> recentPackets(int max, Predicate<PacketSample> keep) {
        List<PacketSample> result = new ArrayList<>(max);
        synchronized (lock) {
            for (int i = packets.size() - 1; i >= 0 && result.size() < max; i--) {
                PacketSample sample = packets.get(i);
                if (keep.test(sample)) {
                    result.add(sample);
                }
            }
        }
        return result;
    }

    /** @return the busiest packet types over the last second that pass {@code keep}, busiest first */
    public List<PacketRate> busiestPackets(int max, Predicate<PacketRate> keep) {
        long now = Core.network().nanoTime();
        List<PacketRate> result = new ArrayList<>();
        synchronized (lock) {
            for (TypeRate type : rates.values()) {
                double perSecond = type.meter.rate(now);
                if (perSecond > 0d) {
                    PacketRate rate = new PacketRate(type.name, type.kind, type.inbound, perSecond);
                    if (keep.test(rate)) {
                        result.add(rate);
                    }
                }
            }
        }
        result.sort(Comparator.comparingDouble(PacketRate::perSecond).reversed());
        return result.size() > max ? result.subList(0, max) : result;
    }

    /** @return the newest alerts no older than {@code maxAgeMillis} that pass {@code keep}, newest first */
    public List<DebugAlert> recentAlerts(int max, long maxAgeMillis, Predicate<DebugAlert> keep) {
        long now = System.currentTimeMillis();
        List<DebugAlert> result = new ArrayList<>(max);
        synchronized (lock) {
            for (int i = alerts.size() - 1; i >= 0 && result.size() < max; i--) {
                DebugAlert alert = alerts.get(i);
                if (now - alert.getMillis() > maxAgeMillis) {
                    break;
                }
                if (keep.test(alert)) {
                    result.add(alert);
                }
            }
        }
        return result;
    }

    /** @return blocks a second across the ground, measured from the last tick's movement */
    public double getHorizontalSpeed() {
        return horizontalSpeed;
    }

    /** @return blocks a second upwards, negative falling */
    public double getVerticalSpeed() {
        return verticalSpeed;
    }

    /** @return setbacks since joining this world */
    public int getRubberbands() {
        return rubberbands;
    }

    /** @return how far the last setback pulled, in blocks, or 0 if there has been none */
    public double getLastRubberbandDistance() {
        return lastRubberbandDistance;
    }

    /** Adds a line to the log. Safe from any thread. */
    public void alert(AlertType type, String message) {
        synchronized (lock) {
            alerts.add(new DebugAlert(type, message, System.currentTimeMillis()));
        }
    }

    // ------------------------------------------------------------- movement

    @Subscribe(stage = Stage.POST)
    private void onTick(TickEvent event) {
        if (!Players.present()) {
            lastPosition = null;
            horizontalSpeed = 0d;
            verticalSpeed = 0d;
            return;
        }
        Vec3 position = Players.position();
        if (lastPosition != null) {
            double dx = position.getX() - lastPosition.getX();
            double dz = position.getZ() - lastPosition.getZ();
            // Twenty ticks a second is the client's own rate, whatever the server's.
            horizontalSpeed = Math.sqrt(dx * dx + dz * dz) * 20d;
            verticalSpeed = (position.getY() - lastPosition.getY()) * 20d;
        }
        lastPosition = position;
    }

    @Subscribe
    private void onWorld(WorldEvent event) {
        lastPosition = null;
        rubberbands = 0;
        lastRubberbandDistance = 0d;
    }

    // --------------------------------------------------------------- alerts

    @Subscribe
    private void onTeleport(ServerTeleportEvent event) {
        // The landing point is the new baseline, so the next tick does not read
        // the teleport itself as a burst of speed.
        lastPosition = event.getTo();
        if (event.isSpawn()) {
            return;
        }
        double distance = event.distance();
        if (distance <= RUBBERBAND_MAX_DISTANCE) {
            rubberbands++;
            lastRubberbandDistance = distance;
            alert(AlertType.RUBBERBAND, String.format(Locale.ROOT, "Pulled back %.2f blocks (#%d)", distance, rubberbands));
        } else {
            alert(AlertType.TELEPORT, String.format(Locale.ROOT, "Moved %.0f blocks", distance));
        }
    }

    @Subscribe
    private void onLagSpike(LagSpikeEvent event) {
        alert(AlertType.LAG, event.isStarted()
                ? "Server not responding"
                : String.format(Locale.ROOT, "Server back after %.1fs", event.getMillis() / 1000d));
    }

    @Subscribe
    private void onAntiCheat(AntiCheatChangeEvent event) {
        AntiCheats.verdictChanged(event).ifPresent(detection -> alert(AlertType.ANTICHEAT,
                detection.getName() + " (" + detection.getConfidence().name().toLowerCase(Locale.ROOT) + ")"));
    }

    @Subscribe
    private void onServer(ServerChangeEvent event) {
        if (event.isJoin()) {
            alert(AlertType.SERVER, event.getCurrent().isSingleplayer()
                    ? "Singleplayer"
                    : "Joined " + event.getCurrent().getHost());
        }
        if (event.isBrandChanged() && event.getCurrent().isMultiplayer()) {
            ServerBrand brand = event.getCurrent().getBrand();
            alert(AlertType.SERVER, "Brand \"" + brand.getRaw() + "\""
                    + (brand.getSoftware().isKnown() ? " = " + brand.getSoftware().getName() : ""));
        }
    }

    /**
     * Last, and even when cancelled, so the log shows what the client actually
     * did with the push after every module had its say.
     */
    @Subscribe(priority = Priority.LOWEST, receiveCancelled = true)
    private void onKnockback(KnockbackEvent event) {
        Vec3 push = event.getVelocity();
        double horizontal = Math.sqrt(push.getX() * push.getX() + push.getZ() * push.getZ());
        String taken = event.isCancelled() ? " (ignored)" : event.isModified() ? " (modified)" : "";
        String source = event.isExplosion() ? "Explosion " : "";
        alert(AlertType.KNOCKBACK, String.format(Locale.ROOT, "%s%.2f h, %.2f v%s",
                source, horizontal, push.getY(), taken));
    }

    // -------------------------------------------------------------- packets

    private void record(PacketDescription packet, boolean inbound, long nanos) {
        String key = (inbound ? "in:" : "out:") + packet.getType();
        synchronized (lock) {
            packets.add(new PacketSample(packet.getType(), packet.getKind(), inbound, nanos));
            rates.computeIfAbsent(key, ignored -> new TypeRate(packet.getType(), packet.getKind(), inbound))
                    .meter.record(nanos);
        }
    }

    /** Packets, as {@code Core.network()} hands them over: once each, described. */
    private final class Traffic implements PacketListener {

        @Override
        public void onInbound(PacketDescription packet, long nanos) {
            record(packet, true, nanos);
        }

        @Override
        public void onOutbound(PacketDescription packet, long nanos) {
            record(packet, false, nanos);
        }
    }

    /** One packet type's rate. Guarded by {@link #lock}, since a meter is not thread-safe. */
    private static final class TypeRate {

        private final String name;
        private final PacketKind kind;
        private final boolean inbound;
        private final RateMeter meter = RateMeter.perSecond();

        private TypeRate(String name, PacketKind kind, boolean inbound) {
            this.name = name;
            this.kind = kind;
            this.inbound = inbound;
        }
    }

    /** A packet type and how often it arrived or left over the last second. */
    public record PacketRate(String name, PacketKind kind, boolean inbound, double perSecond) {
    }
}
