package dev.px.karatsuba.adapter.movement;

import dev.px.core.math.Vec3;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.decoration.ArmorStandEntity;
import net.minecraft.entity.mob.ShulkerEntity;
import net.minecraft.entity.passive.BatEntity;
import net.minecraft.entity.passive.ParrotEntity;
import net.minecraft.entity.vehicle.AbstractBoatEntity;
import net.minecraft.entity.vehicle.AbstractMinecartEntity;
import net.minecraft.predicate.entity.EntityPredicates;
import net.minecraft.util.math.Box;

/**
 * The entities Grim forgives movement around, and the motion they have already
 * put on the player.
 *
 * <p>Grim does not simulate entity collisions. It widens what it accepts instead,
 * and these are the readings a module needs to stay inside that widening:
 *
 * <ul>
 *   <li>{@link #pushers} counts what {@code MovementTicker.handleEntityCollisions}
 *       counts: pushable entities touching the player's box grown by 0.2. Each
 *       one widens the accepted velocity by 0.08 on X and on Z
 *       ({@code PredictionEngine.handleStartingVelocityUncertainty}).</li>
 *   <li>{@link #nearHardCollider} is {@code UncertaintyHandler.checkForHardCollision}:
 *       a boat or shulker within 1.0 of the player's box, which takes 1.2 off the
 *       Simulation offset for that tick and the two after.</li>
 *   <li>{@link #sinceTravel} is how much of that widening vanilla has already
 *       spent. The client pushes the player out of other entities after the move
 *       ({@code tickCramming}) and from their own ticks, and Grim explains those
 *       pushes with the same box.</li>
 * </ul>
 *
 * <p>Every reading is a subset of Grim's: callers pass a smaller grow than Grim's
 * and count only this tick, where Grim keeps the largest count of the last three.
 * Anything this class accepts, Grim does too.
 *
 * <p><b>Game thread only.</b>
 */
public final class EntityPush {

    /** The player's velocity straight after the last {@code travel}, before anything pushed it. */
    private static Vec3 afterTravel;

    /** Who {@link #afterTravel} belongs to, so a respawn or a new world cannot read the old one. */
    private static ClientPlayerEntity owner;

    private EntityPush() {
    }

    // ------------------------------------------------------------- recorded

    /** Called by the living-entity mixin straight after the local player's {@code travel}. */
    public static void afterTravel(ClientPlayerEntity player) {
        owner = player;
        afterTravel = Vec3.of(player.getVelocity().x, player.getVelocity().y, player.getVelocity().z);
    }

    /**
     * Called by the network mixin when the server sets the player's motion: a
     * velocity packet, an explosion or a teleport. Grim explains those through
     * its transactions, not the collision box, so they must not be read as a push.
     */
    public static void serverVelocity() {
        afterTravel = null;
    }

    /**
     * @return horizontal motion added since the last {@code travel} (Y is zero), or
     *         null when it cannot be known: no move yet, a new player, or the
     *         server set the motion in between
     *
     * <p>Read before this tick's move. Consumed: the next reading needs the next move.
     */
    public static Vec3 sinceTravel() {
        ClientPlayerEntity player = MinecraftClient.getInstance().player;
        Vec3 before = afterTravel;
        afterTravel = null;
        if (player == null || player != owner || before == null) {
            return null;
        }
        return Vec3.of(player.getVelocity().x - before.getX(), 0d, player.getVelocity().z - before.getZ());
    }

    // ------------------------------------------------------------- counted

    /**
     * @param grow how far to grow the player's box; Grim grows it by 0.2
     * @return how many entities Grim counts as colliding with the player
     *
     * <p>Grim's {@code TypedPacketEntity.isPushable}: anything living except armor
     * stands, bats and parrots, plus boats and minecarts. Vanilla's own
     * {@code canBePushedBy} filters out spectators and teams whose collision
     * rule forbids it, which Grim honours too. Nothing counts while riding.
     */
    public static int pushers(double grow) {
        ClientPlayerEntity player = MinecraftClient.getInstance().player;
        if (player == null || player.hasVehicle() || player.isSpectator()) {
            return 0;
        }
        Box box = player.getBoundingBox().expand(grow);
        return player.getWorld().getOtherEntities(player, box,
                EntityPredicates.canBePushedBy(player).and(EntityPush::pushable)).size();
    }

    /**
     * @param grow how far to grow the player's box; Grim grows it by 1.0
     * @return whether a boat or shulker is that close, other than one being ridden
     */
    public static boolean nearHardCollider(double grow) {
        ClientPlayerEntity player = MinecraftClient.getInstance().player;
        if (player == null || player.hasVehicle()) {
            return false;
        }
        Box box = player.getBoundingBox().expand(grow);
        return !player.getWorld().getOtherEntities(player, box, entity -> entity.isAlive()
                && (entity instanceof AbstractBoatEntity || entity instanceof ShulkerEntity)).isEmpty();
    }

    private static boolean pushable(Entity entity) {
        if (!entity.isAlive()) {
            return false;
        }
        if (entity instanceof AbstractBoatEntity || entity instanceof AbstractMinecartEntity) {
            return true;
        }
        return entity instanceof LivingEntity
                && !(entity instanceof ArmorStandEntity)
                && !(entity instanceof BatEntity)
                && !(entity instanceof ParrotEntity);
    }
}
