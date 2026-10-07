package dev.px.karatsuba.adapter.game.entity;

import dev.px.core.entity.EntityTracker;
import net.minecraft.entity.decoration.EndCrystalEntity;

/**
 * End crystals in the world.
 *
 * <p>Refreshed once a tick like every tracker, and also told the moment one
 * arrives or leaves the client world (see {@code adapter.combat.CombatBridge}),
 * so a crystal aura can break one on the tick its spawn packet lands rather than
 * a tick later, and never hits one already gone.
 */
public final class CrystalTracker extends EntityTracker<EndCrystalEntity> {

    public CrystalTracker() {
        super(EndCrystalEntity.class);
    }

    @Override
    protected boolean accepts(EndCrystalEntity crystal) {
        return !crystal.isRemoved();
    }
}
