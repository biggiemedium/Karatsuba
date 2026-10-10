package dev.px.karatsuba.client.service;

import dev.px.core.Core;

/**
 * The client's own services, and how a module reaches them.
 *
 * <p>Same shape as every other catalogue in this package tree: one list, in the
 * folder with the thing it lists, exposing {@code registerAll(Core)}. Adding a
 * service is two steps and both happen here.
 *
 * <h2>They go into Core's container, not a container of our own</h2>
 * {@code ServiceContainer.register} only refuses after startup, and the client
 * registers between {@code build()} and {@code start()} &mdash; so these get
 * Core's dependency ordering, its start and stop lifecycle, and its rollback
 * when one fails to come up. Writing a second container to hold them would be
 * writing the one Core already has, and would give two answers to "when does
 * this shut down".
 *
 * <p>Accessors mirror {@code Core.rotations()}, so the two sets read alike at a
 * call site:
 *
 * <pre>{@code
 * Core.rotations().request(this, aim, RotationPriority.HIGH, 30f);
 * Services.inventory().request(this, sword, ClaimPriority.HIGH);
 * Services.motion().scaleHorizontal(this, 1.3d, ClaimPriority.NORMAL);
 * }</pre>
 *
 * <h2>Why these are client and not adapter</h2>
 * Every one of them decides <em>which module wins</em>, and that is a decision
 * about how this client behaves rather than about how the game is reached. They
 * write through the adapter's seams and never touch {@code net.minecraft}
 * themselves, which keeps the arrow pointing the one way it has always pointed.
 */
public final class Services {

    private static InventoryService inventory;
    private static MotionService motion;
    private static ActionService actions;
    private static PacketService packets;

    private Services() {
    }

    /**
     * Registers every service.
     *
     * <p>Before {@code Core.start()}, which is what starts them. Order within is
     * not significant: none of them depends on another, and Core would sort it
     * out through {@code dependsOn()} if one ever did.
     */
    public static void registerAll(Core core) {
        inventory = core.getServices().register(new InventoryService(core.getBus()));
        motion = core.getServices().register(new MotionService(core.getBus()));
        actions = core.getServices().register(new ActionService(core.getBus()));
        packets = core.getServices().register(new PacketService(core.getBus(), core.getLogger()));
        // Core's buttons, sent through the hands above. Nothing reaches it directly:
        // a click is asked of Core.controls(), which hands the winner to it.
        core.getServices().register(new ControlClicks(core.getBus(), core.getControlService(), actions));
    }

    /**
     * The hotbar and the offhand: visible slot claims, silent swaps, the offhand,
     * and what the server believes is held.
     *
     * @throws IllegalStateException if called before {@link #registerAll}
     */
    public static InventoryService inventory() {
        return require(inventory, "InventoryService");
    }

    /** Arbitration for the player's velocity, per axis group. */
    public static MotionService motion() {
        return require(motion, "MotionService");
    }

    /**
     * The player's hands: one attack and one right-click a tick, turned toward,
     * held, checked and sent the same way for every module.
     */
    public static ActionService actions() {
        return require(actions, "ActionService");
    }

    /**
     * Packet routing by name, and the traffic counters.
     *
     * <p>The odd one out: it arbitrates nothing. Two modules cancelling the same
     * packet agree, so there is no contest to settle &mdash; what it solves is
     * volume, by dispatching to the one module that asked instead of waking
     * twenty that did not.
     */
    public static PacketService packets() {
        return require(packets, "PacketService");
    }

    /**
     * @throws IllegalStateException naming what was asked for
     *
     * <p>Loud rather than null, and for the same reason {@code Core.get()} is: a
     * module reaching a service before the client has booted is a boot-order
     * mistake, and a {@code NullPointerException} three frames later says nothing
     * about which one.
     */
    private static <T> T require(T service, String name) {
        if (service == null) {
            throw new IllegalStateException(name + " is not registered yet;"
                    + " Services.registerAll(core) runs during boot, before Core.start()");
        }
        return service;
    }
}
