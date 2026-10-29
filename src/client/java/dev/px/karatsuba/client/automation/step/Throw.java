package dev.px.karatsuba.client.automation.step;

import dev.px.core.flow.Control;
import dev.px.core.flow.FlowContext;
import dev.px.core.flow.Status;
import dev.px.core.flow.Step;
import dev.px.core.flow.StopReason;
import dev.px.core.math.Vec2;
import dev.px.core.math.Vec3;
import dev.px.karatsuba.adapter.game.item.Inventories;
import dev.px.karatsuba.adapter.projectile.ProjectileKit;
import dev.px.karatsuba.adapter.projectile.Projectiles;
import dev.px.karatsuba.adapter.projectile.Shot;
import dev.px.karatsuba.client.service.Services;
import dev.px.karatsuba.client.service.action.Action;
import dev.px.karatsuba.client.service.action.Outcome;
import dev.px.karatsuba.client.service.claim.ClaimPriority;
import dev.px.projectile.aim.Aim;
import dev.px.projectile.aim.AimSolver;

import java.util.function.Supplier;

/**
 * Throws one item so it lands on a point: an ender pearl to a ledge, a splash
 * potion at the feet of a fight.
 *
 * <p>The projectile library works out the rotation that lands it there, through
 * the world as it is, from where the player stands each tick. The step then does
 * what a player does: switches to the item in the open, turns to that rotation
 * (eased), and throws once the server has been told it &mdash; the throw goes
 * where the packet's rotation says, so a throw before the turn arrives misses.
 *
 * <p>Done when the throw goes out. Fails with no such item in the hotbar, with no
 * way to land it there (out of range, or blocked), or when the throw is refused
 * (a pearl still on cooldown).
 */
public final class Throw extends Step {

    /** Degrees: how near the server's rotation must be to the aim before throwing. */
    private static final float AIMED = 1.5f;

    private final Shot.Item kind;
    private final String item;
    private final Vec3 point;
    /** Instead of a point to land on: the rotation to throw along, worked out by the caller; null when it has none. */
    private final Supplier<Vec2> along;

    private AimSolver solver;
    private Outcome outcome;

    private Throw(Shot.Item kind, String item, Vec3 point) {
        super("throw " + item + " at " + Math.round(point.getX()) + ", " + Math.round(point.getY())
                + ", " + Math.round(point.getZ()));
        this.kind = kind;
        this.item = item;
        this.point = point;
        this.along = null;
        uses(Control.ROTATION, Control.USE);
    }

    private Throw(Shot.Item kind, String item, Supplier<Vec2> along) {
        super("throw " + item);
        this.kind = kind;
        this.item = item;
        this.point = null;
        this.along = along;
        uses(Control.ROTATION, Control.USE);
    }

    /** @return a step that throws an ender pearl to land on {@code point} */
    public static Throw pearl(Vec3 point) {
        return new Throw(Shot.Item.ENDER_PEARL, "ender_pearl", point);
    }

    /**
     * @return a step that throws an ender pearl along whatever rotation
     *         {@code along} gives, asked each tick; for a throw judged by more than
     *         where it lands. It fails when {@code along} gives null
     */
    public static Throw pearlAlong(Supplier<Vec2> along) {
        return new Throw(Shot.Item.ENDER_PEARL, "ender_pearl", along);
    }

    /** @return a step that throws {@code item}, which launches as {@code kind}, to land on {@code point} */
    public static Throw item(Shot.Item kind, String item, Vec3 point) {
        return new Throw(kind, item, point);
    }

    @Override
    protected void start(FlowContext c) {
        ProjectileKit kit = Projectiles.kit();
        solver = kit.aim(kit.shot(kind)).build();
        outcome = null;
    }

    @Override
    protected Status tick(FlowContext c) {
        if (outcome == Outcome.SENT) {
            return Status.DONE;
        }
        if (outcome == Outcome.REFUSED || outcome == Outcome.NO_ITEM) {
            return c.fail("the throw was refused: " + outcome.name().toLowerCase());
        }
        int slot = Inventories.findInHotbar(item);
        if (slot == Inventories.NOT_FOUND) {
            return c.fail("no " + item + " in the hotbar");
        }
        Vec2 rotation;
        if (along != null) {
            rotation = along.get();
            if (rotation == null) {
                return c.fail("no throw from here does it");
            }
        } else {
            Aim aim = solver.at(Projectiles.kit().you(), point);
            if (aim == null) {
                return c.fail("cannot land it there: " + solver.getLastStats());
            }
            rotation = aim.getRotation();
        }
        Services.inventory().request(this, slot, ClaimPriority.HIGH);
        c.look(Gaze.toward(rotation));
        if (Services.inventory().getServerSlot() == slot && Gaze.serverFaces(rotation, AIMED)) {
            Services.actions().submit(this, Action.use()
                    .look(rotation)
                    .rotate(false)
                    .priority(ClaimPriority.HIGH)
                    .then(sent -> outcome = sent));
        }
        return Status.RUNNING;
    }

    @Override
    protected void stop(FlowContext c, StopReason why) {
        Services.inventory().release(this);
        Services.actions().release(this);
    }
}
