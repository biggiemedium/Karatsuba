package dev.px.karatsuba.client.automation.task;

import dev.px.karatsuba.client.automation.step.Eat;

import java.util.List;

/**
 * Eats: the best food for hunger, or a named item, hungry or not.
 *
 * <pre>
 * .task eat                       the best food in the hotbar, if hungry
 * .task eat golden_apple          this, now
 * </pre>
 *
 * <p>See {@link Eat} for how it stays within what Grim accepts.
 */
public final class EatTask {

    public static final TaskType TYPE = TaskType.of("eat", "[item]",
            "Eats the best food in the hotbar, or the item named",
            args -> args.has(0) ? item(args.get(0)) : food())
            .completes(index -> index == 0
                    ? List.of("golden_apple", "enchanted_golden_apple", "cooked_beef", "bread")
                    : List.of());

    private EatTask() {
    }

    /** @return a task that eats the best food in the hotbar, if hungry */
    public static Task food() {
        return Task.of("Eat", "the best food", navigation -> Eat.food());
    }

    /** @return a task that eats or drinks this item from the hotbar */
    public static Task item(String item) {
        return Task.of("Eat", item, navigation -> Eat.item(item));
    }
}
