package dev.px.karatsuba.adapter.game.entity;

import dev.px.core.entity.EntityTracker;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;

/**
 * Other players, alive and not spectating.
 *
 * <p>Typed as {@code LivingEntity} rather than {@code PlayerEntity} on purpose:
 * the combat library's searches are typed by one entity class, and a tracker of
 * the same type as {@link LivingTracker} can feed the same search, so an aura can
 * target only players while its damage maths and vitals stay written once.
 */
public final class PlayerTracker extends EntityTracker<LivingEntity> {

    public PlayerTracker() {
        super(LivingEntity.class);
    }

    @Override
    protected boolean accepts(LivingEntity entity) {
        return entity instanceof PlayerEntity player && player.isAlive() && !player.isSpectator();
    }
}
