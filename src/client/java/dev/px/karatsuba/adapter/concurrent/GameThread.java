package dev.px.karatsuba.adapter.concurrent;

import dev.px.core.Core;
import net.minecraft.client.MinecraftClient;

import java.util.function.Consumer;

/**
 * The game's own task queue, for the handoffs {@code ThreadService.sync} is the
 * wrong shape for.
 *
 * <h2>Which of the two to use</h2>
 * Core 1.5 grew a game-thread queue of its own. {@code Core.threads().sync(task)}
 * queues, {@code runPendingSync()} drains, and Core wires that drain to
 * {@code TickEvent} &mdash; which {@code GameEventBridge} posts, so it already
 * works here with nothing extra installed.
 *
 * <p><b>So {@code Core.threads().sync(...)} is the default, and a module should
 * reach for it first.</b> It is portable, it is what every Core doc means by
 * "marshal to the game thread", and it needs no adapter type at the call site.
 * This class is for the two things that queue cannot do:
 *
 * <ul>
 *   <li><b>Run now if already on the thread.</b> {@code sync} always queues, on
 *       purpose &mdash; running inline would let a task execute in the middle of
 *       another task's drain. {@link #run} runs inline when the caller is already
 *       on the game thread, which is what makes it usable from code that does not
 *       know where it is.</li>
 *   <li><b>Land every frame rather than every tick.</b> Core's queue drains from
 *       {@code TickEvent}, so it stops while the game is paused or between worlds.
 *       {@code MinecraftClient} is a {@code ThreadExecutor} whose queue the game
 *       loop drains every frame, before rendering, so {@link #later} still arrives
 *       when ticks have stopped.</li>
 * </ul>
 *
 * <p>Both queues are correct; they differ in when they run. Anything touching
 * render state wants the frame, anything touching world state wants the tick, and
 * anything that does not care wants {@code sync} because it is the portable one.
 *
 * <p><b>The one caveat</b>, for this queue, is shutdown: tasks queued after the
 * loop has stopped are never drained. Anything that must survive a shutdown
 * belongs in a {@code ConfigSection}, not in a queued task.
 */
public final class GameThread {

    private static final MinecraftClient CLIENT = MinecraftClient.getInstance();

    private GameThread() {
    }

    /** @return whether the caller is already on the game thread. */
    public static boolean isCurrent() {
        return CLIENT.isOnThread();
    }

    /**
     * Runs {@code task} on the game thread, immediately if already there.
     *
     * <p>Running inline when the caller is already on the thread is what keeps
     * this usable from code that does not know where it is: a HUD element and a
     * background poll can call the same method and neither pays for a queue hop
     * it does not need.
     */
    public static void run(Runnable task) {
        if (isCurrent()) {
            guard(task);
        } else {
            later(task);
        }
    }

    /**
     * Queues {@code task} for the next drain, even when already on the thread.
     *
     * <p>For the case {@link #run} is wrong for: deferring work out of the middle
     * of a frame, such as swapping the session or opening a screen from inside a
     * render or event handler that is still walking a list.
     */
    public static void later(Runnable task) {
        CLIENT.execute(() -> guard(task));
    }

    /**
     * Wraps a callback so it arrives on the game thread.
     *
     * <p>Written for the Core APIs that hand a result back on a worker thread and
     * document that the caller must marshal it &mdash; {@code AccountService.login}
     * above all:
     *
     * <pre>{@code
     * Core.accounts().login(account, GameThread.marshal(result -> {
     *     if (result.isSuccessful()) {
     *         Core.accounts().applySession(result.getSession());
     *     }
     * }));
     * }</pre>
     */
    public static <T> Consumer<T> marshal(Consumer<T> callback) {
        return value -> run(() -> callback.accept(value));
    }

    /**
     * Runs a task, reporting rather than losing what it throws.
     *
     * <p>{@code ThreadExecutor} logs an uncaught task to the game's log and keeps
     * going, which would leave a Core failure in a file Core never writes to and
     * with none of its context. Catching here puts it through Core's logger, for
     * the same reason {@code ThreadService} wraps its own tasks.
     */
    private static void guard(Runnable task) {
        try {
            task.run();
        } catch (Throwable thrown) {
            Core.log().error("Game-thread task failed", thrown);
        }
    }
}
