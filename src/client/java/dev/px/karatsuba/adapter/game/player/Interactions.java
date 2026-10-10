package dev.px.karatsuba.adapter.game.player;

import dev.px.combat.place.Click;
import dev.px.core.math.Direction;
import dev.px.core.math.Vec2;
import dev.px.core.math.Vec3;
import dev.px.core.math.Vec3i;
import dev.px.karatsuba.adapter.world.PlacementResult;
import dev.px.karatsuba.adapter.world.PlacementSync;
import dev.px.karatsuba.adapter.world.Voxels;
import net.minecraft.block.BlockState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.network.ClientPlayerInteractionManager;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.Entity;
import net.minecraft.network.packet.c2s.play.HandSwingC2SPacket;
import net.minecraft.util.ActionResult;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;

import java.util.function.Consumer;

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
 * {@code client.service.ActionService}. This is the part that only knows
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

    /**
     * How the arm swing that follows an attack or a click goes out.
     *
     * <p>Vanilla swings after every attack and after most clicks, and the swing
     * is a packet of its own: a server watching for hits without one sees
     * something no player can do.
     */
    public enum Swing {
        /** The game's own swing: the packet and the animation. */
        CLIENT,
        /** The packet only: the server sees a swing, the screen does not. */
        PACKET,
        /** No swing at all. */
        NONE
    }

    private Interactions() {
    }

    /** Attacks an entity by id and swings, as vanilla does. See {@link #attack(int, Swing)}. */
    public static boolean attack(int entityId) {
        return attack(entityId, Swing.CLIENT);
    }

    /**
     * Attacks an entity by id.
     *
     * <p>By id rather than by reference because that is what a module can safely
     * hold: an id read last tick either still resolves or comes back null,
     * whereas a kept {@code Entity} may have been removed from the world and is
     * indistinguishable from a live one.
     *
     * <p>Sent through the game's own interaction manager, which syncs the held
     * slot first, exactly as a real attack does.
     *
     * @return whether the entity was found and hit
     */
    public static boolean attack(int entityId, Swing swing) {
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
        // swing follows it as its own packet.
        swing(Hand.MAIN, swing);
        return true;
    }

    /**
     * Right-clicks a hand's item at nothing, sent with a given rotation, and
     * swings as the game would.
     *
     * <p>Since 1.21 the use-item packet carries a yaw and pitch, and the server
     * throws a bottle, pearl or potion along them. The game fills them from the
     * player's rotation at the moment of use; with a silent rotation held that is
     * the camera, not where the server believes the player looks. Passing the
     * rotation the server has (or wants) makes the packet agree with the movement
     * packets, and sends the throw where it was aimed.
     *
     * @param rotation sent in the packet; null for the player's own
     * @return whether the game accepted it
     */
    public static boolean use(Hand hand, Swing swing, Vec2 rotation) {
        MinecraftClient client = MinecraftClient.getInstance();
        ClientPlayerEntity player = client.player;
        ClientPlayerInteractionManager interactions = client.interactionManager;
        if (player == null || interactions == null) {
            return false;
        }
        float yaw = player.getYaw();
        float pitch = player.getPitch();
        if (rotation != null) {
            player.setYaw(rotation.getYaw());
            player.setPitch(rotation.getPitch());
        }
        ActionResult result;
        try {
            result = interactions.interactItem(player, toGame(hand));
        } finally {
            if (rotation != null) {
                player.setYaw(yaw);
                player.setPitch(pitch);
            }
        }
        if (result instanceof ActionResult.Success success
                && success.swingSource() == ActionResult.SwingSource.CLIENT) {
            swing(hand, swing);
        }
        return result.isAccepted();
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

    /**
     * Right-clicks a block face, the way the game does when the crosshair is on
     * it: places what the hand holds against it, or uses the block.
     *
     * <p>Takes the combat library's {@link Click} &mdash; the block, the face and
     * the hit point a placement planner or search worked out &mdash; so every
     * placement goes out exactly as it was planned. Turning to face it first is
     * the caller's: a server that checks where you look will refuse a click you
     * are not looking along.
     *
     * @return whether the game accepted it: placed, used, or consumed
     */
    public static boolean click(Click click, Hand hand) {
        return click(click.getBlock(), click.getFace(), click.getHit(), hand, Swing.CLIENT);
    }

    /**
     * Right-clicks a block face at an exact point.
     *
     * <p>The parts of a {@link Click} given separately, so a caller can send the
     * point its own look ray landed on rather than the one a planner aimed for.
     * Swings when the game would ({@code MinecraftClient.doItemUse}: on a success
     * the client swings for).
     *
     * @param hit where on the face, in world space
     * @return whether the game accepted it: placed, used, or consumed
     */
    public static boolean click(Vec3i block, Direction face, Vec3 hit, Hand hand, Swing swing) {
        return click(block, face, hit, hand, swing, null);
    }

    /**
     * {@link #click(Vec3i, Direction, Vec3, Hand, Swing)}, and told how the server
     * settles the block it placed.
     *
     * <p>When the click puts a block in the world client-side &mdash; it does for a
     * placement, not for using a block &mdash; that block is followed by
     * {@link PlacementSync} until the server confirms it, rejects it, or never
     * answers, and {@code settled} hears which. A click that placed nothing is
     * never reported.
     *
     * @param settled told how the placement settles, a tick or more later; may be null
     */
    public static boolean click(Vec3i block, Direction face, Vec3 hit, Hand hand, Swing swing,
                                Consumer<PlacementResult> settled) {
        MinecraftClient client = MinecraftClient.getInstance();
        ClientPlayerEntity player = client.player;
        ClientPlayerInteractionManager interactions = client.interactionManager;
        ClientWorld world = client.world;

        if (player == null || interactions == null || world == null) {
            return false;
        }
        BlockPos clicked = Voxels.toBlockPos(block);
        // Where a placed block goes: into the clicked block if it is replaceable,
        // as grass is, otherwise beside it on the face clicked.
        BlockPos into = world.getBlockState(clicked).isReplaceable()
                ? clicked
                : clicked.offset(Voxels.toGame(face));
        BlockState before = world.getBlockState(into);

        BlockHitResult target = new BlockHitResult(
                Voxels.toVec3d(hit), Voxels.toGame(face), clicked, false);
        ActionResult result = interactions.interactBlock(player, toGame(hand), target);

        BlockState after = world.getBlockState(into);
        if (after != before && !after.isAir()) {
            PlacementSync.track(world, into, after.getBlock(), settled);
        }
        if (result instanceof ActionResult.Success success
                && success.swingSource() == ActionResult.SwingSource.CLIENT) {
            swing(hand, swing);
        }
        return result.isAccepted();
    }

    /**
     * @return how far the block being broken is, 0 to 1 in the game's ten crack
     *         stages, or -1 when nothing is being broken
     *
     * <p>Read-only: the breaking itself is the game's, driven by the attack
     * button held while the crosshair is on the block, so its speed and packets
     * are exactly a player's.
     */
    public static float breakingProgress() {
        ClientPlayerInteractionManager interactions = MinecraftClient.getInstance().interactionManager;
        if (interactions == null || !interactions.isBreakingBlock()) {
            return -1f;
        }
        return Math.max(0, interactions.getBlockBreakingProgress()) / 10f;
    }

    /** Plays the arm animation, which is also what tells the server the arm moved. */
    public static void swing(Hand hand) {
        swing(hand, Swing.CLIENT);
    }

    /** Swings as {@code swing} says: packet and animation, packet only, or not at all. */
    public static void swing(Hand hand, Swing swing) {
        ClientPlayerEntity player = MinecraftClient.getInstance().player;
        if (player == null || swing == Swing.NONE) {
            return;
        }
        if (swing == Swing.CLIENT) {
            player.swingHand(toGame(hand));
        } else {
            player.networkHandler.sendPacket(new HandSwingC2SPacket(toGame(hand)));
        }
    }

    private static net.minecraft.util.Hand toGame(Hand hand) {
        return hand == Hand.OFF ? net.minecraft.util.Hand.OFF_HAND : net.minecraft.util.Hand.MAIN_HAND;
    }
}
