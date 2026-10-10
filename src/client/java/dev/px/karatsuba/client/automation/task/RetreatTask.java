package dev.px.karatsuba.client.automation.task;

import dev.px.core.flow.Flow;
import dev.px.core.flow.Status;
import dev.px.core.flow.Step;
import dev.px.core.math.Box;
import dev.px.core.math.Vec3;
import dev.px.core.navigation.Goal;
import dev.px.karatsuba.adapter.game.player.PlayerList;
import dev.px.karatsuba.client.automation.navigation.Navigation;
import dev.px.karatsuba.client.automation.step.Sighting;

import java.util.List;

/**
 * Gets away from another player: runs until they are a safe distance off, or out
 * of sight.
 *
 * <pre>
 * .task retreat              from the nearest player who is not a friend, to 16 blocks
 * .task retreat Steve 24
 * </pre>
 *
 * <p>A run, not a backpedal: each leg plans a way out of the ground around where
 * they are now and walks it facing where it goes, sprinting where it can. A leg
 * ends once out of that ground, and the next is planned from where they have got
 * to, so a chaser is run from, not the place they stood.
 */
public final class RetreatTask implements Task {

    public static final TaskType TYPE = TaskType.of("retreat", "[player] [distance]",
            "Runs from a player, or the nearest one who is not a friend", RetreatTask::parse)
            .completes(index -> index == 0 ? PlayerList.names() : List.of());

    private static final double SEARCH = 24d;
    private static final double DEFAULT_DISTANCE = 16d;
    private static final double MIN_DISTANCE = 4d;
    private static final double MAX_DISTANCE = 48d;

    /** Blocks above and below them that count as their ground: a drop between is still near. */
    private static final double HEIGHT = 4d;

    private final String name;
    private final double distance;

    private RetreatTask(String name, double distance) {
        this.name = name;
        this.distance = distance;
    }

    /** @return a task that runs from {@code name}, or the nearest enemy when null, to {@code distance} blocks */
    public static RetreatTask from(String name, double distance) {
        return new RetreatTask(name, distance);
    }

    private static Task parse(TaskArgs args) {
        String name = null;
        double distance = DEFAULT_DISTANCE;
        if (args.has(0)) {
            // One argument may be either: a number is the distance, anything else a name.
            try {
                distance = Double.parseDouble(args.get(0));
            } catch (NumberFormatException notANumber) {
                name = args.get(0);
                distance = args.number(1, DEFAULT_DISTANCE);
            }
        }
        if (distance < MIN_DISTANCE || distance > MAX_DISTANCE) {
            args.fail("Distance must be between " + (int) MIN_DISTANCE + " and " + (int) MAX_DISTANCE);
        }
        Sighting threat = name == null ? Sighting.nearestEnemy(SEARCH) : Sighting.player(name);
        if (!threat.look()) {
            args.fail(name == null ? "No one to get away from within " + (int) SEARCH + " blocks" : name + " is not in sight");
        }
        return from(name, distance);
    }

    @Override
    public String getName() {
        return "Retreat";
    }

    @Override
    public String describe() {
        return "from " + (name != null ? name : "the nearest enemy") + " to " + (int) distance + " blocks";
    }

    @Override
    public Step build(Navigation navigation) {
        Sighting threat = name == null ? Sighting.nearestEnemy(SEARCH) : Sighting.player(name);
        threat.look();
        Step safe = Flow.step("until safe")
                .tick(c -> !threat.look() || threat.distance() >= distance ? Status.DONE : Status.RUNNING);
        Step run = Flow.loop(navigation.to(c -> Goal.avoid(around(threat.position(), distance)))).named("run");
        return Flow.race(safe, run).named("retreat from " + threat.describe());
    }

    /** @return the ground within {@code radius} of {@code centre}, a few blocks up and down */
    private static Box around(Vec3 centre, double radius) {
        return Box.of(centre.getX() - radius, centre.getY() - HEIGHT, centre.getZ() - radius,
                centre.getX() + radius, centre.getY() + HEIGHT, centre.getZ() + radius);
    }
}
