package dev.px.karatsuba.client.module.utility;

import dev.px.core.event.Stage;
import dev.px.core.event.Subscribe;
import dev.px.core.math.Stopwatch;
import dev.px.core.module.Module;
import dev.px.core.module.ModuleInfo;
import dev.px.core.setting.impl.BooleanSetting;
import dev.px.core.setting.impl.NumberSetting;
import dev.px.core.setting.impl.RangeSetting;
import dev.px.karatsuba.adapter.event.game.player.PlayerUpdateEvent;
import dev.px.karatsuba.adapter.game.player.Inputs;

import java.util.Random;

/**
 * Moves the player occasionally so an idle-kick never fires.
 *
 * <p>The worked example for <b>driving the game's own controls</b> rather than
 * writing positions. Holding {@code FORWARD} down makes the game walk the player,
 * which means real collision, real packets and a motion the server has no reason
 * to question. Setting a position instead would be a teleport, and a server that
 * kicks for idling is exactly the kind that also checks for those.
 *
 * <p>It is also the example for {@code Inputs} remembering what it forced. Every
 * path out of an action &mdash; the duration expiring, the player touching a key,
 * the module being switched off &mdash; ends in the same
 * {@link Inputs#releaseAll()}, and a control the player is genuinely holding is
 * left alone.
 *
 * <h2>Randomised, deliberately</h2>
 * A fixed interval and a fixed duration make a pattern, and a pattern is the
 * thing an anti-cheat looks for. Both are {@code RangeSetting}s, which give a
 * fresh value from the range each time rather than a constant &mdash; the same
 * mechanism a CPS setting uses.
 */
@ModuleInfo(name = "Anti AFK", description = "Moves occasionally so an idle kick never fires",
        category = "Utility")
public final class AntiAfkModule extends Module {

    private static final int MILLIS_PER_SECOND = 1_000;

    private final NumberSetting<Integer> idleDelay = integer("Idle Delay", 30, 5, 300)
            .describe("Seconds of no input before this starts acting");

    private final RangeSetting interval = range("Interval", 2d, 6d, 1d, 30d)
            .describe("Seconds between actions, picked fresh each time");

    private final RangeSetting duration = range("Duration", 200d, 800d, 100d, 2000d)
            .describe("How long an action is held, in milliseconds");

    private final BooleanSetting walk = bool("Walk", true);
    private final BooleanSetting jump = bool("Jump", true);
    private final BooleanSetting sneak = bool("Sneak", false);

    private final Random random = new Random();

    /** Reset by any real input; what {@link #idleDelay} is measured against. */
    private final Stopwatch idle = Stopwatch.started();

    /** Time until the next action may begin. Only consulted once idle. */
    private final Stopwatch sinceAction = Stopwatch.started();

    /** Running while an action is held; expired means there is nothing to release. */
    private final Stopwatch holding = Stopwatch.expired();

    /** How long the current action lasts. Rolled fresh when one starts. */
    private long holdMillis;

    @Override
    protected void onEnable() {
        idle.reset();
        sinceAction.reset();
        holdMillis = 0L;
    }

    @Override
    protected void onDisable() {
        stopActing();
    }

    /**
     * One tick of the state machine.
     *
     * <p>{@link Stage#POST} so the player's own update has already run: the
     * controls read here are the ones the game just acted on, rather than the
     * previous tick's.
     */
    @Subscribe(stage = Stage.POST)
    private void onPlayerUpdate(PlayerUpdateEvent event) {
        // A real keypress always wins, and ends any action immediately. Checked
        // before anything else, because a player who has come back deserves their
        // controls in the same tick rather than the next one.
        if (playerIsActive()) {
            idle.reset();
            stopActing();
            return;
        }
        if (acting()) {
            if (holding.elapsed(holdMillis)) {
                stopActing();
                sinceAction.reset();
            }
            return;
        }
        if (idle.elapsed(idleDelay.getInt() * (long) MILLIS_PER_SECOND)
                && sinceAction.elapsed(nextIntervalMillis())) {
            startActing();
        }
    }

    /**
     * @return whether the player is doing something themselves
     *
     * <p>Only meaningful because {@link Inputs#releaseAll()} has already run
     * whenever an action is not in flight: with a control forced, this would see
     * the module's own input and never time out.
     */
    private boolean playerIsActive() {
        return !acting() && Inputs.anyActivity();
    }

    private boolean acting() {
        return Inputs.isForcing();
    }

    /** Picks an action and holds it. */
    private void startActing() {
        holdMillis = Math.round(duration.randomValue());
        holding.reset();

        if (walk.isOn()) {
            Inputs.hold(randomDirection());
        }
        // A third of the time each, so an action is usually a plain walk and
        // occasionally something else. Both together is fine and looks like a hop.
        if (jump.isOn() && random.nextInt(3) == 0) {
            Inputs.hold(Inputs.Control.JUMP);
        }
        if (sneak.isOn() && random.nextInt(3) == 0) {
            Inputs.hold(Inputs.Control.SNEAK);
        }
    }

    /**
     * Ends whatever is being held.
     *
     * <p>Releasing is what makes {@link #acting()} false again, because that reads
     * the adapter's forced set rather than a flag of our own &mdash; one piece of
     * state instead of two that could disagree. Resetting the hold clock is
     * tidiness: nothing consults it until the next action starts.
     */
    private void stopActing() {
        Inputs.releaseAll();
        holding.reset();
    }

    private Inputs.Control randomDirection() {
        Inputs.Control[] directions = {
                Inputs.Control.FORWARD, Inputs.Control.BACK,
                Inputs.Control.LEFT, Inputs.Control.RIGHT
        };
        return directions[random.nextInt(directions.length)];
    }

    private long nextIntervalMillis() {
        return Math.round(interval.randomValue() * MILLIS_PER_SECOND);
    }

    @Override
    public String getDisplayInfo() {
        return acting() ? "Acting" : "Idle";
    }
}
