package dev.px.karatsuba.adapter.game.player;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.network.ClientPlayerInteractionManager;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.Entity;

/**
 * Hitting things and using things.
 *
 * <p>The write half of what a module does to the world, kept behind the same
 * seam as everything else in this package: an entity is named by its id rather
 * than handed over, and the hand is this package's own enum rather than the
 * game's, so a module never imports either.
 *
 * <p>Nothing here arbitrates. Two modules that both attack in the same tick will
 * both attack, and deciding that they should not is a different job &mdash; see
 * {@code client.service.InteractionService}. This is the part that only knows
 * how.
 *
 * <p><b>Game thread only.</b>
 */
public final class Interactions {

    /**
     * Which hand.
     *
     * <p>Shadows the game's enum of the same name on purpose: this is the one a
     * module sees, and the game's is never imported anywhere outside the single
     * conversion below.
     */
    public enum Hand {
        MAIN,
        OFF
    }

    private Interactions() {
    }

    /**
     * Attacks an entity by id.
     *
     * <p>By id rather than by reference because that is what a module can safely
     * hold: an id read last tick either still resolves or comes back null,
     * whereas a kept {@code Entity} may have been removed from the world and is
     * indistinguishable from a live one.
     *
     * @return whether the entity was found and hit
     */
    public static boolean attack(int entityId) {
        MinecraftClient client = MinecraftClient.getInstance();
        ClientPlayerEntity player = client.player;
        ClientPlayerInteractionManager interactions = client.interactionManager;
        ClientWorld world = client.world;

        if (player == null || interactions == null || world == null) {
            return false;
        }
        Entity target = world.getEntityById(entityId);
        if (target == null || target == player || !target.isAlive()) {
            return false;
        }
        interactions.attackEntity(player, target);
        // The swing is separate in vanilla: attackEntity tells the server, and the
        // arm animation is the client's own. Skipping it leaves an attack that
        // nobody watching can see.
        player.swingHand(toGame(Hand.MAIN));
        return true;
    }

    /**
     * Right-clicks with a hand.
     *
     * @return whether there was a player to do it with
     */
    public static boolean use(Hand hand) {
        MinecraftClient client = MinecraftClient.getInstance();
        ClientPlayerEntity player = client.player;
        ClientPlayerInteractionManager interactions = client.interactionManager;

        if (player == null || interactions == null) {
            return false;
        }
        interactions.interactItem(player, toGame(hand));
        return true;
    }

    /** Plays the arm animation, which is also what tells the server the arm moved. */
    public static void swing(Hand hand) {
        ClientPlayerEntity player = MinecraftClient.getInstance().player;
        if (player != null) {
            player.swingHand(toGame(hand));
        }
    }

    private static net.minecraft.util.Hand toGame(Hand hand) {
        return hand == Hand.OFF ? net.minecraft.util.Hand.OFF_HAND : net.minecraft.util.Hand.MAIN_HAND;
    }
}
