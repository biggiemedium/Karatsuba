package dev.px.karatsuba.adapter.game.entity;

import dev.px.core.Core;
import dev.px.core.math.Box;
import dev.px.core.math.Vec2;
import dev.px.core.math.Vec3;
import dev.px.karatsuba.adapter.game.player.Players;
import dev.px.karatsuba.adapter.world.Voxels;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.Entity;
import net.minecraft.entity.ExperienceOrbEntity;
import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.effect.StatusEffect;
import net.minecraft.entity.effect.StatusEffectCategory;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.registry.Registries;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.util.math.Vec3d;

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

    /** @return the name of the entity with this id as the player sees it, or an empty string if it is gone */
    public static String nameOf(int entityId) {
        ClientWorld world = world();
        Entity entity = world == null ? null : world.getEntityById(entityId);
        return entity == null ? "" : entity.getName().getString();
    }

    /**
     * One status effect on an entity: what the server has told the client about,
     * which for another player is what it shares (most servers share all of them).
     */
    public record Effect(String id, String name, int level, int ticksLeft, boolean infinite, boolean beneficial) {
    }

    /** @return the entity's status effects, in the order the game keeps them; empty if it has none or is gone */
    public static List<Effect> effectsOf(int entityId) {
        LivingEntity living = livingById(entityId);
        if (living == null) {
            return Collections.emptyList();
        }
        List<Effect> effects = new ArrayList<>();
        for (StatusEffectInstance instance : living.getStatusEffects()) {
            RegistryEntry<StatusEffect> type = instance.getEffectType();
            effects.add(new Effect(
                    type.getKey().map(key -> key.getValue().toString()).orElse(""),
                    type.value().getName().getString(),
                    instance.getAmplifier() + 1,
                    instance.getDuration(),
                    instance.isInfinite(),
                    type.value().getCategory() != StatusEffectCategory.HARMFUL));
        }
        return effects;
    }

    /** @return the registry id of the item the entity is using (eating, drinking, blocking), or an empty string */
    public static String usingItem(int entityId) {
        LivingEntity living = livingById(entityId);
        if (living == null || !living.isUsingItem()) {
            return "";
        }
        return Registries.ITEM.getId(living.getActiveItem().getItem()).toString();
    }

    private static LivingEntity livingById(int entityId) {
        ClientWorld world = world();
        Entity entity = world == null ? null : world.getEntityById(entityId);
        return entity instanceof LivingEntity living ? living : null;
    }

    /** @return whether the entity with this id is a player on the friends list */
    public static boolean isFriend(int entityId) {
        ClientWorld world = world();
        Entity entity = world == null ? null : world.getEntityById(entityId);
        return entity instanceof PlayerEntity && Core.social().isFriend(entity.getName().getString());
    }

    /**
     * @return the hitbox of the entity with this id, where the last tick left it;
     *         null when no such entity is in the world
     *
     * <p>The box the server measures reach and look rays against: the tick's,
     * not interpolated for a frame, and widened by the game's targeting margin
     * for the few entities that have one.
     */
    public static Box hitbox(int entityId) {
        ClientWorld world = world();
        Entity entity = world == null ? null : world.getEntityById(entityId);
        if (entity == null || entity.isRemoved()) {
            return null;
        }
        return Voxels.toBox(entity.getBoundingBox().expand(entity.getTargetingMargin()));
    }

    /**
     * @return whether a look ray from {@code eye} along {@code rotation} meets the
     *         entity's {@link #hitbox} within {@code range}; false when it is gone
     *
     * <p>The check the game makes when picking the entity under the crosshair,
     * asked of any rotation: in particular the one the server was last sent.
     */
    public static boolean lookHits(int entityId, Vec3 eye, Vec2 rotation, double range) {
        ClientWorld world = world();
        Entity entity = world == null ? null : world.getEntityById(entityId);
        if (entity == null || entity.isRemoved()) {
            return false;
        }
        net.minecraft.util.math.Box box = entity.getBoundingBox().expand(entity.getTargetingMargin());
        Vec3d from = Voxels.toVec3d(eye);
        if (box.contains(from)) {
            return true;
        }
        Vec3d to = from.add(Vec3d.fromPolar(rotation.getPitch(), rotation.getYaw()).multiply(range));
        return box.raycast(from, to).isPresent();
    }

    /**
     * @return how many experience orbs lie within {@code range} blocks of the
     *         player's feet: experience already thrown and not yet picked up
     */
    public static int experienceOrbsNear(double range) {
        ClientWorld world = world();
        ClientPlayerEntity self = MinecraftClient.getInstance().player;
        if (world == null || self == null) {
            return 0;
        }
        double squared = range * range;
        int count = 0;
        for (Entity entity : world.getEntities()) {
            if (entity instanceof ExperienceOrbEntity && entity.isAlive() && entity.squaredDistanceTo(self) <= squared) {
                count++;
            }
        }
        return count;
    }

    /**
     * A dropped item lying in the world.
     *
     * @param position where it is
     * @param count    how many are in the stack
     * @param age      ticks since it was dropped; it cannot be picked up for the first ten
     */
    public record Dropped(Vec3 position, int count, int age) {
    }

    /**
     * @return the dropped items of this kind within {@code radius} of {@code around},
     *         nearest first; namespace optional ({@code "obsidian"})
     */
    public static List<Dropped> droppedItems(String itemId, Vec3 around, double radius) {
        ClientWorld world = world();
        if (world == null || itemId == null) {
            return Collections.emptyList();
        }
        String wanted = itemId.indexOf(':') < 0 ? "minecraft:" + itemId : itemId;
        double squared = radius * radius;
        List<Dropped> found = new ArrayList<>();
        for (Entity entity : world.getEntities()) {
            if (!(entity instanceof ItemEntity item) || !item.isAlive()) {
                continue;
            }
            Vec3 at = Vec3.of(item.getX(), item.getY(), item.getZ());
            if (at.squaredDistanceTo(around) > squared
                    || !wanted.equals(Registries.ITEM.getId(item.getStack().getItem()).toString())) {
                continue;
            }
            found.add(new Dropped(at, item.getStack().getCount(), item.getItemAge()));
        }
        found.sort((a, b) -> Double.compare(a.position().squaredDistanceTo(around),
                b.position().squaredDistanceTo(around)));
        return found;
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
            if (entity == self || FakePlayers.isBodyDouble(entity) || !entity.isAlive() || !filter.test(entity)) {
                continue;
            }
            found.add(GameEntity.of(entity, partialTicks));
        }
        return found;
    }
}
