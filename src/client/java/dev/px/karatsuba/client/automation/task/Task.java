package dev.px.karatsuba.client.automation.task;

import dev.px.core.flow.Step;
import dev.px.core.registry.Named;
import dev.px.karatsuba.client.automation.navigation.Navigation;

import java.util.function.Function;

/**
 * Something automation can be asked to do: walk somewhere, follow someone.
 *
 * <p>A task is a request with its arguments already in it &mdash; "go to 100, 64,
 * -20", not "go somewhere" &mdash; and knows how to turn itself into a
 * {@link Step}, the unit Core's flow engine runs. It does not run itself:
 * {@code Automation.run(task)} builds the step and starts it as a flow, so every
 * task is started, stopped, paused, shown and reported the same way.
 *
 * <h2>Modules and tasks</h2>
 * A module is switched on and stays on, reacting to the game. A task is started,
 * works toward an end, and finishes, fails or is stopped. Neither touches the
 * other: a task never switches a module, and both are built on the same
 * library objects. Two modules contend for the keys and the head through Core's
 * claims, and a task's flow joins that same contest at its priority, so a combat
 * module can still take the head from a task walking past an enemy.
 *
 * <h2>Writing one</h2>
 * Build the step from the {@link Navigation} given and Core's {@code Flow}
 * factories. Reach the game through the context and the adapter's seams, as a
 * module does. A step reads everything through its {@code FlowContext}, so the
 * same step runs in the test kit's sandbox.
 *
 * <pre>{@code
 * public Step build(Navigation navigation) {
 *     return Flow.sequence(navigation.to(home), Flow.waitFor(Span.seconds(5)), navigation.to(mine));
 * }
 * }</pre>
 */
public interface Task extends Named {

    /** @return what kind of task this is, one or two words: "Goto", "Follow" */
    @Override
    String getName();

    /** @return what this one is doing, for the HUD and chat: "to 100, 64, -20" */
    String describe();

    /**
     * @return the step that does it, built afresh each time the task is run
     *
     * <p>Called on the game thread, in a world. Building may read the world
     * &mdash; who is in sight, where the player stands &mdash; but should not act
     * on it: acting is the step's, once it runs.
     */
    Step build(Navigation navigation);

    /**
     * @return a task from its three parts, for one whose whole job is a step or a
     *         short sequence: {@code Task.of("Eat", "golden_apple", nav -> Eat.item("golden_apple"))}
     */
    static Task of(String name, String description, Function<Navigation, Step> build) {
        return new Task() {
            @Override
            public String getName() {
                return name;
            }

            @Override
            public String describe() {
                return description;
            }

            @Override
            public Step build(Navigation navigation) {
                return build.apply(navigation);
            }
        };
    }
}
