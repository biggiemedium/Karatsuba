package dev.px.karatsuba.adapter.movement;

import dev.px.core.Core;
import dev.px.core.event.Priority;
import dev.px.core.event.Stage;
import dev.px.core.event.impl.TickEvent;
import dev.px.core.event.impl.WorldEvent;
import dev.px.core.math.Vec3;
import dev.px.karatsuba.adapter.game.entity.Entities;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;

/**
 * Feeds Core's motion tracker with every living thing near the player.
 *
 * <p>The other half of the simulation: {@link MotionBridge} answers "where will
 * <em>I</em> be", this answers "where will <em>they</em> be". Nobody tells the
 * client another entity's input, so all Core can do is measure where they have
 * been &mdash; and it can only do that if something records them every tick.
 * With this installed, {@code Core.simulation().predict(entity.getId(), 2)} and
 * {@code coast(...)} work for anything a module can see.
 *
 * <p>Keyed by entity id, the same id {@code GameEntity.getId()} hands a module,
 * so a module never needs anything from the game to ask.
 *
 * <p><b>Recorded on the tick, not the frame.</b> Positions read during rendering
 * are interpolated &mdash; smoothed and slightly behind &mdash; and differencing
 * them gives a velocity that is both damped and late. The end of the client tick
 * is after every entity has had its update for the tick.
 */
public final class MotionTracking {

    /** Squared, so the per-entity check is a multiply rather than a root. */
    private static final double RANGE_SQUARED = Entities.DEFAULT_RANGE * Entities.DEFAULT_RANGE;

    private MotionTracking() {
    }

    public static void install(Core core) {
        core.getBus().on(TickEvent.class, Priority.LOW, event -> {
            if (event.getStage() == Stage.POST) {
                recordAll();
            }
        });

        // Entity ids are reused across worlds, so a track that survived a server
        // switch would splice a stranger's history onto someone new. The drift
        // history goes too: the last position observed was in another world.
        core.getBus().on(WorldEvent.class, event -> {
            core.getSimulationService().getTracker().clear();
            core.getSimulationService().getDrift().reset();
            MotionBridge.reset();
        });
    }

    private static void recordAll() {
        MinecraftClient client = MinecraftClient.getInstance();
        ClientWorld world = client.world;
        ClientPlayerEntity self = client.player;
        if (world == null || self == null) {
            return;
        }
        for (Entity entity : world.getEntities()) {
            if (entity == self || !(entity instanceof LivingEntity) || !entity.isAlive()
                    || entity.squaredDistanceTo(self) > RANGE_SQUARED) {
                continue;
            }
            Core.simulation().record(entity.getId(),
                    Vec3.of(entity.getX(), entity.getY(), entity.getZ()),
                    entity.isOnGround());
        }
    }
}
