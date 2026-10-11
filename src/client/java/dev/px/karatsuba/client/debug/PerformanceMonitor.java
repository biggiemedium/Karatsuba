package dev.px.karatsuba.client.debug;

import dev.px.core.Core;
import dev.px.core.event.EventBus;
import dev.px.core.event.Priority;
import dev.px.core.event.Stage;
import dev.px.core.event.impl.Render2DEvent;
import dev.px.core.event.impl.Render3DEvent;
import dev.px.core.event.impl.TickEvent;
import dev.px.karatsuba.client.automation.Automation;
import dev.px.karatsuba.client.automation.navigation.Replan;
import dev.px.karatsuba.client.automation.navigation.SegmentPlanner;
import dev.px.karatsuba.client.automation.navigation.Trip;
import dev.px.karatsuba.client.automation.navigation.TripStats;
import dev.px.karatsuba.client.automation.navigation.Navigation;
import dev.px.navigation.plan.PlanStats;

import java.util.List;
import java.util.Locale;

/**
 * Where the client's time goes: the numbers behind the Debug element's
 * Performance section, and an alert when something spikes.
 *
 * <p>Timed from the edges of the event bus, so nothing it measures has to know:
 *
 * <ul>
 *   <li><b>Client tick</b>: from the first subscriber of each tick stage to the
 *       last, PRE and POST added. Every module, service and flow on the tick, not
 *       the game's own work.</li>
 *   <li><b>Flows</b>: the band just around Core's flow engine (tick PRE, at
 *       {@code Priority.HIGH}): every task, reflex and travel step, and the plans
 *       they make. Anything else subscribed at exactly {@code HIGH} is in it too.</li>
 *   <li><b>Frame</b>: the client's 3D and 2D drawing, from the first subscriber
 *       of each to the last.</li>
 *   <li><b>Plans</b>: each local plan made on the game thread, by its own timing
 *       ({@link PlanStats#getMicros}), noticed after the flows tick; two in one
 *       tick count as the second. With background planning these should be
 *       none, and the worker's plans are counted apart: they cost the game nothing. Each
 *       long-range plan's, from {@link Navigation#getCoarseStats}.</li>
 * </ul>
 *
 * <p>Averages and peaks are over the last second. Game thread only.
 */
public final class PerformanceMonitor {

    /** A tick whose client work takes this long, in milliseconds, is reported. */
    public static final double TICK_SPIKE_MS = 25d;

    /** A plan taking this long, in milliseconds, is reported. */
    public static final double PLAN_SPIKE_MS = 15d;

    /** Ticks between two spike alerts, so a bad stretch is one line, not twenty. */
    private static final int ALERT_GAP = 20;

    private static final int TICKS = 20;
    private static final int FRAMES = 60;

    private static PerformanceMonitor instance;

    private final Window tickMs = new Window(TICKS);
    private final Window flowMs = new Window(TICKS);
    private final Window frameMs = new Window(FRAMES);
    private final Window localPlanMs = new Window(TICKS);
    private final Window localPlanCount = new Window(TICKS);
    private final Window coarsePlanCount = new Window(TICKS);
    private final Window workerPlanCount = new Window(TICKS);

    private long stageStart;
    private long tickNanos;
    private long flowStart;
    private long flowNanos;
    private long drawStart;
    private long frameNanos;

    private PlanStats lastLocal;
    private Replan lastLocalReason;
    private int coarsePlansSeen;
    private int workerPlansSeen;
    private PlanStats lastWorker;
    private Navigation.CoarseStats lastCoarse;

    private long ticks;
    private long lastAlert = Long.MIN_VALUE / 2;

    private PerformanceMonitor() {
    }

    /** Starts measuring. Call once, at boot, after {@code Automation.install}. */
    public static void install() {
        PerformanceMonitor monitor = new PerformanceMonitor();
        monitor.subscribe(Core.bus());
        instance = monitor;
    }

    /** @return the running monitor, or null before {@link #install()} */
    public static PerformanceMonitor get() {
        return instance;
    }

    private void subscribe(EventBus bus) {
        bus.on(TickEvent.class, Integer.MAX_VALUE, event -> stageStart = System.nanoTime());
        bus.on(TickEvent.class, Integer.MIN_VALUE, event -> {
            tickNanos += System.nanoTime() - stageStart;
            if (event.getStage() == Stage.POST) {
                endTick();
            }
        });
        bus.on(TickEvent.class, Priority.HIGH + 1, event -> {
            if (event.getStage() == Stage.PRE) {
                flowStart = System.nanoTime();
            }
        });
        bus.on(TickEvent.class, Priority.HIGH - 1, event -> {
            if (event.getStage() == Stage.PRE) {
                flowNanos = System.nanoTime() - flowStart;
                notePlans();
            }
        });
        bus.on(Render3DEvent.class, Integer.MAX_VALUE, event -> drawStart = System.nanoTime());
        bus.on(Render3DEvent.class, Integer.MIN_VALUE, event -> frameNanos += System.nanoTime() - drawStart);
        bus.on(Render2DEvent.class, Integer.MAX_VALUE, event -> drawStart = System.nanoTime());
        bus.on(Render2DEvent.class, Integer.MIN_VALUE, event -> {
            frameNanos += System.nanoTime() - drawStart;
            frameMs.add(frameNanos / 1e6d);
            frameNanos = 0L;
        });
    }

    // ---------------------------------------------------------------- tick

    /** After the flows: any plan made this tick, local or long range. */
    private void notePlans() {
        Navigation navigation = Automation.get().getNavigation();
        PlanStats local = navigation.getPlanner().getLastStats();
        boolean planned = local != null && local != lastLocal && lastLocal != null;
        if (local != lastLocal) {
            lastLocal = local;
        }
        if (planned) {
            lastLocalReason = lastReplan();
            double ms = local.getMicros() / 1000d;
            localPlanMs.add(ms);
            localPlanCount.add(1d);
            if (ms >= PLAN_SPIKE_MS) {
                alert(String.format(Locale.ROOT, "Plan %.1f ms: %s, %d states%s, %,d ticks simulated%s", ms,
                        local.getOutcome(), local.getExpanded(), local.isBudgetSpent() ? " (budget)" : "",
                        local.getSimulatedTicks(), lastLocalReason == null ? "" : ", " + lower(lastLocalReason)));
            }
        } else {
            localPlanMs.add(0d);
            localPlanCount.add(0d);
        }

        SegmentPlanner background = navigation.getSegments();
        int finished = background.getPlans() - workerPlansSeen;
        workerPlansSeen = background.getPlans();
        workerPlanCount.add(Math.max(0, finished));
        if (finished > 0) {
            lastWorker = background.getLastStats();
        }

        Navigation.CoarseStats coarse = navigation.getCoarseStats();
        int fresh = coarse.plans() - coarsePlansSeen;
        coarsePlansSeen = coarse.plans();
        coarsePlanCount.add(Math.max(0, fresh));
        if (fresh > 0) {
            lastCoarse = coarse;
        }
    }

    private void endTick() {
        ticks++;
        double ms = tickNanos / 1e6d;
        tickMs.add(ms);
        flowMs.add(flowNanos / 1e6d);
        if (ms >= TICK_SPIKE_MS) {
            alert(String.format(Locale.ROOT, "Tick %.1f ms (flows %.1f)", ms, flowNanos / 1e6d));
        }
        tickNanos = 0L;
        flowNanos = 0L;
    }

    private void alert(String message) {
        if (ticks - lastAlert < ALERT_GAP) {
            return;
        }
        lastAlert = ticks;
        DebugMonitor monitor = DebugMonitor.get();
        if (monitor != null) {
            monitor.alert(AlertType.PERFORMANCE, message);
        }
    }

    // --------------------------------------------------------------- reads

    /** @return the client's work per tick over the last second, in milliseconds */
    public Window getTick() {
        return tickMs;
    }

    /** @return the flow engine's work per tick over the last second, in milliseconds */
    public Window getFlows() {
        return flowMs;
    }

    /** @return the client's drawing per frame over the last {@value #FRAMES} frames, in milliseconds */
    public Window getFrame() {
        return frameMs;
    }

    /** @return local planning per tick over the last second, in milliseconds */
    public Window getLocalPlanTime() {
        return localPlanMs;
    }

    /** @return local plans in the last second */
    public int getLocalPlans() {
        return (int) localPlanCount.sum();
    }

    /** @return plans finished on the background planner's worker in the last second */
    public int getWorkerPlans() {
        return (int) workerPlanCount.sum();
    }

    /** @return the last plan finished on the worker, or null before one */
    public PlanStats getLastWorker() {
        return lastWorker;
    }

    /** @return the segment planner, for its lead */
    public SegmentPlanner getSegments() {
        return Automation.get().getNavigation().getSegments();
    }

    /** @return long-range plans in the last second */
    public int getCoarsePlans() {
        return (int) coarsePlanCount.sum();
    }

    /** @return the last local plan's numbers, or null before one */
    public PlanStats getLastLocal() {
        return lastLocal;
    }

    /** @return why the last local plan was made, or null */
    public Replan getLastLocalReason() {
        return lastLocalReason;
    }

    /** @return the last long-range plan's numbers, or null before one */
    public Navigation.CoarseStats getLastCoarse() {
        return lastCoarse;
    }

    /** @return the trip being walked's stats, or null when none is */
    public TripStats getTripStats() {
        List<Trip> trips = Automation.get().getNavigation().getActiveTrips();
        return trips.isEmpty() ? null : trips.get(0).getStats();
    }

    private Replan lastReplan() {
        TripStats stats = getTripStats();
        return stats == null ? null : stats.getLastReplan();
    }

    private static String lower(Replan reason) {
        return reason.name().toLowerCase(Locale.ROOT).replace('_', ' ');
    }

    /** The last few samples: their average and peak. */
    public static final class Window {

        private final double[] samples;
        private int next;
        private int count;

        Window(int size) {
            this.samples = new double[size];
        }

        void add(double sample) {
            samples[next] = sample;
            next = (next + 1) % samples.length;
            count = Math.min(count + 1, samples.length);
        }

        public double average() {
            return count == 0 ? 0d : sum() / count;
        }

        public double max() {
            double max = 0d;
            for (int i = 0; i < count; i++) {
                max = Math.max(max, samples[i]);
            }
            return max;
        }

        public double sum() {
            double sum = 0d;
            for (int i = 0; i < count; i++) {
                sum += samples[i];
            }
            return sum;
        }
    }
}
