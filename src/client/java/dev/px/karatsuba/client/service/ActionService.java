package dev.px.karatsuba.client.service;

import dev.px.core.Core;
import dev.px.core.event.EventBus;
import dev.px.core.event.Priority;
import dev.px.core.event.Stage;
import dev.px.core.event.Subscription;
import dev.px.core.event.impl.TickEvent;
import dev.px.core.event.impl.WorldEvent;
import dev.px.core.math.Box;
import dev.px.core.math.Direction;
import dev.px.core.math.MathUtil;
import dev.px.core.math.Vec2;
import dev.px.core.math.Vec3;
import dev.px.core.math.Vec3i;
import dev.px.core.movement.rotation.RotationRequest;
import dev.px.core.service.Service;
import dev.px.karatsuba.adapter.event.game.player.RotationClaimEvent;
import dev.px.karatsuba.adapter.game.entity.Entities;
import dev.px.karatsuba.adapter.game.entity.Targets;
import dev.px.karatsuba.adapter.game.item.Inventories;
import dev.px.karatsuba.adapter.game.player.Interactions;
import dev.px.karatsuba.adapter.game.player.Players;
import dev.px.karatsuba.client.service.action.Action;
import dev.px.karatsuba.client.service.action.ActionProfile;
import dev.px.karatsuba.client.service.action.Outcome;
import dev.px.karatsuba.client.service.action.SwapMode;
import dev.px.karatsuba.client.service.claim.Claims;

import java.util.List;

/**
 * The player's hands: who gets to attack and who gets to right-click this tick,
 * and making sure what goes out is something a player could have sent.
 *
 * <p>Modules say what they want as an {@link Action} and the service does the
 * rest, the same way for every module:
 *
 * <pre>{@code
 * Services.actions().submit(this, Action.click(spot.getClick())
 *         .holding("end_crystal")
 *         .anyFace()
 *         .then(outcome -> { if (outcome == Outcome.SENT) search.placed(spot); }));
 * }</pre>
 *
 * <h2>Two buttons, one winner each</h2>
 * Attacking and right-clicking are separate contests, as they are separate
 * buttons: the highest priority wins each, the first to ask breaking a tie. A
 * click, a block use and an item use all share the right button, so one of them
 * goes out a tick, the way a player's single right-click does.
 *
 * <h2>When, in the tick</h2>
 * <ol>
 *   <li>Modules submit during a tick &mdash; or between ticks, from a packet such as
 *       {@code CrystalSpawnEvent}.</li>
 *   <li>Just before that tick's movement packets ({@link RotationClaimEvent}), the
 *       winning action that wants to be looked at gets the rotation, aimed from
 *       where the player now is. That rotation goes out in the movement packet.</li>
 *   <li>At the end of the tick, for {@link SwapMode#HOLD}, the item's slot is
 *       claimed from {@code Services.inventory()}, which selects it.</li>
 *   <li>At the <b>start of the next tick</b> the action is checked against what the
 *       server knows &mdash; the rotation just sent &mdash; and sent: attack first,
 *       then the right-click.</li>
 * </ol>
 *
 * <h2>Why the start of the tick</h2>
 * That is where vanilla handles its buttons ({@code handleInputEvents}): after the
 * client has dealt with the packets that arrived between ticks, including
 * answering the server's pings, and before this tick's movement packet. The end
 * of a tick looks the same to the server in every other way, but there a click
 * lands straight after the tick-end packet and before those ping answers. Grim's
 * Post check (2.3.73, for 1.21.2+ clients) holds every block placement, entity
 * interaction, item use and dig sent after a tick-end packet, and flags it if a
 * ping answer arrives before the next tick-end: exactly that order. Sent here,
 * every ping answer is already out.
 *
 * <h2>The checks</h2>
 * While the {@link ActionProfile} has them on, an action waits or is refused
 * rather than sent when a player could not have sent it:
 * <ul>
 *   <li>a container is open, or an item is in use (unless multitasking);</li>
 *   <li>the target is beyond the player's reach attribute for it, measured from
 *       the eyes the server knows: to the hitbox for an attack, to the hit point
 *       for a click;</li>
 *   <li>the rotation the server was last sent does not look at it. A click is
 *       then sent at the point that look actually lands on, never at one it
 *       could not;</li>
 *   <li>the profile's delays, and one of each button a tick.</li>
 * </ul>
 *
 * <h2>Hearing back</h2>
 * Every submitted action is told its {@link Outcome} at the end of the tick,
 * winner or not, through {@link Action#then}. Only {@link Outcome#SENT} means a
 * packet went out: that is when a search should hear {@code placed} or
 * {@code attacked}, and not before.
 *
 * <h2>Ghost blocks</h2>
 * {@code SENT} means the click went out, not that the block is there. Every click
 * goes through the game's sequenced interaction, so the client shows a placed
 * block at once and the server's answer corrects it; on top of that the
 * adapter's {@code PlacementSync} follows each placed block until the server
 * settles it, and takes back any it never answers. {@link Action#settled} hears
 * which: confirmed, rejected, or timed out. A taken-back block leaves its cell
 * showing what the server has, so a module that plans from the world simply
 * places it again.
 *
 * <p>Actions last until the end of the tick they were submitted for, then are let
 * go. A module that still wants one asks again, and one that stops asking
 * &mdash; switched off, out of targets, thrown &mdash; leaves nothing behind.
 */
public final class ActionService implements Service {

    /** How far inside a hitbox the service aims an attack, so the ray lands inside rather than on an edge. */
    private static final double AIM_INSET = 0.1d;

    /**
     * How near, in degrees, the server's rotation must be to an exact look before a
     * use goes out. Mouse sensitivity rounds every rotation, so exact equality is
     * too much to ask.
     */
    private static final float LOOK_TOLERANCE = 2f;

    private final EventBus bus;

    private final Claims<Action> attacks = new Claims<>();
    private final Claims<Action> clicks = new Claims<>();
    private final Claims<Action> offhands = new Claims<>();

    /** Whether this tick's offhand action actually sent something. */
    private boolean swapped;

    private ActionProfile profile = new ActionProfile() {
    };

    private long tick;
    private long lastAttack = Long.MIN_VALUE / 2;
    private long lastClick = Long.MIN_VALUE / 2;

    private Subscription tickStart;
    private Subscription prepare;
    private Subscription rotations;
    private Subscription world;

    public ActionService(EventBus bus) {
        this.bus = bus;
    }

    @Override
    public String getName() {
        return "Actions";
    }

    @Override
    public void start() {
        // Sent at the start of the tick, where vanilla handles its buttons, and
        // ahead of every module, so last tick's actions go out before this tick's
        // are asked for. Not at the end of the tick: that is straight after the
        // tick-end packet and before the client answers the server's pings, and
        // Grim's Post check flags a click there (see the class notes).
        tickStart = bus.on(TickEvent.class, Priority.HIGHEST, event -> {
            if (event.getStage() == Stage.PRE) {
                tick++;
                run();
            }
        });
        rotations = bus.on(RotationClaimEvent.class, event -> claimRotation());
        // HIGHEST, ahead of InventoryService applying at HIGH, so a HOLD slot claim is
        // selected by the end of this tick, ready for the click at the start of the next.
        prepare = bus.on(TickEvent.class, Priority.HIGHEST, event -> {
            if (event.getStage() == Stage.POST) {
                claimSlots();
            }
        });
        world = bus.on(WorldEvent.class, event -> clear());
    }

    @Override
    public void stop() {
        for (Subscription subscription : new Subscription[]{tickStart, prepare, rotations, world}) {
            if (subscription != null) {
                subscription.close();
            }
        }
        tickStart = prepare = rotations = world = null;
        clear();
    }

    // ------------------------------------------------------------- requests

    /**
     * Asks for an action this tick. One per module per button: a second submit
     * from the same module for the same button replaces the first.
     */
    public void submit(Object owner, Action action) {
        Claims<Action> button = action.isAttack() ? attacks
                : action.getKind() == Action.Kind.OFFHAND ? offhands
                : clicks;
        // Two ticks: a submit between ticks (from a packet) must outlive the
        // next tick's start. Every action is let go once the tick has run anyway.
        button.file(owner, action, action.getPriority(), 2);
    }

    /** Withdraws this module's actions. Never required. */
    public boolean release(Object owner) {
        boolean had = attacks.release(owner);
        had = offhands.release(owner) || had;
        return clicks.release(owner) || had;
    }

    /** The profile every action is checked and sent under. See {@code client.settings.interaction.InteractionSettings}. */
    public void setProfile(ActionProfile profile) {
        this.profile = profile == null ? new ActionProfile() {
        } : profile;
    }

    public ActionProfile getProfile() {
        return profile;
    }

    // -------------------------------------------------------------- reading

    /** @return whether this module's attack is the one that will be sent this tick */
    public boolean isAttacking(Object owner) {
        return attacks.isHeldBy(owner);
    }

    /** @return whether this module's right-click is the one that will be sent this tick */
    public boolean isClicking(Object owner) {
        return clicks.isHeldBy(owner);
    }

    /** @return a name for whoever holds the attack this tick, or null */
    public String getAttackHolderName() {
        Claims.Claim<Action> winner = attacks.best();
        return winner == null ? null : winner.getOwnerName();
    }

    /** @return a name for whoever holds the right-click this tick, or null */
    public String getClickHolderName() {
        Claims.Claim<Action> winner = clicks.best();
        return winner == null ? null : winner.getOwnerName();
    }

    // -------------------------------------------------------------- the tick

    /**
     * Turns toward whichever winning action matters most: the higher priority,
     * an attack on a tie. Only one look a tick; the other action is sent too if
     * that look happens to serve it, and otherwise waits.
     */
    private void claimRotation() {
        if (!profile.rotate() || !Players.present()) {
            return;
        }
        Claims.Claim<Action> attack = attacks.best();
        Claims.Claim<Action> click = clicks.best();
        Claims.Claim<Action> chosen = null;
        Vec2 look = null;
        if (attack != null && attack.getValue().isRotate()) {
            look = lookOf(attack.getValue());
            chosen = look == null ? null : attack;
        }
        if (click != null && click.getValue().isRotate()
                && (chosen == null || click.getPriority() > chosen.getPriority())) {
            Vec2 clickLook = lookOf(click.getValue());
            if (clickLook != null) {
                chosen = click;
                look = clickLook;
            }
        }
        if (chosen == null) {
            return;
        }
        Core.rotations().request(chosen.getOwner(), RotationRequest.at(look)
                .priority(chosen.getPriority())
                .step(profile.turnSpeed())
                .mode(profile.rotationMode()));
    }

    /**
     * The rotation an action wants: its exact {@code look} if it gave one, else
     * toward its aim point. The player has not moved yet this tick, so a point is
     * aimed at from about where the eyes will be after the move, which is where
     * the server measures the click from.
     */
    private static Vec2 lookOf(Action action) {
        if (action.getLook() != null) {
            return action.getLook();
        }
        Vec3 aim = aimOf(action);
        return aim == null ? null : Players.eyePosition().add(Players.velocity()).rotationTo(aim);
    }

    /** Where to look for an action: its own aim, else inside the target's hitbox, nearest the eyes. */
    private static Vec3 aimOf(Action action) {
        if (action.getAim() != null) {
            return action.getAim();
        }
        if (action.getKind() == Action.Kind.ATTACK) {
            Box box = Entities.hitbox(action.getEntityId());
            if (box == null) {
                return null;
            }
            double inset = Math.min(AIM_INSET, Math.min(box.getWidth(), box.getHeight()) / 4d);
            return box.inset(inset).closestPoint(Players.eyePosition());
        }
        return null;
    }

    /** {@link SwapMode#HOLD}: asks the slot service for each winner's item, before it applies. */
    private void claimSlots() {
        if (profile.swap() != SwapMode.HOLD) {
            return;
        }
        for (Claims<Action> button : List.of(attacks, clicks)) {
            Claims.Claim<Action> winner = button.best();
            if (winner == null || winner.getValue().getItem() == null) {
                continue;
            }
            String item = winner.getValue().getItem();
            if (Inventories.offhandHolds(item)) {
                continue;
            }
            int slot = Inventories.findInHotbar(item);
            if (slot != Inventories.NOT_FOUND) {
                Services.inventory().request(winner.getOwner(), slot, winner.getPriority());
            }
        }
    }

    /** Sends at most one attack and one right-click, tells every action what happened, and lets them go. */
    private void run() {
        Claims.Claim<Action> offhand = offhands.best();
        Claims.Claim<Action> attack = attacks.best();
        Claims.Claim<Action> click = clicks.best();

        // The offhand first: a totem is worth more than any hit. A swap of hands
        // in the same tick as an attack or a right-click is an order Grim flags
        // (PacketOrderG), so when one goes out, the other two wait a tick.
        swapped = false;
        Outcome moved = offhand == null ? null : executeOffhand(offhand.getValue());
        Outcome attacked = null;
        Outcome clicked = null;
        if (swapped) {
            attacked = attack == null ? null : Outcome.WAITING;
            clicked = click == null ? null : Outcome.WAITING;
        } else {
            attacked = attack == null ? null : execute(attack.getValue());
            if (click != null) {
                clicked = attacked == Outcome.SENT && !profile.sameTick()
                        ? Outcome.WAITING
                        : execute(click.getValue());
            }
        }

        report(offhands, offhand, moved);
        report(attacks, attack, attacked);
        report(clicks, click, clicked);
        clear();
    }

    /**
     * Puts an item in the offhand. From the hotbar it is the swap-hands key, with
     * no inventory click; from the main inventory it is one click, which with the
     * checks on waits until the player stands still with nothing held down
     * &mdash; an inventory click while moving, sprinting or sneaking is something
     * a player with the inventory open cannot do, and Grim flags it
     * (MultiActionsC).
     */
    private Outcome executeOffhand(Action action) {
        if (!Players.present()) {
            return Outcome.BUSY;
        }
        InventoryService.OffhandResult result = Services.inventory()
                .toOffhand(action.getItem(), profile.checks(), action.isStopping());
        swapped = result == InventoryService.OffhandResult.SENT;
        switch (result) {
            case ALREADY:
            case SENT:
                return Outcome.SENT;
            case NO_ITEM:
                return Outcome.NO_ITEM;
            case WAITING:
                return Outcome.WAITING;
            case BUSY:
                return Outcome.BUSY;
            default:
                return Outcome.REFUSED;
        }
    }

    private Outcome execute(Action action) {
        if (!Players.present()) {
            return Outcome.BUSY;
        }
        boolean checks = profile.checks();
        if (checks && Players.inContainer()) {
            return Outcome.BUSY;
        }
        if (checks && !profile.multitask() && Players.usingItem() && action.getKind() != Action.Kind.USE) {
            return Outcome.BUSY;
        }
        if (action.isAttack()
                ? tick - lastAttack < profile.attackDelay()
                : tick - lastClick < profile.clickDelay()) {
            return Outcome.WAITING;
        }

        // The item: a hand already holding it, or a hotbar slot to swap to.
        Interactions.Hand hand = Interactions.Hand.MAIN;
        int swapTo = Inventories.NOT_FOUND;
        String item = action.getItem();
        if (item != null) {
            if (Inventories.offhandHolds(item)) {
                hand = Interactions.Hand.OFF;
            } else if (!Inventories.holding(item)) {
                int slot = Inventories.findInHotbar(item);
                if (slot == Inventories.NOT_FOUND || profile.swap() == SwapMode.OFF) {
                    return Outcome.NO_ITEM;
                }
                if (profile.swap() == SwapMode.HOLD) {
                    // Claimed this tick; another module's slot claim won.
                    return Outcome.WAITING;
                }
                swapTo = slot;
            }
        }

        Vec3 eye = Players.eyePosition();
        Vec2 look = Players.serverRotation();
        Interactions.Swing swing = profile.swing();

        switch (action.getKind()) {
            case ATTACK: {
                Box box = Entities.hitbox(action.getEntityId());
                if (box == null) {
                    return Outcome.NO_TARGET;
                }
                if (checks) {
                    double reach = Players.entityReach();
                    if (box.distanceTo(eye) > reach) {
                        return Outcome.OUT_OF_REACH;
                    }
                    if (profile.raytrace() && !Entities.lookHits(action.getEntityId(), eye, look, reach)) {
                        return Outcome.TURNING;
                    }
                }
                return send(swapTo, () -> Interactions.attack(action.getEntityId(), swing), true);
            }
            case CLICK: {
                Vec3i block = action.getBlock();
                Direction face = action.getFace();
                Vec3 hit = action.getHit();
                // Unlike the look checks, not optional: a bed placed facing the wrong
                // way is in the wrong cells, whatever the server allows.
                if (action.getFacing() != null && Direction.fromYaw(look.getYaw()) != action.getFacing()) {
                    return Outcome.TURNING;
                }
                if (checks) {
                    if (action.isAirPlace() && !profile.airPlace()) {
                        return Outcome.NOT_ALLOWED;
                    }
                    double reach = Players.blockReach();
                    if (hit.distanceTo(eye) > reach) {
                        return Outcome.OUT_OF_REACH;
                    }
                    if (profile.raytrace() && !action.isAirPlace()) {
                        Targets.Hit seen = Targets.raycast(eye, look, reach);
                        if (seen == null || !seen.getBlock().equals(block)
                                || (!action.isAnyFace() && seen.getFace() != face)) {
                            return Outcome.TURNING;
                        }
                        // Sent where the server's look lands, never where it would not.
                        face = seen.getFace();
                        hit = seen.getPoint();
                    }
                }
                Interactions.Hand using = hand;
                Direction sentFace = face;
                Vec3 sentHit = hit;
                return send(swapTo, () -> Interactions.click(block, sentFace, sentHit, using, swing,
                        action.getSettled()), false);
            }
            default: {
                // A use asked for an exact look (throwing at the feet) waits until the
                // server has it: the throw goes where the packet's rotation says.
                if (checks && profile.raytrace() && action.getLook() != null && !within(look, action.getLook())) {
                    return Outcome.TURNING;
                }
                Interactions.Hand using = hand;
                // Sent with the rotation the server has, so the packet agrees with the
                // movement packets rather than carrying the camera's.
                return send(swapTo, () -> Interactions.use(using, swing, look), false);
            }
        }
    }

    /** @return whether two rotations are within {@link #LOOK_TOLERANCE} on both axes */
    private static boolean within(Vec2 actual, Vec2 wanted) {
        float yaw = Math.abs(MathUtil.wrapDegrees(actual.getYaw() - wanted.getYaw()));
        float pitch = Math.abs(actual.getPitch() - wanted.getPitch());
        return yaw <= LOOK_TOLERANCE && pitch <= LOOK_TOLERANCE;
    }

    /** Swaps in for the call when asked, sends, swaps back, and stamps the button's delay. */
    private Outcome send(int swapTo, java.util.function.BooleanSupplier act, boolean attack) {
        // The swap, and the swap back, are the inventory service's: both told to
        // the server in this call, neither sent twice.
        boolean accepted = Services.inventory().silently(swapTo, act::getAsBoolean);
        if (attack) {
            lastAttack = tick;
        } else {
            lastClick = tick;
        }
        return accepted ? Outcome.SENT : Outcome.REFUSED;
    }

    /** Tells the winner its outcome and every other action it was outranked. */
    private static void report(Claims<Action> button, Claims.Claim<Action> winner, Outcome outcome) {
        for (Claims.Claim<Action> claim : button.all()) {
            Action action = claim.getValue();
            if (action.getThen() == null) {
                continue;
            }
            try {
                action.getThen().accept(claim == winner ? outcome : Outcome.OUTRANKED);
            } catch (RuntimeException failure) {
                Core.get().getLogger().error("An action callback from " + claim.getOwnerName() + " threw", failure);
            }
        }
    }

    private void clear() {
        attacks.clear();
        clicks.clear();
        offhands.clear();
    }
}
