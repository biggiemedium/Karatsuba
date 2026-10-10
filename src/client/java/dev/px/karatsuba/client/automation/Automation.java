package dev.px.karatsuba.client.automation;

import dev.px.core.Core;
import dev.px.core.event.EventBus;
import dev.px.core.event.Priority;
import dev.px.core.event.Stage;
import dev.px.core.event.Subscription;
import dev.px.core.event.impl.Render3DEvent;
import dev.px.core.event.impl.TickEvent;
import dev.px.core.flow.FlowHandle;
import dev.px.core.flow.FlowService;
import dev.px.core.flow.Step;
import dev.px.core.movement.simulation.SimulationService;
import dev.px.core.service.Service;
import dev.px.karatsuba.adapter.game.player.Inputs;
import dev.px.karatsuba.client.automation.navigation.Navigation;
import dev.px.karatsuba.client.automation.task.Task;
import dev.px.karatsuba.client.automation.view.RouteView;
import dev.px.karatsuba.client.settings.ClientSettings;
import dev.px.karatsuba.client.settings.automation.AutomationSettings;

import java.util.ArrayList;
import java.util.List;

/**
 * Runs {@link Task}s: one at a time, as a flow on Core's flow engine, under the
 * client's {@link AutomationSettings}.
 *
 * <pre>{@code
 * Automation.get().run(MineTask.block(Vec3i.of(100, 64, -20)));
 * Automation.get().stop();
 * }</pre>
 *
 * <p>Core's {@code FlowService} can run any number of flows at once, and stays
 * reachable for flows of other kinds. This is the client's front for the one the
 * player asked for: the task behind the commands, the HUD element and, later, a
 * GUI section, so all of them agree on what is running.
 *
 * <ul>
 *   <li><b>One at a time.</b> Running a task cancels the one before.</li>
 *   <li><b>At the settings' priority</b>, kept in step with them every tick, so
 *       a module asking for the keys or the head higher still wins them.</li>
 *   <li><b>Yielding to the player</b>, when the settings say so: a movement key
 *       pressed while a task runs stops it.</li>
 *   <li><b>Reporting</b> when a task arrives, fails or is stopped, as a
 *       notification, if the settings say so.</li>
 * </ul>
 *
 * <p>Leaving a world pauses the task, as it pauses every flow, and joining one
 * resumes it. A restart forgets it.
 *
 * <p><b>Game thread only.</b>
 */
public final class Automation implements Service {

    private static Automation instance;

    private final EventBus bus;
    private final FlowService flows;
    private final AutomationSettings settings;
    private final Navigation navigation;
    private final RouteView routes;
    private final List<Subscription> subscriptions = new ArrayList<>();

    private Task task;
    private FlowHandle running;

    private Automation(EventBus bus, FlowService flows, SimulationService simulation, AutomationSettings settings) {
        this.bus = bus;
        this.flows = flows;
        this.settings = settings;
        this.navigation = new Navigation(simulation, settings);
        this.routes = new RouteView(navigation, settings);
    }

    /**
     * Registers automation with Core. After {@code ClientSettings.registerAll},
     * whose settings it runs under, and before {@code Core.start()}, which starts it.
     */
    public static void install(Core core) {
        instance = core.getServices().register(new Automation(core.getBus(), core.getFlowService(),
                core.getSimulationService(), ClientSettings.automation()));
    }

    /**
     * @return the running automation
     * @throws IllegalStateException before {@link #install}
     */
    public static Automation get() {
        if (instance == null) {
            throw new IllegalStateException("Automation is not installed yet;"
                    + " Automation.install(core) runs during boot, before Core.start()");
        }
        return instance;
    }

    @Override
    public String getName() {
        return "Automation";
    }

    @Override
    @SuppressWarnings("unchecked")
    public Class<? extends Service>[] dependsOn() {
        return new Class[]{FlowService.class};
    }

    @Override
    public void start() {
        // Ahead of the flows, which tick at HIGH: a task the player has taken over
        // is stopped before it files this tick's claims.
        subscriptions.add(bus.on(TickEvent.class, Priority.HIGHEST, event -> {
            if (event.getStage() == Stage.PRE) {
                beginTick();
            } else {
                endTick();
            }
        }));
        subscriptions.add(bus.on(Render3DEvent.class, event -> routes.draw()));
    }

    @Override
    public void stop() {
        for (Subscription subscription : subscriptions) {
            subscription.close();
        }
        subscriptions.clear();
        cancel();
    }

    // ------------------------------------------------------------- running

    /**
     * Runs {@code task}, cancelling whatever was running.
     *
     * @return the flow it runs as
     */
    public FlowHandle run(Task task) {
        cancel();
        Step step = task.build(navigation);
        this.task = task;
        this.running = flows.start(task.getName() + " " + task.describe(), step, settings.priority());
        return running;
    }

    /**
     * Stops the running task, saying so.
     *
     * @return whether there was one to stop
     */
    public boolean stop(String why) {
        Task stopped = task;
        if (!cancel()) {
            return false;
        }
        report(stopped, Ending.STOPPED, why);
        return true;
    }

    /** @return whether there was a running task to pause */
    public boolean pause() {
        if (!isActive() || running.getState() == FlowHandle.State.PAUSED) {
            return false;
        }
        running.pause();
        return true;
    }

    /** @return whether there was a task paused by hand to resume */
    public boolean resume() {
        if (!isActive() || running.getState() != FlowHandle.State.PAUSED) {
            return false;
        }
        running.resume();
        return true;
    }

    // ------------------------------------------------------------- reading

    /** @return whether a task is running or paused */
    public boolean isActive() {
        return running != null && !running.isDone();
    }

    /** @return the task running or paused, or null */
    public Task getTask() {
        return isActive() ? task : null;
    }

    /** @return the flow the task runs as, or null; for its live view and state */
    public FlowHandle getFlow() {
        return isActive() ? running : null;
    }

    /** @return where tasks get their travel steps, for a task or anything drawing routes */
    public Navigation getNavigation() {
        return navigation;
    }

    // ---------------------------------------------------------------- tick

    private void beginTick() {
        navigation.newTick();
        if (!isActive()) {
            return;
        }
        running.setPriority(settings.priority());
        if (settings.yieldToPlayer() && running.getState() == FlowHandle.State.RUNNING
                && Inputs.playerMoving()) {
            stop("you took the controls");
        }
    }

    /** Hears how a task ended, once, after the flows have ticked. */
    private void endTick() {
        if (running == null || !running.isDone()) {
            return;
        }
        FlowHandle ended = running;
        Task finished = task;
        running = null;
        task = null;
        switch (ended.getState()) {
            case DONE -> report(finished, Ending.DONE, null);
            case FAILED -> report(finished, Ending.FAILED, ended.getReason());
            default -> {
                // Cancelled: whoever cancelled it has said so.
            }
        }
    }

    /** @return whether there was a task to cancel; says nothing */
    private boolean cancel() {
        FlowHandle cancelled = running;
        running = null;
        task = null;
        if (cancelled == null || cancelled.isDone()) {
            return false;
        }
        cancelled.cancel();
        return true;
    }

    private void report(Task about, Ending ending, String why) {
        if (about == null || !settings.announce()) {
            return;
        }
        String title = about.getName() + " " + ending.word;
        String message = about.describe() + (why == null ? "" : ": " + why);
        switch (ending) {
            case DONE -> Core.notifications().success(title, message);
            case FAILED -> Core.notifications().error(title, message);
            case STOPPED -> Core.notifications().info(title, message);
        }
    }

    /** How a task ended, as far as the player is told. */
    private enum Ending {
        DONE("done"),
        FAILED("failed"),
        STOPPED("stopped");

        private final String word;

        Ending(String word) {
            this.word = word;
        }
    }
}
