package dev.px.karatsuba.client.automation.task;

import dev.px.core.flow.Flow;
import dev.px.core.flow.Span;
import dev.px.core.math.Vec3i;
import dev.px.karatsuba.adapter.game.player.Players;
import dev.px.karatsuba.client.automation.step.CloseContainer;
import dev.px.karatsuba.client.automation.step.UseBlock;

import java.util.List;

/**
 * Opening a container and closing it again.
 *
 * <pre>
 * .task open 100 64 -20      walk up to the chest there and open it
 * .task close                close whatever is open, standing still to do it
 * </pre>
 *
 * <p>Opening walks within reach, looks at the block and right-clicks it through
 * the action service (see {@link UseBlock}), then waits for the server to show
 * the window: a container that does not open &mdash; locked, blocked by a block
 * on top, already taken &mdash; fails the task after a few seconds rather than
 * clicking again. Closing is {@link CloseContainer}.
 */
public final class ContainerTasks {

    public static final TaskType OPEN = TaskType.of("open", "<x y z>",
            "Walks to a container and opens it", args -> open(args.block(0)))
            .completes(index -> index < 3 ? List.of("~") : List.of());

    public static final TaskType CLOSE = TaskType.of("close", "",
            "Closes the open container, standing still to do it", args -> close());

    /** How long the server has to show the window once the click is sent. */
    private static final Span OPENING = Span.seconds(3);

    private ContainerTasks() {
    }

    /** @return a task that walks up to {@code block} and opens it */
    public static Task open(Vec3i block) {
        return Task.of("Open", block.getX() + ", " + block.getY() + ", " + block.getZ(), navigation -> Flow.sequence(
                navigation.toReach(block).named("walk up to it"),
                new UseBlock(block),
                Flow.waitUntil(c -> Players.inOtherContainer()).named("wait for it to open").timeout(OPENING)));
    }

    /** @return a task that closes whatever container is open */
    public static Task close() {
        return Task.of("Close", "the container", navigation -> new CloseContainer());
    }
}
