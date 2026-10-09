package dev.px.karatsuba.adapter.game.entity;

import dev.px.core.entity.EntityTracker;
import dev.px.karatsuba.adapter.mixin.world.PersistentProjectileEntityInvoker;
import dev.px.karatsuba.adapter.projectile.ProjectileType;
import net.minecraft.entity.projectile.PersistentProjectileEntity;
import net.minecraft.entity.projectile.ProjectileEntity;

/**
 * Projectiles still in the air whose flight the projectile library can follow:
 * arrows, tridents, thrown items, llama spit.
 *
 * <p>Arrows and tridents stuck in a block are left out, and so is anything
 * {@link ProjectileType#of} has no rules for (fireballs, wind charges, shulker
 * bullets, fireworks), so every entity here can be handed to a flight.
 */
public final class ProjectileTracker extends EntityTracker<ProjectileEntity> {

    public ProjectileTracker() {
        super(ProjectileEntity.class);
    }

    @Override
    protected boolean accepts(ProjectileEntity projectile) {
        if (projectile.isRemoved() || ProjectileType.of(projectile) == null) {
            return false;
        }
        return !(projectile instanceof PersistentProjectileEntity stuck)
                || !((PersistentProjectileEntityInvoker) stuck).karatsuba$isInGround();
    }
}
