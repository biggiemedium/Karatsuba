package dev.px.karatsuba.client.automation.step;

import dev.px.core.flow.Flow;
import dev.px.core.flow.Status;
import dev.px.core.flow.Step;
import dev.px.core.math.Vec3;
import dev.px.karatsuba.adapter.game.entity.Entities;
import dev.px.karatsuba.adapter.game.entity.GameEntity;
import dev.px.karatsuba.adapter.game.player.Players;

/**
 * Another player a task is about &mdash; to follow, fight or get away from &mdash;
 * and where they were last seen.
 *
 * <p>Looked for once a tick by its {@link #watch()} step, and read from as often
 * as anything likes: a goal built on {@link #position()} is asked about thousands
 * of times a plan, and must not search the world each time.
 *
 * <pre>{@code
 * Sighting enemy = Sighting.nearestEnemy(24d);
 * Step fight = Flow.race(enemy.watch(), Flow.loop(navigation.to(Goal.near(enemy::position, 3d))));
 * }</pre>
 */
public final class Sighting {

    /** How far away a player may be and still be seen: the client's own entity range. */
    public static final double SIGHT = Entities.DEFAULT_RANGE;

    /** The player asked for by name, or null for whoever is nearest. */
    private final String name;
    private final double range;

    private GameEntity seen;
    private String label;

    private Sighting(String name, double range) {
        this.name = name;
        this.range = range;
        this.label = name != null ? name : "the nearest player";
    }

    /** @return a sighting of the player with this name, case aside */
    public static Sighting player(String name) {
        return new Sighting(name, SIGHT);
    }

    /** @return a sighting of the nearest player within {@code range} who is not a friend */
    public static Sighting nearestEnemy(double range) {
        return new Sighting(null, range);
    }

    /**
     * Looks for them now. A named player is kept to; the nearest enemy is picked
     * once and kept to after, so a fight does not hop between players.
     *
     * @return whether they are in sight
     */
    public boolean look() {
        String wanted = seen != null ? seen.getName() : name;
        Vec3 eyes = Players.eyePosition();
        GameEntity best = null;
        double bestDistance = Double.MAX_VALUE;
        for (GameEntity entity : Entities.nearby(1f, range)) {
            if (!entity.isPlayer() || entity.getId() == Players.entityId()) {
                continue;
            }
            if (wanted != null) {
                if (entity.getName().equalsIgnoreCase(wanted)) {
                    best = entity;
                    break;
                }
                continue;
            }
            double distance = entity.distanceTo(eyes);
            if (!entity.isFriend() && distance < bestDistance) {
                best = entity;
                bestDistance = distance;
            }
        }
        if (best == null) {
            return false;
        }
        seen = best;
        label = best.getName();
        return true;
    }

    /**
     * @return a step that looks for them every tick: done if they go down, failed
     *         once they are out of sight
     */
    public Step watch() {
        return Flow.step("watch " + label)
                .tick(c -> {
                    if (!look()) {
                        return c.fail(label + " is out of sight");
                    }
                    return seen.getHealth() <= 0f ? Status.DONE : Status.RUNNING;
                });
    }

    /** @return the player as last seen, or null before they first were */
    public GameEntity entity() {
        return seen;
    }

    /** @return where their feet were last seen; until then, where ours are, which counts as arrived */
    public Vec3 position() {
        return seen != null ? seen.getPosition() : Players.position();
    }

    /** @return blocks from our eyes to theirs as last seen; infinite before they were */
    public double distance() {
        return seen == null ? Double.POSITIVE_INFINITY : Players.eyePosition().distanceTo(seen.getEyePosition());
    }

    /** @return who, for the HUD and chat: their name once seen */
    public String describe() {
        return label;
    }
}
