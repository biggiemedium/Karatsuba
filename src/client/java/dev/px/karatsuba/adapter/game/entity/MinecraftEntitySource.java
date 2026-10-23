package dev.px.karatsuba.adapter.game.entity;

import com.google.common.collect.Iterables;
import dev.px.core.entity.EntitySource;
import dev.px.karatsuba.adapter.mixin.player.LivingEntityAccessor;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;

/**
 * The world, as Core's {@code EntityService} reads it once a tick.
 *
 * <p>The adapter half of {@code Core.entities()}: it lists the world and says
 * where each entity is. Everything built on Core's trackers &mdash; targeting,
 * {@code Core.prediction()}, and the combat library's searches, obstructions and
 * damage monitor &mdash; sees the game through this one class.
 *
 * <h2>The server's positions, not the drawn ones</h2>
 * The game eases another entity toward each position the server sends over a few
 * ticks, which smooths and delays every move. Core's prediction is measured
 * against what the server said, so for anything but the local player this reports
 * the position the entity is easing toward, and {@link #positionStamp} counts the
 * positions sent. The local player is reported where the game has it: its
 * movement is our own, not interpolated.
 *
 * <p>Fake players (a client-side entity no server ever positions) never ease, so
 * they read as where they stand.
 *
 * <p><b>Game thread only</b>, like the tick Core refreshes on.
 */
public final class MinecraftEntitySource implements EntitySource<Entity> {

    public static final MinecraftEntitySource INSTANCE = new MinecraftEntitySource();

    private MinecraftEntitySource() {
    }

    @Override
    public Iterable<Entity> entities() {
        ClientWorld world = MinecraftClient.getInstance().world;
        // A body double is the local player's likeness, not a player to track.
        return world == null ? null : Iterables.filter(world.getEntities(), entity -> !FakePlayers.isBodyDouble(entity));
    }

    @Override
    public Entity self() {
        return MinecraftClient.getInstance().player;
    }

    @Override
    public double x(Entity entity) {
        LivingEntityAccessor easing = easing(entity);
        return easing == null ? entity.getX() : easing.karatsuba$getServerX();
    }

    @Override
    public double y(Entity entity) {
        LivingEntityAccessor easing = easing(entity);
        return easing == null ? entity.getY() : easing.karatsuba$getServerY();
    }

    @Override
    public double z(Entity entity) {
        LivingEntityAccessor easing = easing(entity);
        return easing == null ? entity.getZ() : easing.karatsuba$getServerZ();
    }

    @Override
    public double width(Entity entity) {
        return entity.getWidth();
    }

    @Override
    public double height(Entity entity) {
        return entity.getHeight();
    }

    @Override
    public double eyeHeight(Entity entity) {
        return entity.getStandingEyeHeight();
    }

    @Override
    public float yaw(Entity entity) {
        return entity.getYaw();
    }

    @Override
    public float pitch(Entity entity) {
        return entity.getPitch();
    }

    @Override
    public long positionStamp(Entity entity) {
        if (entity instanceof PositionStamped stamped && entity != MinecraftClient.getInstance().player) {
            return stamped.karatsuba$positionStamp();
        }
        return -1L;
    }

    /**
     * @return the living entity's server target while it is still easing toward
     *         it; null when it has arrived, is the local player, or is not living
     */
    private static LivingEntityAccessor easing(Entity entity) {
        if (entity instanceof LivingEntity && entity != MinecraftClient.getInstance().player) {
            LivingEntityAccessor living = (LivingEntityAccessor) entity;
            if (living.karatsuba$getBodyTrackingIncrements() > 0) {
                return living;
            }
        }
        return null;
    }
}
