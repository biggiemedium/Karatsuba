package dev.px.karatsuba.client.automation.step;

import dev.px.core.flow.Control;
import dev.px.core.flow.FlowContext;
import dev.px.core.flow.Status;
import dev.px.core.flow.Step;
import dev.px.core.flow.StopReason;
import dev.px.core.math.Box;
import dev.px.core.math.Vec3;
import dev.px.karatsuba.adapter.game.entity.GameEntity;
import dev.px.karatsuba.adapter.game.item.Inventories;
import dev.px.karatsuba.adapter.game.player.Players;
import dev.px.karatsuba.client.service.Services;
import dev.px.karatsuba.client.service.action.Action;
import dev.px.karatsuba.client.service.claim.ClaimPriority;

/**
 * Fights another player hand to hand: eyes on them, weapon out, a hit on every
 * full swing.
 *
 * <p>The hands are what a player's are:
 * <ul>
 *   <li><b>Eyes</b> on the nearest part of them, eased, so the aim follows the
 *       way a mouse does and the walk keeps facing them.</li>
 *   <li><b>The weapon</b> the hardest-hitting thing in the hotbar, switched to
 *       in the open.</li>
 *   <li><b>A hit</b> only on a recharged swing, and only within reach: an early
 *       swing does a fraction of the damage, and is not what a player who knows
 *       how to fight does. Sent through {@code Services.actions()}, so only once
 *       the rotation the server has looks at them.</li>
 * </ul>
 *
 * <p>Never finishes on its own: the task decides when the fight is over, usually
 * with the {@link Sighting}'s watch beside it. Moving is the task's too; this
 * holds no keys, so a travel step beside it that leaves the head alone walks the
 * player at them, facing them, with the Move Fix turning its keys forward.
 */
public final class Melee extends Step {

    /** Full damage lands at 1; a little under is indistinguishable and never late. */
    private static final float READY = 0.92f;

    /** How far inside their hitbox to aim, so the look lands on them, not their edge. */
    private static final double INSET = 0.15d;

    private final Sighting target;

    public Melee(Sighting target) {
        super("fight " + target.describe());
        this.target = target;
        uses(Control.ROTATION, Control.ATTACK);
    }

    @Override
    protected Status tick(FlowContext c) {
        GameEntity enemy = target.entity();
        if (enemy == null) {
            return Status.RUNNING;
        }
        Vec3 eyes = Players.eyePosition();
        Box body = enemy.getHitbox();
        c.look(Gaze.toward(body.inset(INSET).closestPoint(eyes)));

        int weapon = Inventories.bestWeaponSlot();
        if (weapon != Inventories.NOT_FOUND) {
            Services.inventory().request(this, weapon, ClaimPriority.HIGH);
        }
        if (Players.attackCooldown() >= READY && body.distanceTo(eyes) <= Players.entityReach()) {
            Services.actions().submit(this, Action.attack(enemy.getId())
                    .rotate(false)
                    .priority(ClaimPriority.HIGH));
        }
        return Status.RUNNING;
    }

    @Override
    protected void stop(FlowContext c, StopReason why) {
        Services.inventory().release(this);
        Services.actions().release(this);
    }
}
