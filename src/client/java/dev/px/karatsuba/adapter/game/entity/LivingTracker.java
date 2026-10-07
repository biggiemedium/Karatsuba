package dev.px.karatsuba.adapter.game.entity;

import dev.px.core.entity.EntityTracker;
import net.minecraft.entity.LivingEntity;

/**
 * Every living entity other than the local player: players, mobs, armour stands.
 *
 * <p>Core's tracker, so selectors, prediction and the combat library can follow
 * them. Dead entities are let go the tick they die; what else counts as a target
 * is the caller's {@code where}.
 */
public final class LivingTracker extends EntityTracker<LivingEntity> {

    public LivingTracker() {
        super(LivingEntity.class);
    }

    @Override
    protected boolean accepts(LivingEntity entity) {
        return entity.isAlive();
    }
}
