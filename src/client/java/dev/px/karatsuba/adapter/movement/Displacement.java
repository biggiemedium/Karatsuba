package dev.px.karatsuba.adapter.movement;

import dev.px.core.math.Vec3;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.entity.Entity;
import net.minecraft.entity.MovementType;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;

/**
 * Moves the player further on their next move without changing their velocity.
 *
 * <p>For the one place a module may put the player somewhere the physics did not:
 * inside an offset Grim has already agreed to forgive (see
 * {@link EntityPush#nearHardCollider}). Velocity is left alone on purpose. Grim
 * keeps its own copy of the player's velocity and carries it from tick to tick;
 * an extra push in it would still be there after the forgiveness ran out, and
 * flag then. A displacement is spent in the tick it is made.
 *
 * <p>Refused if the extra distance would run into a block. The move would
 * collide, the collision would zero the velocity, and the client's velocity and
 * Grim's would part ways for good.
 *
 * <p><b>Game thread only.</b>
 */
public final class Displacement {

    /** How many points along the path are tested for blocks. A thin wall cannot hide between them. */
    private static final int PATH_SAMPLES = 4;

    /** Shrinks the tested box so standing on the floor does not count as hitting it. */
    private static final double CONTACT_MARGIN = 1.0E-3d;

    private static Vec3 pending;

    private Displacement() {
    }

    /** Adds {@code offset} (horizontal; Y is ignored) to the player's next own move. */
    public static void next(Vec3 offset) {
        pending = Vec3.of(offset.getX(), 0d, offset.getZ());
    }

    /** Drops a displacement that has not been used. */
    public static void cancel() {
        pending = null;
    }

    /**
     * Called by the entity mixin at the head of {@code Entity.move}.
     *
     * @return the movement to make: {@code movement} plus any pending displacement,
     *         for the local player's own move only
     */
    public static Vec3d apply(Entity entity, MovementType type, Vec3d movement) {
        Vec3 offset = pending;
        if (offset == null || type != MovementType.SELF) {
            return movement;
        }
        ClientPlayerEntity player = MinecraftClient.getInstance().player;
        if (entity != player) {
            return movement;
        }
        pending = null;
        Vec3d displaced = movement.add(offset.getX(), 0d, offset.getZ());
        return clear(player, movement, displaced) ? displaced : movement;
    }

    /**
     * @return whether every point between the plain move and the displaced one is
     *         free of blocks, horizontally. The vertical part is the game's own
     *         move and collides the same either way.
     */
    private static boolean clear(ClientPlayerEntity player, Vec3d movement, Vec3d displaced) {
        Box start = player.getBoundingBox().contract(CONTACT_MARGIN);
        for (int i = 1; i <= PATH_SAMPLES; i++) {
            double t = (double) i / PATH_SAMPLES;
            Vec3d step = movement.lerp(displaced, t);
            if (!player.getWorld().isSpaceEmpty(player, start.offset(step.x, 0d, step.z))) {
                return false;
            }
        }
        return true;
    }
}
