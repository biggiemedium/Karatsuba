package dev.px.karatsuba.client.service.action;

import dev.px.combat.place.Click;
import dev.px.core.math.Direction;
import dev.px.core.math.Vec2;
import dev.px.core.math.Vec3;
import dev.px.core.math.Vec3i;
import dev.px.karatsuba.adapter.world.PlacementResult;
import dev.px.karatsuba.client.service.claim.ClaimPriority;
import lombok.Getter;

import java.util.function.Consumer;

/**
 * One thing a module wants done with the player's hands this tick: attack an
 * entity, right-click a block face, or use the held item.
 *
 * <pre>{@code
 * Services.actions().submit(this, Action.attack(crystalId)
 *         .holding("end_crystal")            // anything; omit to use what is held
 *         .priority(ClaimPriority.HIGH)
 *         .then(outcome -> { if (outcome == Outcome.SENT) search.attacked(crystal); }));
 *
 * Services.actions().submit(this, Action.click(spot.getClick())
 *         .holding("end_crystal")
 *         .anyFace()                          // a crystal goes on top whichever face is hit
 *         .then(outcome -> { if (outcome == Outcome.SENT) search.placed(spot); }));
 * }</pre>
 *
 * <p>A module says <em>what</em>; {@code ActionService} decides <em>whether</em>
 * and <em>how</em>: which module wins, turning to face it, getting the item into
 * a hand, checking reach and look against what the server knows, and sending it
 * at the point in the tick vanilla does.
 *
 * <p>Built fresh each tick and handed over: the service holds it until the end
 * of the tick and then lets it go, so a module that still wants it submits again.
 * Not to be changed once submitted.
 */
@Getter
public final class Action {

    /** Which of the two buttons, and what it is pointed at. */
    public enum Kind {
        /** Left-click an entity. */
        ATTACK,
        /** Right-click a block face: place against it, or use it. */
        CLICK,
        /** Right-click the held item, at nothing. */
        USE,
        /** Put an item in the offhand. */
        OFFHAND
    }

    private final Kind kind;

    /** {@link Kind#ATTACK}: the entity. */
    private final int entityId;

    /** {@link Kind#CLICK}: the block, the face and the point on it, as planned. */
    private final Vec3i block;
    private final Direction face;
    private final Vec3 hit;
    private final boolean airPlace;

    /** An exact rotation to turn to, overriding {@link #aim}; null for none. */
    private Vec2 look;

    /** Where to look; null leaves it to the service (the target itself) or, for a use, nowhere. */
    private Vec3 aim;

    /** Registry id of the item to hold; null acts with whatever is held. */
    private String item;

    private int priority = ClaimPriority.NORMAL;
    private boolean rotate;
    private boolean anyFace;
    /** {@link Kind#CLICK}: only in a tick whose attack, from the same owner, was sent first. */
    private boolean afterAttack;
    private Direction facing;
    private Consumer<Outcome> then;
    private Consumer<PlacementResult> settled;

    /** {@link Kind#OFFHAND}: stand the player still for a tick if moving is all that stops the click. */
    private boolean stopping;

    /** {@link Kind#OFFHAND}: from the main inventory before the hotbar, which is left alone while the inventory has one. */
    private boolean inventoryFirst;

    /** {@link Kind#OFFHAND} with {@link #inventoryFirst}: the hotbar after all, when the inventory click cannot go this tick. */
    private boolean urgent;

    private Action(Kind kind, int entityId, Vec3i block, Direction face, Vec3 hit, boolean airPlace) {
        this.kind = kind;
        this.entityId = entityId;
        this.block = block;
        this.face = face;
        this.hit = hit;
        this.airPlace = airPlace;
        this.rotate = kind == Kind.ATTACK || kind == Kind.CLICK;
    }

    /** Attack an entity by id, looking at its hitbox. */
    public static Action attack(int entityId) {
        return new Action(Kind.ATTACK, entityId, null, null, null, false);
    }

    /** Right-click what a placement planner or search worked out, looking at its hit point. */
    public static Action click(Click click) {
        return new Action(Kind.CLICK, -1, click.getBlock(), click.getFace(), click.getHit(), click.isAirPlace())
                .aimAt(click.getHit());
    }

    /** Right-click a block face at a point, looking at it. */
    public static Action click(Vec3i block, Direction face, Vec3 hit) {
        return new Action(Kind.CLICK, -1, block, face, hit, false).aimAt(hit);
    }

    /** Right-click the held item at nothing: eat, throw, drink. */
    public static Action use() {
        return new Action(Kind.USE, -1, null, null, null, false);
    }

    /**
     * Put this item in the offhand: from the hotbar by the swap-hands key, or from
     * the main inventory by one inventory click. Nothing is sent if it is already
     * there.
     */
    public static Action offhand(String itemId) {
        Action action = new Action(Kind.OFFHAND, -1, null, null, null, false);
        action.item = itemId;
        action.rotate = false;
        return action;
    }

    /**
     * For an offhand item that is only in the main inventory: if the player moving
     * is all that holds the click back, stand them still for one tick so it goes
     * out at the start of the next. Worth the stutter when the item is a totem and
     * the player is about to die; see {@code InventoryClick.stopping()}.
     */
    public Action stopping() {
        this.stopping = true;
        return this;
    }

    /**
     * For an offhand item: take it from the main inventory first, and from the
     * hotbar only when the main inventory has none. Keeps the hotbar the player
     * laid out: a hotbar swap leaves the old offhand item in that hotbar slot.
     */
    public Action fromInventoryFirst() {
        this.inventoryFirst = true;
        return this;
    }

    /**
     * With {@link #fromInventoryFirst()}: when the inventory click cannot go out
     * this tick (moving, an item in use, a click already sent), use a hotbar one
     * now rather than wait. For a totem the player needs this tick.
     */
    public Action urgent() {
        this.urgent = true;
        return this;
    }

    /** Look here rather than where the service would aim. */
    public Action aimAt(Vec3 point) {
        this.aim = point;
        return this;
    }

    /**
     * Turn to exactly this rotation, rather than toward a point: straight down to
     * throw at the feet, say. The service turns there and, with its look checks
     * on, waits until the server has it before acting. Also makes a
     * {@link Kind#USE} rotate, which it does not by default.
     */
    public Action look(Vec2 rotation) {
        this.look = rotation;
        this.rotate = true;
        return this;
    }

    /** Hold this item for it; namespace optional, {@code "end_crystal"}. */
    public Action holding(String itemId) {
        this.item = itemId;
        return this;
    }

    /** {@link ClaimPriority}: the higher wins the button this tick. */
    public Action priority(int priority) {
        this.priority = priority;
        return this;
    }

    /**
     * Whether the service turns to face it. Off for a module that turns the
     * player itself; the look check still applies.
     */
    public Action rotate(boolean rotate) {
        this.rotate = rotate;
        return this;
    }

    /**
     * A click whose result does not depend on the face: an end crystal, a bed or
     * anchor being used. The look check then accepts any face of the block, and
     * the click is sent on the face looked at.
     */
    public Action anyFace() {
        this.anyFace = true;
        return this;
    }

    /**
     * A click that only works once this owner's attack has gone out: placing a
     * crystal on the base of the one being broken, which the server frees when it
     * handles the attack. Sent only in a tick whose attack is this owner's and was
     * sent; otherwise it waits.
     *
     * <p>The client still sees the broken entity when the click goes, so its own
     * check of the item can fail where the server's will not: such a click is
     * reported {@link Outcome#REFUSED} although the packet went out.
     */
    public Action afterAttack() {
        this.afterAttack = true;
        return this;
    }

    /**
     * A click whose result takes its direction from where the player faces: a
     * bed, which the game lays out ahead of you. The action then also waits until
     * the rotation the server has faces this way, horizontally.
     */
    public Action facing(Direction facing) {
        this.facing = facing;
        return this;
    }

    /** Told what happened, at the end of the tick; once, on the game thread. */
    public Action then(Consumer<Outcome> then) {
        this.then = then;
        return this;
    }

    /**
     * Told what the server made of the block this click placed, a round trip or
     * more after {@link #then} heard {@link Outcome#SENT}: confirmed, rejected, or
     * never answered. A rejected or unanswered block has already been taken back
     * client-side, so the cell shows what the server has and can be tried again.
     * Never told for a click that placed nothing, such as using a bed.
     */
    public Action settled(Consumer<PlacementResult> settled) {
        this.settled = settled;
        return this;
    }

    public boolean isAttack() {
        return kind == Kind.ATTACK;
    }

    @Override
    public String toString() {
        switch (kind) {
            case ATTACK:
                return "attack #" + entityId;
            case CLICK:
                return "click " + block + " " + face + (item == null ? "" : " with " + item);
            case OFFHAND:
                return "offhand " + item;
            default:
                return "use" + (item == null ? "" : " " + item);
        }
    }
}
