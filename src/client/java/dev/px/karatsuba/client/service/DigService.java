package dev.px.karatsuba.client.service;

import dev.px.core.Core;
import dev.px.core.event.EventBus;
import dev.px.core.event.Priority;
import dev.px.core.event.Stage;
import dev.px.core.event.Subscription;
import dev.px.core.event.impl.TickEvent;
import dev.px.core.event.impl.WorldEvent;
import dev.px.core.math.Direction;
import dev.px.core.math.Vec2;
import dev.px.core.math.Vec3;
import dev.px.core.math.Vec3i;
import dev.px.core.movement.rotation.RotationRequest;
import dev.px.core.service.Service;
import dev.px.core.util.math.RotationMath;
import dev.px.karatsuba.adapter.game.item.Inventories;
import dev.px.karatsuba.adapter.game.player.Digging;
import dev.px.karatsuba.adapter.game.player.Interactions;
import dev.px.karatsuba.adapter.game.player.Players;
import dev.px.karatsuba.client.service.action.ActionProfile;
import dev.px.karatsuba.client.service.claim.Claims;
import dev.px.karatsuba.client.service.dig.DigProfile;
import dev.px.karatsuba.client.service.dig.DigReport;
import dev.px.karatsuba.client.service.dig.DigRequest;
import dev.px.karatsuba.client.service.dig.FastBreakBudget;
import dev.px.karatsuba.client.service.dig.Sightline;

import java.util.HashMap;
import java.util.Map;

/**
 * The player's one dig: who gets it this tick, and digging it the way Grim accepts,
 * the same way for every caller.
 *
 * <p>Callers say which block, with which tool, at what priority ({@link DigRequest});
 * the service does the rest, so Smart Mine, Ender Chest Farmer and any task dig
 * alike without one knowing another exists:
 *
 * <pre>{@code
 * Services.digging().dig(this, DigRequest.of(block).tool(pickaxe).priority(ClaimPriority.NORMAL));
 * DigReport report = Services.digging().report(this);
 * }</pre>
 *
 * <h2>One dig, one winner</h2>
 * The game breaks one block at a time, so the highest priority wins, the first to
 * ask breaking a tie, as with the action service's buttons. A request lasts the
 * tick; a caller that stops asking stops digging, and the game lets the block go.
 *
 * <h2>How it digs</h2>
 * Through the adapter's {@link Digging}: the game's own
 * {@code updateBlockBreakingProgress}, at its place in the tick. Here is what it
 * needs around that, from {@code Grim.md} ("Breaking"):
 * <ul>
 *   <li><b>The tool</b> is held openly through the inventory service, under the
 *       caller's name, and the dig waits until it is in hand.</li>
 *   <li><b>The look</b> is a rotation under the caller's name, at the profile's
 *       turn speed and mode; the dig goes out only on the face the rotation the
 *       server already has enters.</li>
 *   <li><b>The swing</b> is the profile's, never none (NoSwingBreak).</li>
 *   <li><b>Held, not let go:</b> a dig that cannot go on this tick (tool, look, a
 *       click already out) keeps its progress and sends nothing.</li>
 * </ul>
 *
 * <h2>Client Settings &gt; Mining</h2>
 * The {@link DigProfile}: <b>Quiet Mining</b> looks and swings only on the start
 * and finish ticks, the only dig packets Grim reads, leaving the head and hands
 * free between; <b>Early Break</b> finishes early inside Grim's FastBreak
 * allowance. That allowance is one balance per connection, which is why it lives
 * here, once, and not in any module: two copies would spend it twice.
 *
 * <h2>When, in the tick</h2>
 * Claims are reset at the very start of the tick and decided last in its start
 * ({@link Priority#LOWEST}), after every module has asked and after the action
 * service has sent its clicks (so a dig never shares a tick with one), and before
 * the game's input handling, where the dig itself happens.
 */
public final class DigService implements Service {

    /** Kept clear of the early budget, for the network's jitter between START and FINISHED. */
    private static final double JITTER_MARGIN = 100d;

    private final EventBus bus;
    private final ActionService actions;
    private final InventoryService inventory;

    private final Claims<DigRequest> claims = new Claims<>();

    /** Last decision, per caller. */
    private Map<Object, DigReport> reports = new HashMap<>();

    private DigProfile profile = new DigProfile() {
    };

    /** Grim's FastBreak balance as this client reckons it: one per connection. */
    private final FastBreakBudget budget = new FastBreakBudget();

    private final Sightline sightline = new Sightline();

    /** The block dug last, for the budget's START, and the face the current dig started on. */
    private Vec3i lastBlock;
    private Direction digFace;

    private long seenStart = Long.MIN_VALUE;
    private long seenFinish = Long.MIN_VALUE;

    private Subscription tickStart;
    private Subscription decide;
    private Subscription world;

    public DigService(EventBus bus, ActionService actions, InventoryService inventory) {
        this.bus = bus;
        this.actions = actions;
        this.inventory = inventory;
    }

    @Override
    public String getName() {
        return "Digging";
    }

    @Override
    @SuppressWarnings("unchecked")
    public Class<? extends Service>[] dependsOn() {
        return new Class[]{ActionService.class, InventoryService.class};
    }

    @Override
    public void start() {
        tickStart = bus.on(TickEvent.class, Priority.HIGHEST, event -> {
            if (event.getStage() == Stage.PRE) {
                claims.beginTick();
            }
        });
        decide = bus.on(TickEvent.class, Priority.LOWEST, event -> {
            if (event.getStage() == Stage.PRE) {
                decide();
            }
        });
        world = bus.on(WorldEvent.class, event -> clear());
    }

    @Override
    public void stop() {
        for (Subscription subscription : new Subscription[]{tickStart, decide, world}) {
            if (subscription != null) {
                subscription.close();
            }
        }
        tickStart = decide = world = null;
        clear();
    }

    // ------------------------------------------------------------- requests

    /** Asks for this dig this tick. A second ask from the same caller replaces the first. */
    public void dig(Object owner, DigRequest request) {
        claims.file(owner, request, request.getPriority(), 1);
    }

    /** Lets go early. Never required: a request lapses at the end of its tick. */
    public boolean release(Object owner) {
        return claims.release(owner);
    }

    /** @return how this caller's dig went when it was last decided; {@link DigReport#IDLE} if it asked for none */
    public DigReport report(Object owner) {
        return reports.getOrDefault(owner, DigReport.IDLE);
    }

    public void setProfile(DigProfile profile) {
        this.profile = profile;
    }

    public DigProfile getProfile() {
        return profile;
    }

    /** @return Grim's FastBreak balance as this client reckons it, in ms; for readouts */
    public double earlyBalance() {
        return budget.balance();
    }

    // ------------------------------------------------------------- deciding

    private void decide() {
        watchBudget();
        Map<Object, DigReport> next = new HashMap<>();
        Claims.Claim<DigRequest> winner = claims.best();
        for (Claims.Claim<DigRequest> claim : claims.all()) {
            if (claim != winner) {
                next.put(claim.getOwner(), DigReport.OUTBID);
            }
        }
        if (winner != null && Players.present()) {
            next.put(winner.getOwner(), run(winner.getOwner(), winner.getValue()));
        }
        reports = next;
    }

    /** One tick of the winner's dig. Grim's break checks read only START and FINISHED, which decides what each tick needs. */
    private DigReport run(Object owner, DigRequest request) {
        Vec3i block = request.getBlock();
        if (!Digging.breakable(block)) {
            return blockedReport("can't be broken");
        }
        if (!block.equals(lastBlock)) {
            sightline.clear();
            lastBlock = block;
        }
        ActionProfile rules = actions.getProfile();
        Vec3 eyes = Players.eyePosition();
        double reach = Players.blockReach();
        Vec3 aim = sightline.find(block, eyes, reach);
        if (aim == null) {
            return blockedReport("out of reach or sight");
        }

        boolean toolReady = request.getTool() == Inventories.NOT_FOUND;
        if (!toolReady) {
            inventory.request(owner, request.getTool(), request.getPriority());
            toolReady = Inventories.selectedSlot() == request.getTool();
        }

        // "Carries on" as the game decides it: a changed stack (Mending, a swap) starts
        // the dig again, and that START needs the look and swing like the first.
        boolean digging = Digging.continues(block);
        boolean cooling = !digging && Digging.coolingDown();
        float delta = Digging.progressPerTick(block);
        float progress = digging ? Math.max(0f, Digging.progress(block)) : 0f;
        if (digging) {
            budget.observe(delta);
        }
        boolean finishing = digging && progress + delta >= 1f;
        boolean early = digging && !finishing && earlyFinish(progress, delta);
        boolean sends = (!digging && !cooling) || finishing || early;
        boolean quiet = profile.quiet();

        // Turned from about where the eyes will be after this tick's move, which is
        // where the server measures the next dig from. Quiet, only for the start and
        // in time for the finish: the head is free in between.
        Vec2 look = eyes.add(Players.velocity()).rotationTo(aim);
        if (rules.rotate() && (!quiet || !digging || finishSoon(progress, delta, look, rules))) {
            Core.rotations().request(owner, RotationRequest.at(look)
                    .priority(request.getPriority())
                    .step(rules.turnSpeed())
                    .mode(rules.rotationMode()));
        }

        if (!toolReady) {
            // A dig in progress is held rather than let go: the game would restart it on the new item.
            return hold(block, digging, "the tool");
        }
        if (quiet && digging && !sends && digFace != null) {
            // Between the start and the finish: no packet goes out, so the look and the
            // swing do not matter, and nor does what the hands did this tick.
            Digging.request(block, digFace, Interactions.Swing.NONE);
            return DigReport.digging(progress);
        }

        // Dug only along the rotation the server already has, from the eyes it
        // knows, on the face that look enters.
        Direction face = Sightline.faceLookedAt(block, eyes, Players.serverRotation(), reach);
        if (face == null && !(rules.checks() && rules.raytrace())) {
            face = Direction.nearest(eyes.subtract(aim));
        }
        if (cooling) {
            // The game's pause after a block: the update only counts it down, and sends nothing.
            Digging.request(block, face != null ? face : Direction.nearest(eyes.subtract(aim)),
                    quiet ? Interactions.Swing.NONE : swing(rules));
            return DigReport.COOLING;
        }
        if (face == null) {
            return hold(block, digging, "the look");
        }
        if (actions.actedThisTick()) {
            // A click or attack went out this tick, which the game never pairs with a dig.
            return hold(block, digging, "the hands");
        }
        // The start and the finish each need a swing (NoSwingBreak). Quiet sends it as a
        // packet only, so the arm never moves on screen.
        Interactions.Swing swing = quiet ? Interactions.Swing.PACKET : swing(rules);
        if (!digging) {
            digFace = face;
        }
        if (early) {
            Digging.finish(block, face, swing);
        } else {
            Digging.request(block, face, swing);
        }
        return DigReport.digging(progress);
    }

    /** Holds a dig in progress where it is; a dig not yet started simply waits. */
    private static DigReport hold(Vec3i block, boolean digging, String waitingFor) {
        if (digging) {
            Digging.pause(block);
        }
        return DigReport.waiting(waitingFor);
    }

    private DigReport blockedReport(String reason) {
        sightline.clear();
        return DigReport.blocked(reason);
    }

    /**
     * @return whether to finish this tick, before the block runs out: enough of it dug
     *         that the server takes the finish, and little enough early that Grim's
     *         balance stays inside the budget
     */
    private boolean earlyFinish(float progress, float delta) {
        return profile.earlyBreak()
                && progress + delta >= profile.minProgress()
                && budget.allows(System.currentTimeMillis(), profile.earlyBudget(), JITTER_MARGIN);
    }

    /**
     * @return whether the finish is close enough that the head must start turning back
     *         to the block now to be on it in time, at the turn speed
     */
    private boolean finishSoon(float progress, float delta, Vec2 look, ActionProfile rules) {
        if (delta <= 0f) {
            return false;
        }
        double threshold = profile.earlyBreak() ? profile.minProgress() : 1d;
        int ticksLeft = (int) Math.max(0d, Math.ceil((threshold - progress) / delta) - 1d);
        float turn = RotationMath.difference(Players.serverRotation(), look);
        int turnTicks = (int) Math.ceil(turn / Math.max(1f, rules.turnSpeed()));
        return ticksLeft <= turnTicks + 1;
    }

    /** Hands Grim's view of the starts and finishes {@code Digging} sent to the budget, once each. */
    private void watchBudget() {
        long started = Digging.startedAt();
        if (started != seenStart) {
            seenStart = started;
            budget.started(started, lastBlock != null ? Digging.progressPerTick(lastBlock) : 0f);
        }
        long finished = Digging.finishedAt();
        if (finished != seenFinish) {
            seenFinish = finished;
            budget.finished(finished);
        }
    }

    /** Interaction's swing, never none: the game swings on every tick of a dig, and Grim's NoSwingBreak flags a dig without one. */
    private static Interactions.Swing swing(ActionProfile rules) {
        return rules.swing() == Interactions.Swing.NONE ? Interactions.Swing.PACKET : rules.swing();
    }

    /** A world change: every request and readout goes. Grim's balance is the connection's, so it stays. */
    private void clear() {
        claims.clear();
        reports = new HashMap<>();
        sightline.clear();
        lastBlock = null;
        digFace = null;
    }
}
