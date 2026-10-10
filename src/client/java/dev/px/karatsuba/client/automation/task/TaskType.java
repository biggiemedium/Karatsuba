package dev.px.karatsuba.client.automation.task;

import dev.px.core.registry.Named;
import dev.px.core.util.Validate;

import java.util.List;
import java.util.function.Function;
import java.util.function.IntFunction;

/**
 * A kind of task the player can ask for by name: what it is called, what it
 * takes, and how to make one from what was typed.
 *
 * <p>Each task class declares its own, beside it, as {@code TYPE}, and
 * {@link Tasks} lists them. That is all a front end needs: {@code .task} parses
 * with it, and a GUI can list and describe every kind without knowing any.
 *
 * <pre>{@code
 * public static final TaskType TYPE = TaskType.of("eat", "[item]", "Eats the best food, or an item",
 *         args -> args.has(0) ? EatTask.item(args.get(0)) : EatTask.food());
 * }</pre>
 */
public final class TaskType implements Named {

    private final String name;
    private final String usage;
    private final String description;
    private final Function<TaskArgs, Task> parse;
    private IntFunction<List<String>> completions = index -> List.of();

    private TaskType(String name, String usage, String description, Function<TaskArgs, Task> parse) {
        this.name = Validate.notBlank(name, "name");
        this.usage = Validate.notNull(usage, "usage");
        this.description = Validate.notNull(description, "description");
        this.parse = Validate.notNull(parse, "parse");
    }

    /**
     * @param name        what is typed: {@code goto}
     * @param usage       its arguments, in usage notation: {@code <x y z>}; empty for none
     * @param description what it does, in one line
     * @param parse       makes the task from its arguments, failing through them when they are wrong
     */
    public static TaskType of(String name, String usage, String description, Function<TaskArgs, Task> parse) {
        return new TaskType(name, usage, description, parse);
    }

    /** Offers completions for the argument at each index. */
    public TaskType completes(IntFunction<List<String>> completions) {
        this.completions = Validate.notNull(completions, "completions");
        return this;
    }

    @Override
    public String getName() {
        return name;
    }

    /** @return its arguments, in usage notation; empty for none */
    public String getUsage() {
        return usage;
    }

    public String getDescription() {
        return description;
    }

    /** @return the task, made from these arguments */
    public Task create(TaskArgs args) {
        return parse.apply(args);
    }

    /** @return completions for the argument at {@code index} */
    public List<String> complete(int index) {
        return completions.apply(index);
    }
}
