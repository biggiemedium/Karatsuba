package dev.px.karatsuba.adapter.game.entity;

/**
 * A living entity that counts the positions the server sends it.
 *
 * <p>Implemented on {@code LivingEntity} by {@code LivingEntityMixin}. Servers do
 * not send every entity's position every tick, so a position that has not
 * changed may mean the entity stopped or that nothing was sent; the count is how
 * Core's prediction tells the two apart ({@code EntitySource.positionStamp}).
 */
public interface PositionStamped {

    /** @return a number that changes whenever the server sends this entity a position */
    long karatsuba$positionStamp();
}
