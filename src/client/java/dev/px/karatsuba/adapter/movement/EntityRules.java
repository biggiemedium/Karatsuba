package dev.px.karatsuba.adapter.movement;

import dev.px.core.movement.prediction.EntityPhysics;
import dev.px.core.util.math.PhysicsProfile;
import dev.px.karatsuba.adapter.mixin.player.LivingEntityInvoker;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.player.PlayerEntity;

/**
 * The rules a living entity moves by right now, read off the game.
 *
 * <p>One reading for everyone: {@link MotionBridge} scores the local player's
 * simulation with it, and {@link #INSTANCE} is what {@code Core.prediction()}
 * asks about everyone else. Speed, Slowness and Jump Boost need no maths of their
 * own here: the game folds them into the attributes and the jump velocity this
 * reads, and since 1.20.5 a server can change those attributes per player too.
 *
 * <p>The attributes of another entity are what the server synced. Movement speed
 * is one the game tracks, so an enemy's Speed II arrives with it.
 */
public final class EntityRules {

    /**
     * {@code Core.prediction()}'s physics: an entity's own rules while it walks,
     * and null &mdash; "only what it has been seen to do" &mdash; while it rides,
     * glides, swims, climbs or flies, none of which the simulation models.
     */
    public static final EntityPhysics INSTANCE = (tracked, base) -> {
        if (!(tracked.get() instanceof LivingEntity entity)) {
            return base;
        }
        if (entity.hasVehicle() || entity.isGliding() || entity.isTouchingWater() || entity.isInLava()
                || entity.isClimbing()
                || entity instanceof PlayerEntity player && player.getAbilities().flying) {
            return null;
        }
        return read(entity, base);
    };

    private EntityRules() {
    }

    /**
     * {@code base} with this entity's movement rules applied; the hitbox is left
     * as {@code base} has it.
     *
     * <p>The movement-speed attribute already includes the sprint modifier, and
     * Core applies its own sprint multiplier on top, so it is divided back out
     * here; left in, a sprinting prediction would be sprinting twice.
     */
    public static PhysicsProfile read(LivingEntity entity, PhysicsProfile base) {
        double speed = entity.getAttributeValue(EntityAttributes.MOVEMENT_SPEED);
        if (entity.isSprinting()) {
            speed /= base.getSprintMultiplier();
        }
        PhysicsProfile rules = base
                .withMoveSpeedAttribute(speed)
                .withJumpVelocity(((LivingEntityInvoker) entity).karatsuba$getJumpVelocity())
                .withGravity(entity.getFinalGravity())
                .withStepHeight(entity.getStepHeight());
        // Players only: asking a mob for an attribute it was never given throws.
        if (entity.getAttributes().hasAttribute(EntityAttributes.SNEAKING_SPEED)) {
            rules = rules.withSneakMultiplier(entity.getAttributeValue(EntityAttributes.SNEAKING_SPEED));
        }
        return rules;
    }
}
