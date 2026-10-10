package dev.px.karatsuba.client.automation.task;

import dev.px.core.flow.Flow;
import dev.px.core.flow.Span;
import dev.px.core.math.Vec3i;
import dev.px.karatsuba.adapter.world.Blocks;
import dev.px.karatsuba.client.automation.step.BreakBlock;

import java.util.List;

/**
 * Walks up to a block and breaks it.
 *
 * <pre>
 * .task mine 100 64 -20
 * .task mine ~ ~-1 ~2        the block two ahead, at the feet
 * </pre>
 *
 * <p>See {@link BreakBlock} for how the break stays a player's. A block not broken
 * within {@link #LIMIT} fails the task: bedrock, a block the server protects, or
 * obsidian without a pickaxe.
 */
public final class MineTask {

    public static final TaskType TYPE = TaskType.of("mine", "<x y z>",
            "Walks up to a block and breaks it", MineTask::parse)
            .completes(index -> index < 3 ? List.of("~") : List.of());

    /** Longer than any block takes with a fitting tool; past it, the break is not going to happen. */
    private static final Span LIMIT = Span.seconds(30);

    private MineTask() {
    }

    /** @return a task that walks up to {@code block} and breaks it */
    public static Task block(Vec3i block) {
        return Task.of("Mine", block.getX() + ", " + block.getY() + ", " + block.getZ(), navigation -> Flow.sequence(
                navigation.toReach(block).named("walk up to it"),
                new BreakBlock(block).timeout(LIMIT)));
    }

    private static Task parse(TaskArgs args) {
        Vec3i block = args.block(0);
        if (Blocks.isAir(block)) {
            args.fail("There is no block at " + block.getX() + ", " + block.getY() + ", " + block.getZ());
        }
        return block(block);
    }
}
