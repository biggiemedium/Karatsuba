package dev.px.karatsuba.client.automation.task;

import dev.px.core.flow.Step;
import dev.px.core.math.Vec3i;
import dev.px.core.navigation.Goal;
import dev.px.core.util.Validate;
import dev.px.karatsuba.adapter.game.player.Players;
import dev.px.karatsuba.client.automation.navigation.Navigation;

import java.util.List;

/**
 * Walks to a place and stops there.
 *
 * <p>The goal is any of Core's: a block, a column, a height, near a point. The
 * task is done once the feet are in it, and fails when the planner can find no
 * way at all, with its reason.
 *
 * <pre>
 * .task goto 100 64 -20      the block
 * .task goto 100 -20         anywhere in that column
 * .task goto 70              any block at that height
 * .task goto ~ ~ ~30         thirty blocks south
 * </pre>
 */
public final class GotoTask implements Task {

    public static final TaskType TYPE = TaskType.of("goto", "<x y z | x z | y>",
            "Walks to a block, a column or a height", GotoTask::parse)
            .completes(index -> index < 3 ? List.of("~") : List.of());

    private final Goal goal;
    private final String where;

    private GotoTask(Goal goal, String where) {
        this.goal = Validate.notNull(goal, "goal");
        this.where = Validate.notNull(where, "where");
    }

    /**
     * @param goal  where to get to
     * @param where the goal as the player would say it, for the HUD and chat
     */
    public static GotoTask to(Goal goal, String where) {
        return new GotoTask(goal, where);
    }

    private static Task parse(TaskArgs args) {
        Vec3i at = Players.blockPosition();
        switch (args.size()) {
            case 3: {
                Vec3i block = args.block(0);
                return to(Goal.block(block), block.getX() + ", " + block.getY() + ", " + block.getZ());
            }
            case 2: {
                int x = args.coordinate(0, at.getX());
                int z = args.coordinate(1, at.getZ());
                return to(Goal.column(x, z), x + ", " + z);
            }
            case 1: {
                int y = args.coordinate(0, at.getY());
                return to(Goal.level(y), "height " + y);
            }
            default:
                args.fail("Give a block (x y z), a column (x z) or a height (y)");
                return null;
        }
    }

    @Override
    public String getName() {
        return "Goto";
    }

    @Override
    public String describe() {
        return "to " + where;
    }

    @Override
    public Step build(Navigation navigation) {
        return navigation.to(goal).named("walk " + describe());
    }

    public Goal getGoal() {
        return goal;
    }
}
