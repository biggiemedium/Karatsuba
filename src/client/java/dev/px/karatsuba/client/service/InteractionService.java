package dev.px.karatsuba.client.service;

import dev.px.core.event.EventBus;
import dev.px.core.event.Priority;
import dev.px.core.event.Stage;
import dev.px.core.event.Subscription;
import dev.px.core.event.impl.TickEvent;
import dev.px.core.event.impl.WorldEvent;
import dev.px.core.service.Service;
import dev.px.karatsuba.adapter.game.player.Interactions;
import dev.px.karatsuba.client.service.claim.ClaimPriority;
import dev.px.karatsuba.client.service.claim.Claims;

/**
 * Who gets to swing, and who gets to right-click, this tick.
 *
 * <p>Less contended than the slot or the rotation, and included anyway for the
 * property that matters: <b>one attack per tick and one use per tick, chosen
 * deterministically.</b> Two modules that both attack without arbitration send
 * two hits, which is not twice as good &mdash; it is a pattern no player
 * produces and the most obvious thing a server can look for.
 *
 * <pre>{@code
 * @Subscribe
 * private void onTick(TickEvent event) {
 *     if (target != null && reachOk) {
 *         Services.interactions().attack(this, target.getId(), ClaimPriority.HIGH);
 *     }
 * }
 * }</pre>
 *
 * <h2>Two channels, not one</h2>
 * Attacking and using are separate contests because they are separate buttons: a
 * module that places blocks and a module that hits things are not in conflict,
 * and forcing them through one winner would make them so.
 *
 * <h2>Where it runs</h2>
 * At {@link TickEvent} {@link Stage#POST} &mdash; the end of the client tick, by
 * which point the rotation has already been resolved and sent. An attack
 * therefore lands with the rotation the server has just been told about, rather
 * than one tick behind it.
 */
public final class InteractionService implements Service {

    private final EventBus bus;

    /** Entity id to hit. */
    private final Claims<Integer> attacks = new Claims<>();

    /** Which hand to use. */
    private final Claims<Interactions.Hand> uses = new Claims<>();

    /**
     * When the swing happens, within {@link Stage#POST}.
     *
     * <p>Deliberately lower than {@link SlotService}'s, so the hotbar has already
     * settled: the game syncs the selected slot to the server as part of an
     * attack, and an attack that runs first would sync the slot the player had
     * rather than the one a module claimed.
     */
    private static final int RUN_PRIORITY = Priority.NORMAL;

    private Subscription tickStart;
    private Subscription tickEnd;
    private Subscription worldSubscription;

    public InteractionService(EventBus bus) {
        this.bus = bus;
    }

    @Override
    public String getName() {
        return "Interactions";
    }

    @Override
    public void start() {
        // HIGHEST: modules are on the bus before any service starts, so anything
        // lower would let a claim be filed before last tick's had expired.
        tickStart = bus.on(TickEvent.class, Priority.HIGHEST, event -> {
            if (event.getStage() == Stage.PRE) {
                attacks.beginTick();
                uses.beginTick();
            }
        });
        tickEnd = bus.on(TickEvent.class, RUN_PRIORITY, event -> {
            if (event.getStage() == Stage.POST) {
                run();
            }
        });
        worldSubscription = bus.on(WorldEvent.class, event -> clear());
    }

    @Override
    public void stop() {
        if (tickStart != null) {
            tickStart.close();
            tickStart = null;
        }
        if (tickEnd != null) {
            tickEnd.close();
            tickEnd = null;
        }
        if (worldSubscription != null) {
            worldSubscription.close();
            worldSubscription = null;
        }
        clear();
    }

    // ------------------------------------------------------------- requests

    /** Asks to attack an entity this tick, at {@link ClaimPriority#NORMAL}. */
    public void attack(Object owner, int entityId) {
        attack(owner, entityId, ClaimPriority.NORMAL);
    }

    /**
     * Asks to attack an entity this tick.
     *
     * <p>A claim, not a command: if something with a higher priority also wants
     * the swing, this one does not happen and the module is expected to ask again
     * next tick rather than to retry.
     */
    public void attack(Object owner, int entityId, int priority) {
        attacks.file(owner, entityId, priority, 1);
    }

    /** Asks to right-click with the main hand this tick. */
    public void use(Object owner, int priority) {
        use(owner, Interactions.Hand.MAIN, priority);
    }

    public void use(Object owner, Interactions.Hand hand, int priority) {
        uses.file(owner, hand, priority, 1);
    }

    /** Withdraws both this tick's requests. Never required. */
    public boolean release(Object owner) {
        boolean had = attacks.release(owner);
        return uses.release(owner) || had;
    }

    // -------------------------------------------------------------- reading

    /** @return the entity that will be hit this tick, or -1 */
    public int getResolvedAttack() {
        Claims.Claim<Integer> winner = attacks.best();
        return winner == null ? -1 : winner.getValue();
    }

    public boolean isAttacking(Object owner) {
        return attacks.isHeldBy(owner);
    }

    public boolean isUsing(Object owner) {
        return uses.isHeldBy(owner);
    }

    /** @return a name for whoever won the swing this tick, or null */
    public String getAttackHolderName() {
        Claims.Claim<Integer> winner = attacks.best();
        return winner == null ? null : winner.getOwnerName();
    }

    // ------------------------------------------------------------ internals

    /**
     * Carries out at most one of each.
     *
     * <p>The claims are not cleared afterwards: they expire on their own next
     * tick, which is what lets {@link #isAttacking} still answer truthfully for
     * the rest of this one.
     */
    private void run() {
        Claims.Claim<Integer> attack = attacks.best();
        if (attack != null) {
            Interactions.attack(attack.getValue());
        }
        Claims.Claim<Interactions.Hand> use = uses.best();
        if (use != null) {
            Interactions.use(use.getValue());
        }
    }

    private void clear() {
        attacks.clear();
        uses.clear();
    }
}
