package dev.px.karatsuba.adapter.projectile;

import dev.px.core.Core;
import dev.px.core.event.impl.WorldEvent;
import dev.px.karatsuba.adapter.game.entity.ProjectileTracker;

/**
 * Where a module gets the projectile library, wired to the game.
 *
 * <pre>{@code
 * ProjectileKit kit = Projectiles.kit();
 * }</pre>
 *
 * <p>The counterpart of {@code Combat.kit()}, for Trajectories, BowAim and
 * "where did their pearl go" modules. See {@link ProjectileKit}.
 */
public final class Projectiles {

    private static MinecraftProjectileKit kit;

    private Projectiles() {
    }

    /**
     * Registers the projectile tracker and builds the kit.
     *
     * <p>From {@code MinecraftAdapter.install}, after Core's entity source is in
     * place and before {@code Core.start()}.
     */
    public static void install(Core core) {
        core.getEntityService().register(new ProjectileTracker());
        kit = new MinecraftProjectileKit(core);
        // A server may bring its own block states; cached shapes are per state.
        core.getBus().on(WorldEvent.class, event -> ProjectileBlocks.INSTANCE.clear());
    }

    /**
     * @throws IllegalStateException if called before the adapter installed it
     */
    public static ProjectileKit kit() {
        if (kit == null) {
            throw new IllegalStateException("the projectile kit is not installed yet;"
                    + " MinecraftAdapter.install(core) builds it during boot, before Core.start()");
        }
        return kit;
    }
}
