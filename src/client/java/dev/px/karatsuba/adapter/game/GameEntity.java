package dev.px.karatsuba.adapter.game;

import dev.px.core.Core;
import dev.px.core.math.Box;
import dev.px.core.math.Vec2;
import dev.px.core.math.Vec3;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.mob.Monster;
import net.minecraft.entity.player.PlayerEntity;

/**
 * One entity, in Core's vocabulary.
 *
 * <p>Core has no entity type &mdash; the README says so in as many words, and that
 * is the whole reason this class exists. A module that wants to outline a player
 * needs a position and a box, not a {@code net.minecraft.entity.Entity}, and the
 * moment it takes the latter it stops being portable and starts being a file that
 * has to be rewritten every game version.
 *
 * <p>So this is the translation, and it is deliberately a <b>snapshot</b> rather
 * than a wrapper. The fields are read once, on the game thread, when
 * {@link Entities} builds the list. A wrapper would hand a module a live handle it
 * could dereference from a worker thread three frames later, which is the bug this
 * shape makes impossible.
 *
 * <p>Positions are interpolated where the game interpolates them, because a module
 * drawing at 240 FPS off 20 ticks a second needs the smoothed value or its boxes
 * visibly stutter behind the entity they belong to.
 */
public final class GameEntity {

    private final int id;
    private final String name;
    private final Vec3 position;
    private final Vec3 eyePosition;
    private final Box hitbox;
    private final Vec2 rotation;
    private final float health;
    private final float maxHealth;
    private final boolean player;
    private final boolean hostile;
    private final boolean friend;

    private GameEntity(int id, String name, Vec3 position, Vec3 eyePosition, Box hitbox,
                       Vec2 rotation, float health, float maxHealth,
                       boolean player, boolean hostile, boolean friend) {
        this.id = id;
        this.name = name;
        this.position = position;
        this.eyePosition = eyePosition;
        this.hitbox = hitbox;
        this.rotation = rotation;
        this.health = health;
        this.maxHealth = maxHealth;
        this.player = player;
        this.hostile = hostile;
        this.friend = friend;
    }

    /**
     * Reads one entity into Core types.
     *
     * <p>Package-private on purpose: an entity is obtained from {@link Entities},
     * which is the one place that knows it must run on the game thread.
     *
     * @param partialTicks the frame's interpolation, or 1 to take the entity where
     *                     the last tick left it
     */
    static GameEntity of(Entity entity, float partialTicks) {
        boolean isPlayer = entity instanceof PlayerEntity;
        String name = entity.getName().getString();

        float health = 0f;
        float maxHealth = 0f;
        if (entity instanceof LivingEntity living) {
            health = living.getHealth();
            maxHealth = living.getMaxHealth();
        }

        Vec3 position = interpolated(entity, partialTicks);

        return new GameEntity(
                entity.getId(),
                name,
                position,
                position.add(0d, entity.getEyeY() - entity.getY(), 0d),
                hitboxAt(entity, position),
                Vec2.rotation(entity.getYaw(), entity.getPitch()),
                health,
                maxHealth,
                isPlayer,
                entity instanceof Monster,
                // Core owns the friends list; the adapter only reports the answer,
                // so a module never has to know where friendship is stored.
                isPlayer && Core.social().isFriend(name));
    }

    private static Vec3 interpolated(Entity entity, float partialTicks) {
        return Vec3.of(
                lerp(entity.lastRenderX, entity.getX(), partialTicks),
                lerp(entity.lastRenderY, entity.getY(), partialTicks),
                lerp(entity.lastRenderZ, entity.getZ(), partialTicks));
    }

    /**
     * The collision box, moved onto the interpolated position.
     *
     * <p>The game's box is where the last tick left it. Drawn unmoved at 240 FPS
     * off 20 ticks a second it visibly lags the entity inside it, so it is shifted
     * by the same delta the position was interpolated by rather than rebuilt from
     * dimensions &mdash; whatever shape the entity is stays its shape.
     */
    private static Box hitboxAt(Entity entity, Vec3 position) {
        net.minecraft.util.math.Box box = entity.getBoundingBox();
        double dx = position.getX() - entity.getX();
        double dy = position.getY() - entity.getY();
        double dz = position.getZ() - entity.getZ();

        return Box.of(box.minX + dx, box.minY + dy, box.minZ + dz,
                box.maxX + dx, box.maxY + dy, box.maxZ + dz);
    }

    private static double lerp(double from, double to, float progress) {
        return from + (to - from) * progress;
    }

    public int getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    /** Feet position, interpolated for the frame it was read on. */
    public Vec3 getPosition() {
        return position;
    }

    public Vec3 getEyePosition() {
        return eyePosition;
    }

    /** The collision box, in world space. What {@code Render.boxOutline} wants. */
    public Box getHitbox() {
        return hitbox;
    }

    /** Yaw and pitch, as Core's rotation vector. */
    public Vec2 getRotation() {
        return rotation;
    }

    public float getHealth() {
        return health;
    }

    public float getMaxHealth() {
        return maxHealth;
    }

    /** @return health as 0..1, or 1 for anything that has none */
    public float getHealthFraction() {
        return maxHealth <= 0f ? 1f : Math.max(0f, Math.min(1f, health / maxHealth));
    }

    public boolean isPlayer() {
        return player;
    }

    public boolean isHostile() {
        return hostile;
    }

    /** @return whether Core's social service knows this name */
    public boolean isFriend() {
        return friend;
    }

    public double distanceTo(Vec3 from) {
        return from.distanceTo(position);
    }

    @Override
    public String toString() {
        return "GameEntity[" + name + " @ " + position + "]";
    }
}
