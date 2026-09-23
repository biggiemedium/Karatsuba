package dev.px.karatsuba.client.service;

import dev.px.core.event.EventBus;
import dev.px.core.event.Priority;
import dev.px.core.event.Stage;
import dev.px.core.event.Subscription;
import dev.px.core.event.impl.TickEvent;
import dev.px.core.event.impl.WorldEvent;
import dev.px.core.service.Service;
import dev.px.karatsuba.adapter.game.item.Inventories;
import dev.px.karatsuba.client.service.claim.ClaimPriority;
import dev.px.karatsuba.client.service.claim.Claims;
import lombok.Getter;

/**
 * Who gets the hotbar, and what happens when nobody wants it any more.
 *
 * <p>Two modules that both swap the held item are the classic silent conflict:
 * an auto-tool picks the pickaxe, an aura picks the sword, and which one the
 * player is actually holding depends on subscription order. Neither module can
 * see the other, so the fix people reach for is a direct reference from one to
 * the other &mdash; and that is how a client turns into a mesh.
 *
 * <p>So modules stop selecting the slot and start asking for it:
 *
 * <pre>{@code
 * @Subscribe
 * private void onTick(TickEvent event) {
 *     int sword = Inventories.findInHotbar("diamond_sword");
 *     if (sword != Inventories.NOT_FOUND) {
 *         Services.slots().request(this, sword, ClaimPriority.HIGH);
 *     }
 * }
 * }</pre>
 *
 * <h2>The player's own slot is remembered, not guessed</h2>
 * The slot the player had before anything claimed is captured the moment the
 * first claim arrives and restored when the last one expires. Reading it later
 * would read back whatever the winning module selected, and the player would
 * find their hotbar permanently parked on a pickaxe.
 *
 * <h2>Where it applies</h2>
 * Once per tick, at {@link Stage#POST} &mdash; after the player's update, so a
 * module that decided during the tick is in time, and before the next tick's
 * interactions read what is held.
 *
 * <p><b>Not for one-shot use-and-restore.</b> Throwing a pearl and going back to
 * what you were holding is a single call that starts and finishes inside one
 * tick; that is {@code Inventories.useFromHotbar}, and it does not contend with
 * anything because it has already finished. This service is for <em>sustained</em>
 * ownership.
 */
public final class SlotService implements Service {

    private final EventBus bus;

    private final Claims<Integer> claims = new Claims<>();

    /**
     * When the winning slot is selected, within {@link Stage#POST}.
     *
     * <p>Higher than {@link InteractionService}'s, and that ordering is
     * load-bearing: the game calls {@code syncSelectedSlot} immediately before
     * every attack and every use, so the slot has to be right <em>before</em> an
     * interaction runs or the server resolves the hit against the previous item.
     */
    private static final int APPLY_PRIORITY = Priority.HIGH;

    private Subscription tickStart;
    private Subscription tickEnd;
    private Subscription worldSubscription;

    /**
     * The slot the player had before arbitration took over, or
     * {@link Inventories#NOT_FOUND} when nothing is claimed.
     *
     * <p>Doubles as the "are we currently holding the hotbar" flag, which is why
     * there is no separate boolean to keep in step with it.
     */
    @Getter
    private int playerSlot = Inventories.NOT_FOUND;

    public SlotService(EventBus bus) {
        this.bus = bus;
    }

    @Override
    public String getName() {
        return "Slots";
    }

    @Override
    public void start() {
        // HIGHEST, and it has to be: modules are subscribed to the bus before any
        // service starts, so at equal priority a module would file its claim
        // before last tick's had expired.
        tickStart = bus.on(TickEvent.class, Priority.HIGHEST, event -> {
            if (event.getStage() == Stage.PRE) {
                claims.beginTick();
            }
        });
        // A separate subscription rather than the other branch of that one, so
        // the apply order against InteractionService is stated as a number
        // instead of falling out of registration order.
        tickEnd = bus.on(TickEvent.class, APPLY_PRIORITY, event -> {
            if (event.getStage() == Stage.POST) {
                apply();
            }
        });
        // A claim cannot survive a world change: the slot it names may not hold
        // what it did, and the baseline certainly does not.
        worldSubscription = bus.on(WorldEvent.class, event -> reset());
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
        reset();
    }

    // ------------------------------------------------------------- requests

    /** Asks for a hotbar slot, 0 to 8, at {@link ClaimPriority#NORMAL}. */
    public void request(Object owner, int slot) {
        request(owner, slot, ClaimPriority.NORMAL);
    }

    /** Asks for a hotbar slot for this tick. Call again next tick to keep it. */
    public void request(Object owner, int slot, int priority) {
        request(owner, slot, priority, 1);
    }

    /**
     * Asks for a hotbar slot.
     *
     * <p>Out-of-range slots are dropped rather than clamped: a module asking for
     * slot 12 has a bug, and silently giving it slot 8 hides it.
     *
     * @param holdTicks ticks the claim survives unrenewed; 1 for anything asking
     *                  every tick
     */
    public void request(Object owner, int slot, int priority, int holdTicks) {
        if (slot < 0 || slot >= Inventories.HOTBAR_SIZE) {
            return;
        }
        claims.file(owner, slot, priority, holdTicks);
    }

    /** Lets go early. Never required &mdash; a claim expires on its own. */
    public boolean release(Object owner) {
        return claims.release(owner);
    }

    // -------------------------------------------------------------- reading

    /** @return whether anything currently owns the hotbar */
    public boolean isActive() {
        return playerSlot != Inventories.NOT_FOUND;
    }

    public boolean isHeldBy(Object owner) {
        return claims.isHeldBy(owner);
    }

    /** @return the slot that will be selected, or {@link Inventories#NOT_FOUND} */
    public int getResolvedSlot() {
        Claims.Claim<Integer> winner = claims.best();
        return winner == null ? Inventories.NOT_FOUND : winner.getValue();
    }

    /** @return a name for whoever holds it, or null. For notifications and debug readouts. */
    public String getHolderName() {
        Claims.Claim<Integer> winner = claims.best();
        return winner == null ? null : winner.getOwnerName();
    }

    // ------------------------------------------------------------ internals

    /**
     * Selects the winning slot, or hands the hotbar back.
     *
     * <p>Handing back is the half that is easy to forget and the half a player
     * notices. It happens exactly once, on the tick the last claim expires.
     */
    private void apply() {
        Claims.Claim<Integer> winner = claims.best();

        if (winner == null) {
            if (playerSlot != Inventories.NOT_FOUND) {
                Inventories.selectSlot(playerSlot);
                playerSlot = Inventories.NOT_FOUND;
            }
            return;
        }

        if (playerSlot == Inventories.NOT_FOUND) {
            // First claim since going idle: whatever is selected now is the
            // player's, and is what they get back afterwards.
            playerSlot = Inventories.selectedSlot();
        }
        Inventories.selectSlot(winner.getValue());
    }

    /** Drops every claim without restoring, for when the world it referred to has gone. */
    private void reset() {
        claims.clear();
        playerSlot = Inventories.NOT_FOUND;
    }
}
