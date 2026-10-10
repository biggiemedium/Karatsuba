package dev.px.karatsuba.client.automation.task;

import java.util.List;
import java.util.Locale;

/**
 * The task catalogue: every kind of task the player can ask for by name.
 *
 * <p>Same shape as the other catalogues: write the task beside this, declare its
 * {@link TaskType}, add a line here. {@code .task} reads this list, and so will
 * a GUI section; nothing else needs to change.
 */
public final class Tasks {

    private static final List<TaskType> ALL = List.of(
            // Getting about.
            GotoTask.TYPE,
            FollowTask.TYPE,
            ClimbTask.TYPE,
            // The world.
            MineTask.TYPE,
            ContainerTasks.OPEN,
            ContainerTasks.CLOSE,
            // Yourself.
            EatTask.TYPE,
            ItemTasks.OFFHAND,
            // A fight.
            FightTask.TYPE,
            RetreatTask.TYPE,
            ItemTasks.PEARL);

    private Tasks() {
    }

    /** @return every kind, in the order a list of them reads best */
    public static List<TaskType> all() {
        return ALL;
    }

    /** @return the kind with this name, case aside, or null */
    public static TaskType find(String name) {
        String wanted = name.toLowerCase(Locale.ROOT);
        for (TaskType type : ALL) {
            if (type.getName().equals(wanted)) {
                return type;
            }
        }
        return null;
    }
}
