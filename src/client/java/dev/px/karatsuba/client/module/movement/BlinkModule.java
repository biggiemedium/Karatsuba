package dev.px.karatsuba.client.module.movement;

import dev.px.combat.hole.Hole;
import dev.px.core.Core;
import dev.px.core.entity.Tracked;
import dev.px.core.event.Priority;
import dev.px.core.event.Stage;
import dev.px.core.event.Subscribe;
import dev.px.core.event.impl.Render3DEvent;
import dev.px.core.event.impl.TickEvent;
import dev.px.core.event.impl.WorldEvent;
import dev.px.core.flow.Flow;
import dev.px.core.flow.FlowHandle;
import dev.px.core.flow.Step;
import dev.px.core.math.Vec3;
import dev.px.core.math.Vec3i;
import dev.px.core.module.ModuleInfo;
import dev.px.core.movement.rotation.RotationPriority;
import dev.px.core.movement.simulation.MotionState;
import dev.px.core.render.Color;
import dev.px.core.render.Render;
import dev.px.core.setting.impl.BooleanSetting;
import dev.px.core.setting.impl.ColorSetting;
import dev.px.core.setting.impl.EnumSetting;
import dev.px.core.setting.impl.GroupSetting;
import dev.px.core.setting.impl.NumberSetting;
import dev.px.karatsuba.adapter.combat.Combat;
import dev.px.karatsuba.adapter.combat.CombatKit;
import dev.px.karatsuba.adapter.event.game.combat.TotemPopEvent;
import dev.px.karatsuba.adapter.event.game.network.MovementPacketEvent;
import dev.px.karatsuba.adapter.event.game.player.ServerTeleportEvent;
import dev.px.karatsuba.adapter.game.player.PlayerList;
import dev.px.karatsuba.adapter.game.player.Players;
import dev.px.karatsuba.adapter.network.PacketHold;
import dev.px.karatsuba.client.automation.step.HoldPackets;
import dev.px.karatsuba.client.module.ClientModule;
import dev.px.karatsuba.client.module.movement.blink.BlinkTrail;
import dev.px.karatsuba.client.module.movement.blink.BurstBudget;
import dev.px.karatsuba.client.notify.Notices;
import dev.px.karatsuba.client.notify.Source;
import dev.px.karatsuba.client.render.ThemeColors;

/**
 * Holds your packets back while on, then lets them all go at once: to the server
 * you stood still, then arrived.
 *
 * <p>The hold is a {@link HoldPackets} step run as a flow, {@code until} one of the
 * Auto Toggle events, and the module switches itself off when the flow ends. The
 * hold is Blink's own ({@link PacketHold#create}), so Elytra Fly letting go of its
 * shared hold never lets go of Blink's.
 *
 * <h2>Grim</h2>
 * Always everything, in order: Grim reads movement against its tick-end packet
 * (TickTimer), against the input packet (Simulation) and against pong replies
 * (Timer, Post), so holding only some of them is a flag whichever are left out.
 * Held together it reads as latency, which Grim absorbs &mdash; up to a point.
 * TimerLimit stops counting a player's clock as further behind than its
 * {@code ping-abuse-limit-threshold} (1000 ms by default) plus TimerA's 120 ms
 * drift, so every tick held past that, less the ping already there, flags with a
 * setback when it lands. Grim Safe keeps each hold inside that, and the blink
 * goes on as a run of such holds, each let go as a burst: until it is turned off
 * ({@link Mode#CONTINUOUS}) or until Max Timeout ({@link Mode#FIXED}).
 * The rules are in {@code anticheat/Grim.md}.
 *
 * <h2>Servers set up differently</h2>
 * Grim Safe's budget assumes the server's threshold is Ping Limit and the ping is
 * what the tab list says. A server with a lower threshold, or a connection that
 * jitters, sets back every burst that runs past what it really allows.
 * {@code Adaptive} learns that instead ({@link BurstBudget}): bursts start short and
 * grow while they land clean, and a setback within a round trip of a burst landing
 * cuts the length back and caps it there, for that server, for the session. A
 * burst also goes out early once you are {@link #BURST_DISTANCE} blocks from where
 * the server last saw you: the vanilla server sets back a batch that moves you
 * about ten blocks in one of its ticks, whatever the anticheat.
 *
 * <h2>Other modules while it holds</h2>
 * Whatever they send joins the hold in order and reaches the server with the
 * rest, as if late. The action and dig services hold off only when a hold takes
 * some kinds and not others ({@link PacketHold#isSplitting()}), which Blink never
 * does.
 *
 * @author James
 */
@ModuleInfo(name = "Blink", description = "Holds your packets back, then sends them all at once", category = "Movement")
public class BlinkModule extends ClientModule {

    /** The player's hitbox width, for whether they are wholly inside a hole. */
    private static final double PLAYER_WIDTH = 0.6d;

    /** How far out a crystal is weighed for the Explosion toggle; beyond it the falloff leaves nothing. */
    private static final double CRYSTAL_RANGE = 12d;

    /** Grim's TimerA drift: how far behind the clock it lets a balance fall before catching it up. */
    private static final int TIMER_DRIFT_MS = 120;

    /** Room left under Grim's limit for uneven client ticks and a ping read a moment late. */
    private static final int MARGIN_MS = 150;

    private static final int TICK_MS = 50;

    /** Blocks from where the server last saw you at which a burst goes out early: inside vanilla's ten. */
    private static final double BURST_DISTANCE = 8d;

    /** Ticks past the round trip a setback may take to come back and still be blamed on the last burst. */
    private static final int JUDGE_SLACK = 10;

    /** How long a blink goes on. Either way it is a run of bursts, each a hold let go in one go. */
    public enum Mode {
        /** Burst after burst until the module is turned off, or an Auto Toggle event. */
        CONTINUOUS,
        /** Burst after burst until Max Timeout, then off. */
        FIXED
    }

    /** Blink's own hold, of everything. */
    private final PacketHold.Hold hold = PacketHold.create("Blink");

    private final BlinkTrail trail = new BlinkTrail();

    // ------------------------------------------------------------- settings

    private final EnumSetting<Mode> mode = enumOf("Mode", Mode.FIXED)
            .describe("Continuous: burst after burst until you turn it off. Fixed: bursts until Max Timeout, then off");

    private final BooleanSetting grimSafe = bool("Grim Safe", true)
            .describe("Burst before Grim's TimerLimit would flag: about a second, less your ping. The server sees you at each burst");

    private final NumberSetting<Integer> pingLimit = integer("Ping Limit", 1000, 200, 3000)
            .describe("The server's TimerLimit ping-abuse-limit-threshold, in milliseconds. 1000 unless changed")
            .visibleWhen(grimSafe::isOn);

    private final NumberSetting<Integer> maxTimeout = integer("Max Timeout", 5, 3, 10)
            .describe("Fixed: seconds the blink lasts in all. Continuous without Grim Safe: seconds each burst holds")
            .visibleWhen(() -> mode.get() == Mode.FIXED || !grimSafe.isOn());

    private final BooleanSetting adaptive = bool("Adaptive", true)
            .describe("Learn how long this server lets a burst hold: start short, grow while bursts land, cut back after a setback");

    private final GroupSetting grim = group("Grim", true)
            .containing(grimSafe, pingLimit, adaptive);

    private final BooleanSetting holeReached = bool("Hole Reached", true)
            .describe("Once you are safely in a hole other than the one you started in");

    private final BooleanSetting totemPop = bool("Totem Pop", true)
            .describe("When one of your totems pops");

    private final BooleanSetting explosion = bool("Explosion", true)
            .describe("When a crystal near you would deal at least Explosion Damage, or leave you within a heart");

    private final NumberSetting<Double> explosionDamage = decimal("Explosion Damage", 8d, 1d, 36d)
            .describe("Damage, after armour, that counts as critical")
            .visibleWhen(explosion::isOn);

    private final BooleanSetting setback = bool("Setback", true)
            .describe("When the server puts you back");

    private final BooleanSetting distance = bool("Distance", false)
            .describe("Once you are this far from where you started");

    private final NumberSetting<Double> maxDistance = decimal("Max Distance", 4d, 1d, 10d)
            .describe("Blocks from where you started")
            .visibleWhen(distance::isOn);

    private final GroupSetting autoToggle = group("Auto Toggle", true)
            .containing(holeReached, totemPop, explosion, explosionDamage, setback, distance, maxDistance);

    private final BooleanSetting showTrail = bool("Trail", true)
            .describe("A line through every position held back, up to you");

    private final BooleanSetting serverBox = bool("Server Box", true)
            .describe("Your hitbox where the server last saw you");

    private final BooleanSetting themeColours = bool("Theme Colours", false)
            .describe("Draw in the client theme's colours instead of the ones set here");

    private final ColorSetting trailColor = color("Trail Colour", Color.of(120, 200, 255, 255))
            .visibleWhen(() -> !themeColours.isOn());

    private final ColorSetting serverColor = color("Server Colour", Color.of(255, 255, 255, 200))
            .visibleWhen(() -> serverBox.isOn() && !themeColours.isOn());

    private final NumberSetting<Float> thickness = number("Thickness", 1.5f, 1f, 5f).step(0.1f);

    private final BooleanSetting animations = bool("Animations", true)
            .describe("The trail fades out when sent and the server box glides; off, they snap");

    private final BooleanSetting throughWalls = bool("Through Walls", true);

    private final GroupSetting appearance = group("Appearance", false)
            .containing(showTrail, serverBox, themeColours, trailColor, serverColor, thickness, animations,
                    throughWalls);

    // ---------------------------------------------------------------- state

    private FlowHandle flow;
    private long startTick;

    /** What this server has shown it lets a burst hold. */
    private final BurstBudget budget = new BurstBudget();

    /** The burst under way: the tick it began, and where the server last saw you. */
    private long burstStart;
    private Vec3 burstFrom;

    /** The burst last let go, until it is judged: when, and how many ticks it held. */
    private long lastBurstTick;
    private int lastBurstHeld;
    private boolean judging;

    /** The hole the blink started in, or null; reaching it again is not arriving. */
    private Hole startHole;
    private Vec3 startPosition;
    private boolean setbackSeen;
    private boolean popped;

    /** The player's feet at the last two tick ends, to draw the trail's head between them. */
    private Vec3 lastTickPosition;
    private Vec3 tickPosition;
    private long lastFrameNanos;

    @Override
    protected void onEnable() {
        flow = null;
        setbackSeen = false;
        popped = false;
        trail.clear();
        if (!Players.present()) {
            return; // switched off again at the end of the tick
        }
        startTick = Core.flows().getTick();
        startPosition = Players.position();
        startHole = holeAtFeet();
        tickPosition = lastTickPosition = startPosition;
        trail.sent(startPosition);

        // On now rather than when the flow first ticks, so nothing sent before then slips out.
        hold.everything().lasting(holdLimit()).start();
        Step once = Flow.sequence(
                Flow.action("budget", c -> {
                    hold.lasting(holdLimit());
                    burstStart = Core.flows().getTick();
                    burstFrom = Players.position();
                }),
                HoldPackets.with(hold).until(c -> tooFarForOneBurst()),
                Flow.action("delivered", c -> {
                    burstDelivered();
                    trail.deliver();
                }));
        flow = Core.flows().start("Blink", Flow.loop(once).until(c -> letGo()).named("blink"), RotationPriority.NORMAL);
    }

    @Override
    protected void onDisable() {
        if (flow != null && !flow.isDone()) {
            flow.cancel();
        }
        boolean wasHolding = hold.isActive();
        flow = null;
        hold.release(); // let out all packets once done
        if (wasHolding) {
            burstDelivered();
        }
        trail.deliver();
    }

    /** End of the tick: the flow ended (an event, the timeout, or the hold let go elsewhere), so Blink does too. */
    @Subscribe(stage = Stage.POST)
    private void onTickEnd(TickEvent event) {
        judgeQuietly();
        lastTickPosition = tickPosition;
        tickPosition = Players.present() ? Players.position() : tickPosition;
        if (flow == null || flow.isDone()) {
            disable();
        }
    }

    /** Last, so it sees the position as it will be sent. Cancelled packets never reach it. */
    @Subscribe(priority = Priority.LOWEST)
    private void onMovementPacket(MovementPacketEvent event) {
        if (!event.hasPosition()) {
            return;
        }
        if (hold.isActive()) {
            trail.held(event.getPosition());
        } else {
            trail.sent(event.getPosition());
        }
    }

    /** Also while off: the setback for the last burst can come back after Blink has ended. */
    @Subscribe(ignoreListening = true)
    private void onTeleport(ServerTeleportEvent event) {
        if (event.isSpawn()) {
            return;
        }
        if (isListening()) {
            setbackSeen = true;
        }
        if (judging && Core.flows().getTick() - lastBurstTick <= judgeWindow()) {
            judging = false;
            if (adaptive.isOn()) {
                budget.setBack(lastBurstHeld);
                Notices.info(Source.MODULE_MESSAGES, "Blink", "Set back after a " + lastBurstHeld * TICK_MS
                        + " ms burst; bursts here now hold " + budget.ticks() * TICK_MS + " ms");
            }
        }
    }

    /** Also while off: what is learned is kept per server, so it must follow the server. */
    @Subscribe(ignoreListening = true)
    private void onServer(WorldEvent event) {
        if (!event.isUnloaded()) {
            budget.server(event.getServerAddress());
            judging = false;
        }
    }

    /** Also while off, to judge the burst let go as Blink ended. */
    @Subscribe(stage = Stage.POST, ignoreListening = true)
    private void onTickEndAlways(TickEvent event) {
        if (!isListening()) {
            judgeQuietly();
        }
    }

    @Subscribe
    private void onTotemPop(TotemPopEvent event) {
        if (event.isSelf()) {
            popped = true;
        }
    }

    @Subscribe
    private void onWorld(WorldEvent event) {
        if (event.isUnloaded()) {
            trail.clear();
            disable();
        }
    }

    /** Also while off, so the last delivery finishes drawing into the player after the blink ends. */
    @Subscribe(ignoreListening = true)
    private void onRender3D(Render3DEvent event) {
        long now = System.nanoTime();
        float frameSeconds = lastFrameNanos == 0L ? 0f : Math.min(0.1f, (now - lastFrameNanos) / 1.0e9f);
        lastFrameNanos = now;
        if (!showTrail.isOn() || !trail.isShowing() || !Players.present()) {
            return;
        }
        Vec3 head = isListening() && lastTickPosition != null && tickPosition != null
                ? lastTickPosition.lerp(tickPosition, event.getPartialTicks())
                : Players.position();
        boolean themed = themeColours.isOn();
        Color ghost = serverBox.isOn() ? ThemeColors.of(serverColor, themed, 1f) : null;

        Render.setDepthTest(!throughWalls.isOn());
        try {
            trail.draw(head, ThemeColors.of(trailColor, themed, 0f), ghost, thickness.getFloat(), frameSeconds,
                    animations.isOn());
        } finally {
            Render.setDepthTest(true);
        }
    }

    // ------------------------------------------------------------- holding

    /**
     * @return the limit for the next hold, in {@link PacketHold.Hold#lasting} ticks:
     *         with Grim Safe what TimerLimit allows at the current ping, else Max
     *         Timeout; in Fixed, never past what is left of Max Timeout
     */
    private int holdLimit() {
        int ticks;
        if (grimSafe.isOn()) {
            int ping = Math.max(0, PlayerList.ping(Players.entityId()));
            ticks = (pingLimit.getInt() + TIMER_DRIFT_MS - ping - MARGIN_MS) / TICK_MS;
        } else {
            ticks = maxTimeout.getInt() * 20;
        }
        if (adaptive.isOn()) {
            // One more than the budget: the last line takes one off for the tick-end the hold lets go at.
            ticks = Math.min(ticks, budget.ticks() + 1);
        }
        if (mode.get() == Mode.FIXED) {
            ticks = Math.min(ticks, maxTimeout.getInt() * 20 - (int) (Core.flows().getTick() - startTick));
        }
        // A hold lets go at the end of the tick after its limit: one more tick-end than the limit.
        return Math.max(1, ticks - 1);
    }

    /** Notes a burst going out, to be judged by what the server does in the next round trip. */
    private void burstDelivered() {
        lastBurstTick = Core.flows().getTick();
        lastBurstHeld = (int) Math.max(1L, lastBurstTick - burstStart);
        judging = true;
    }

    /** A burst that has gone a round trip without a setback landed clean: let the next one hold a little longer. */
    private void judgeQuietly() {
        if (judging && Core.flows().getTick() - lastBurstTick > judgeWindow()) {
            judging = false;
            if (adaptive.isOn()) {
                budget.landed(lastBurstHeld);
            }
        }
    }

    /** @return ticks after a burst in which a setback is its doing: the round trip and slack */
    private static int judgeWindow() {
        int ping = PlayerList.ping(Players.entityId());
        return (ping == PlayerList.UNKNOWN_PING ? 10 : (int) Math.ceil(ping / (double) TICK_MS)) + JUDGE_SLACK;
    }

    /** @return whether this burst has carried you far enough that the vanilla server would set it back */
    private boolean tooFarForOneBurst() {
        return burstFrom != null && Players.present() && Players.position().distanceTo(burstFrom) >= BURST_DISTANCE;
    }

    /** @return whether an Auto Toggle event has happened, or Fixed's Max Timeout. Read at the start of each tick, by the flow. */
    private boolean letGo() {
        if (!Players.present()) {
            return true;
        }
        boolean timedOut = mode.get() == Mode.FIXED
                && Core.flows().getTick() - startTick >= maxTimeout.getInt() * 20L;
        return timedOut
                || (setback.isOn() && setbackSeen)
                || (totemPop.isOn() && popped)
                || (explosion.isOn() && criticalCrystal(Combat.kit()))
                || (distance.isOn() && Players.position().distanceTo(startPosition) >= maxDistance.getDouble())
                || (holeReached.isOn() && inNewHole());
    }

    /** @return whether the player stands wholly inside a hole that is not the one they set off from */
    private boolean inNewHole() {
        if (!Players.onGround()) {
            return false;
        }
        Hole hole = holeAtFeet();
        return hole != null && !hole.equals(startHole)
                && hole.contains(MotionState.at(Players.position()), PLAYER_WIDTH);
    }

    /**
     * @return whether a crystal already in the world would, going off now, deal at
     *         least Explosion Damage or leave us within a heart. Measured where the
     *         client is, which is where letting go puts us on the server.
     */
    private <E> boolean criticalCrystal(CombatKit<E> kit) {
        Tracked<E> self = Core.entities().getSelf();
        if (self == null) {
            return false;
        }
        double critical = Math.min(explosionDamage.getDouble(), Players.healthPool() - 2d);
        boolean[] found = {false};
        kit.crystals().forEachWithin(self.getPosition(), CRYSTAL_RANGE, crystal -> {
            if (!found[0] && kit.crystalRules().damage(crystal, self) >= critical) {
                found[0] = true;
            }
        });
        return found[0];
    }

    private static Hole holeAtFeet() {
        Vec3i feet = Players.blockPosition();
        return Combat.kit().holeFinder().at(feet.getX(), feet.getY(), feet.getZ());
    }

    @Override
    public String getDisplayInfo() {
        return flow != null && !flow.isDone()
                ? String.format("%.1fs", (Core.flows().getTick() - startTick) / 20f)
                        + (adaptive.isOn() ? " " + budget.ticks() * TICK_MS + "ms" : "")
                : "";
    }
}
