package dev.px.karatsuba.client.automation.task;

import dev.px.core.flow.Flow;
import dev.px.core.flow.Step;
import dev.px.core.navigation.Goal;
import dev.px.karatsuba.adapter.game.player.PlayerList;
import dev.px.karatsuba.adapter.game.player.Players;
import dev.px.karatsuba.client.automation.navigation.Navigation;
import dev.px.karatsuba.client.automation.step.Sighting;

import java.util.List;

/**
 * Keeps within a few blocks of another player, wherever they go, until stopped.
 *
 * <p>Their watch and a walk to them, raced: the watch fails the task the moment
 * they are out of sight; the walk goes on in a loop, setting off again whenever
 * they move away, facing where it goes.
 *
 * <pre>
 * .task follow Steve         three blocks behind
 * .task follow Steve 6
 * </pre>
 */
public final class FollowTask implements Task {

    public static final TaskType TYPE = TaskType.of("follow", "<player> [distance]",
            "Follows a player until stopped", FollowTask::parse)
            .completes(index -> index == 0 ? PlayerList.names() : List.of());

    /** Close enough to keep up, far enough not to walk into them. */
    private static final double DEFAULT_DISTANCE = 3d;
    private static final double MIN_DISTANCE = 1d;
    private static final double MAX_DISTANCE = 16d;

    private final String name;
    private final double distance;

    private FollowTask(String name, double distance) {
        this.name = name;
        this.distance = distance;
    }

    /**
     * @param name     the player to follow, as the tab list spells it; case does not matter
     * @param distance how near to keep, in blocks
     */
    public static FollowTask player(String name, double distance) {
        return new FollowTask(name, distance);
    }

    private static Task parse(TaskArgs args) {
        String name = args.get(0);
        if (name.equalsIgnoreCase(Players.name())) {
            args.fail("You cannot follow yourself");
        }
        double distance = args.number(1, DEFAULT_DISTANCE);
        if (distance < MIN_DISTANCE || distance > MAX_DISTANCE) {
            args.fail("Distance must be between " + (int) MIN_DISTANCE + " and " + (int) MAX_DISTANCE);
        }
        return player(name, distance);
    }

    @Override
    public String getName() {
        return "Follow";
    }

    @Override
    public String describe() {
        return name;
    }

    @Override
    public Step build(Navigation navigation) {
        Sighting sighting = Sighting.player(name);
        Step chase = Flow.loop(navigation.to(Goal.near(sighting::position, distance)))
                .named("keep within " + (int) Math.ceil(distance) + " of " + name);
        return Flow.race(sighting.watch(), chase).named("follow " + name);
    }
}
