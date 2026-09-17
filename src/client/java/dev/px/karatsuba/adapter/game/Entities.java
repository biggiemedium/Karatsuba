package dev.px.karatsuba.adapter.game;

import dev.px.core.math.Vec3;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Predicate;

/**
 * The entities around the player, as {@link GameEntity} snapshots.
 *
 * <p>The other half of the adapter layer Core leaves out. A module asks for a list
 * and gets Core types; it never learns that {@code ClientWorld} exists, never holds
 * a live entity past the frame it was read on, and never has to be rewritten
 * because an entity method was renamed.
 *
 * <p><b>Reading is the expensive part, so it happens once.</b> Every query here
 * walks the world's entity list, converting as it goes. A render handler that
 * wanted players and hostiles separately should take one list and partition it
 * rather than calling twice &mdash; which is why the filtered readers below all
 * take a predicate instead of the class growing a method per combination.
 *
 * <p><b>Game thread only</b>, like {@link Players}: this reads live world state.
 * The snapshots it returns are free to outlive the call, and that is the point.
 */
public final class Entities {

    /** What the game itself treats as a sensible render distance for entity work. */
    public static final double DEFAULT_RANGE = 64d;

    private Entities() {
    }

    private static ClientWorld world() {
        return MinecraftClient.getInstance().world;
    }

    /**
     * The player, as the same snapshot type as everything else.
     *
     * @param partialTicks the frame's interpolation; pass 1 outside a render pass
     * @return the snapshot, or null in a menu
     */
    public static GameEntity self(float partialTicks) {
        ClientPlayerEntity self = MinecraftClient.getInstance().player;
        return self == null ? null : GameEntity.of(self, partialTicks);
    }

    /**
     * Every living entity in the world except the player.
     *
     * <p>Living only: dropped items, arrows and boats are entities too, and a
     * module asking for "entities" has never once meant those.
     */
    public static List<GameEntity> living(float partialTicks) {
        return matching(partialTicks, entity -> entity instanceof LivingEntity);
    }

    /** Living entities within {@code range} blocks of the player's eyes. */
    public static List<GameEntity> nearby(float partialTicks, double range) {
        Vec3 eye = Players.eyePosition();
        double squared = range * range;

        return matching(partialTicks, entity -> entity instanceof LivingEntity
                && Vec3.of(entity.getX(), entity.getY(), entity.getZ()).squaredDistanceTo(eye) <= squared);
    }

    /**
     * Every entity the predicate accepts, as snapshots.
     *
     * <p>The predicate takes the game's entity rather than the snapshot, so a
     * filter costs nothing for the entities it rejects &mdash; converting first and
     * filtering afterwards would build a {@link GameEntity} for every mob in render
     * distance to throw most of them away.
     */
    public static List<GameEntity> matching(float partialTicks, Predicate<Entity> filter) {
        ClientWorld world = world();
        ClientPlayerEntity self = MinecraftClient.getInstance().player;
        if (world == null) {
            return Collections.emptyList();
        }

        List<GameEntity> found = new ArrayList<>();
        for (Entity entity : world.getEntities()) {
            if (entity == self || !entity.isAlive() || !filter.test(entity)) {
                continue;
            }
            found.add(GameEntity.of(entity, partialTicks));
        }
        return found;
    }
}
