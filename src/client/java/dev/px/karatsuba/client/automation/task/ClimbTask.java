package dev.px.karatsuba.client.automation.task;

import dev.px.core.flow.Flow;
import dev.px.core.flow.Span;
import dev.px.core.navigation.Goal;
import dev.px.karatsuba.adapter.game.player.Players;
import dev.px.karatsuba.client.automation.step.Climb;
import dev.px.karatsuba.client.automation.step.Ladder;

import java.util.List;

/**
 * Climbs the nearest ladder: walks to it, then up or down it.
 *
 * <pre>
 * .task climb             up if at its foot, down if above it
 * .task climb up
 * .task climb down
 * </pre>
 *
 * <p>Routes cannot be planned through a ladder, so this is the piece that joins
 * a route below to a route above. See {@link Climb}.
 */
public final class ClimbTask {

    public static final TaskType TYPE = TaskType.of("climb", "[up|down]",
            "Climbs the nearest ladder, vines or scaffolding", ClimbTask::parse)
            .completes(index -> index == 0 ? List.of("up", "down") : List.of());

    /** How far to look for a ladder, in blocks. */
    private static final int SEARCH = 8;

    /** How near the foot or head of a ladder to walk before climbing takes over. */
    private static final double APPROACH = 1d;

    /** Longer than any ladder takes; past it, the climb has stopped for good. */
    private static final Span LIMIT = Span.seconds(60);

    private ClimbTask() {
    }

    /** @return a task that walks to {@code ladder} and climbs it up or down */
    public static Task ladder(Ladder ladder, boolean up) {
        return Task.of("Climb", (up ? "up " : "down ") + ladder, navigation -> Flow.sequence(
                navigation.to(Goal.near(up ? ladder.base() : ladder.top(), APPROACH)).named("walk to it"),
                (up ? Climb.up(ladder) : Climb.down(ladder)).timeout(LIMIT)));
    }

    private static Task parse(TaskArgs args) {
        Ladder ladder = Ladder.nearest(Players.position(), SEARCH);
        if (ladder == null) {
            args.fail("Nothing to climb within " + SEARCH + " blocks");
        }
        String way = args.get(0, "auto").toLowerCase();
        boolean up = switch (way) {
            case "up" -> true;
            case "down" -> false;
            case "auto" -> Players.position().getY() < ladder.getTopY();
            default -> {
                args.fail("Climb up or down, or say neither to choose by where you stand");
                yield true;
            }
        };
        return ladder(ladder, up);
    }
}
