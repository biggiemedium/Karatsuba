package dev.px.karatsuba.client.module.combat.velocity;

import dev.px.core.Core;
import dev.px.core.entity.Tracked;
import dev.px.core.event.Stage;
import dev.px.core.event.Subscribe;
import dev.px.core.event.impl.TickEvent;
import dev.px.core.math.Vec3;
import dev.px.core.setting.impl.BooleanSetting;
import dev.px.core.setting.impl.NumberSetting;
import dev.px.karatsuba.adapter.combat.Combat;
import dev.px.karatsuba.adapter.combat.CombatKit;
import dev.px.karatsuba.adapter.event.game.player.KnockbackEvent;
import dev.px.karatsuba.adapter.game.player.Players;
import dev.px.karatsuba.client.module.combat.VelocityModule;
import dev.px.karatsuba.client.module.inventory.manager.ArmorPlanner;
import dev.px.karatsuba.client.module.mode.SubMode;
import dev.px.karatsuba.client.service.Services;
import dev.px.karatsuba.client.service.claim.ClaimPriority;
import dev.px.karatsuba.client.service.inventory.InventoryClick;

/**
 * Wears Blast Protection while an explosion is likely, so the server sends less
 * of its push.
 *
 * <p>Explosion knockback is worked out on the server as the push times
 * {@code 1 - explosion_knockback_resistance}, and Blast Protection adds 0.15 of
 * that a level, per piece: one Blast Protection IV piece takes 60% off, vertical
 * included, and two take all of it. Nothing on the client is changed, so there
 * is nothing for Grim to catch; see {@code anticheat/Grim.md}.
 *
 * <h2>When</h2>
 * While an end crystal is within {@code Range} (with {@code Enemy Near}, only
 * one an enemy could set off), or for {@code Hold} seconds after one was, or
 * after any explosion's push: beds and anchors are not seen coming, but the
 * next one is covered. Through {@link ArmorPlanner#preferBlastFor}, so
 * Inventory Manager ranks Blast Protection first too instead of undoing the
 * swap, and puts the usual armour back once the request lapses. Without
 * Inventory Manager the blast pieces stay on.
 *
 * <h2>Grim</h2>
 * Every click goes through {@code Services.inventory()}, which sends one a tick
 * and only on a tick the reported keys stand still (MultiActionsC). With
 * {@code Stop To Swap}, each click stops you for that tick instead of waiting
 * for you to stand still. Hits are jump-reset, as in {@link JumpResetVelocity}.
 */
public final class BlastProtectionVelocity extends SubMode<VelocityModule.Mode> {

    /**
     * Above Inventory Manager's armour ({@link ClaimPriority#HIGH}), below Elytra
     * Swap's, so a chestplate going on for its crystal guard is not held up.
     */
    private static final int PRIORITY = ClaimPriority.HIGH + 10;

    private final NumberSetting<Double> range = decimal("Range", 8d, 3d, 14d)
            .step(0.5d)
            .describe("How close an end crystal has to be to put Blast Protection on");

    private final BooleanSetting enemyNear = bool("Enemy Near", true)
            .describe("Count a crystal only while an enemy is close enough to set it off");

    private final NumberSetting<Double> enemyReach = decimal("Enemy Reach", 6d, 3d, 10d)
            .step(0.5d)
            .describe("How close to a crystal an enemy has to be")
            .visibleWhen(enemyNear);

    private final NumberSetting<Double> hold = decimal("Hold", 3d, 0.5d, 10d)
            .step(0.5d)
            .describe("Seconds to keep Blast Protection on once the danger has passed");

    private final BooleanSetting stopToSwap = bool("Stop To Swap", true)
            .describe("Stop for a tick per click rather than wait for you to stand still");

    private final BooleanSetting jumpResetHits = bool("Jump Reset", true)
            .describe("Jump on hits, as the Jump Reset mode does");

    private final ArmorPlanner planner = new ArmorPlanner();

    private final JumpReset jumpReset = new JumpReset();

    public BlastProtectionVelocity() {
        super(VelocityModule.Mode.BLAST_PROTECTION, "Blast Protection");
    }

    @Subscribe
    private void onKnockback(KnockbackEvent event) {
        if (event.isExplosion()) {
            ArmorPlanner.preferBlastFor(holdMillis());
        } else if (jumpResetHits.isOn()) {
            jumpReset.onKnockback(event, 100);
        }
    }

    @Subscribe(stage = Stage.PRE)
    private void onTickStart(TickEvent event) {
        jumpReset.onTickStart();
    }

    /**
     * Plans at the end of the tick, as Inventory Manager does; the service sends
     * the click at the start of the next.
     */
    @Subscribe(stage = Stage.POST)
    private void onTickEnd(TickEvent event) {
        if (!Players.present()) {
            return;
        }
        if (threatened(Combat.kit())) {
            ArmorPlanner.preferBlastFor(holdMillis());
        }
        if (!ArmorPlanner.blastPreferred()) {
            return;
        }
        // Never a worn-out threshold and never over an elytra: that is Inventory
        // Manager's and Elytra Swap's call.
        ArmorPlanner.Plan plan = planner.next(ArmorPlanner.Preference.BLAST, 0f, true);
        if (plan != null) {
            InventoryClick click = plan.click();
            Services.inventory().click(this, stopToSwap.isOn() ? click.stopping() : click, PRIORITY);
        }
    }

    @Override
    protected void onSelected() {
        jumpReset.reset();
    }

    private long holdMillis() {
        return Math.round(hold.getDouble() * 1000d);
    }

    /** @return whether an end crystal that could go off is within range */
    private <E> boolean threatened(CombatKit<E> kit) {
        boolean[] threat = {false};
        kit.crystals().forEachWithin(Players.position(), range.getDouble(), crystal -> {
            if (!threat[0] && (!enemyNear.isOn() || enemyNear(kit, crystal.getPosition()))) {
                threat[0] = true;
            }
        });
        return threat[0];
    }

    /** @return whether a player who is not a friend is within reach of the point */
    private <E> boolean enemyNear(CombatKit<E> kit, Vec3 point) {
        double reach = enemyReach.getDouble();
        Tracked<E> self = Core.entities().getSelf();
        for (Tracked<E> player : kit.players().getAll()) {
            if (self != null && player.get() == self.get()) {
                continue;
            }
            if (!kit.isFriend(player.get()) && player.getEyePosition().distanceTo(point) <= reach) {
                return true;
            }
        }
        return false;
    }
}
