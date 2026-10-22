package dev.px.karatsuba.client.automation.step;

import dev.px.core.flow.Flow;
import dev.px.core.flow.Step;
import dev.px.core.math.Vec3;
import dev.px.core.navigation.Goal;
import dev.px.karatsuba.adapter.game.entity.Entities;
import dev.px.karatsuba.adapter.game.player.Players;
import dev.px.karatsuba.client.automation.navigation.Navigation;

import java.util.List;
import java.util.function.Supplier;

/**
 * Walks over the dropped items of one kind lying around a point, one after another,
 * nearest first, until none is left there: the drops that bounced or were knocked
 * out of pickup reach.
 *
 * <p>Built from the navigation's own walking (each a trip to the nearest drop, so
 * the route rules, hazards and the player's state all apply), not a step of its
 * own. A drop younger than {@code minAge} ticks is left alone: it may still be on
 * its way to the player, and none can be picked up in its first ten ticks anyway.
 */
public final class PickUpDrops {

    /** Close enough to the drop for the pickup box (the player's, grown by one) to reach it. */
    private static final double PICKUP = 0.7d;

    private PickUpDrops() {
    }

    /**
     * @param around where to look, read each time a walk starts
     * @param radius how far from {@code around} a drop may be and still be fetched
     * @param minAge ticks a drop must have lain before it counts as stray
     */
    public static Step of(Navigation navigation, String item, Supplier<Vec3> around, double radius, int minAge) {
        return Flow.loop(navigation.to(c -> {
                    Entities.Dropped next = stray(item, around.get(), radius, minAge);
                    return next == null ? Goal.near(Players.position(), PICKUP) : Goal.near(next.position(), PICKUP);
                }))
                .until(c -> stray(item, around.get(), radius, minAge) == null)
                .named("pick up " + item.substring(item.indexOf(':') + 1).replace('_', ' '));
    }

    /** @return whether a stray drop of the item lies around the point */
    public static boolean any(String item, Vec3 around, double radius, int minAge) {
        return stray(item, around, radius, minAge) != null;
    }

    private static Entities.Dropped stray(String item, Vec3 around, double radius, int minAge) {
        List<Entities.Dropped> drops = Entities.droppedItems(item, around, radius);
        for (Entities.Dropped drop : drops) {
            if (drop.age() >= minAge) {
                return drop;
            }
        }
        return null;
    }
}
